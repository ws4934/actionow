package com.actionow.task.service.impl;

import com.actionow.common.core.constant.RedisKeyConstants;
import com.actionow.task.entity.Task;
import com.actionow.task.mapper.TaskMapper;
import com.actionow.task.service.TaskPriorityQueueService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 任务优先级队列服务实现
 * 使用Redis Sorted Set实现分布式优先级队列
 * Score = priority * 1e12 + timestamp (确保相同优先级按时间排序)
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskPriorityQueueServiceImpl implements TaskPriorityQueueService {

    private final StringRedisTemplate redisTemplate;
    private final TaskMapper taskMapper;
    private final ObjectMapper objectMapper;

    private static final String QUEUE_KEY = RedisKeyConstants.PREFIX + "task:priority:queue";
    private static final String TASK_DATA_KEY = RedisKeyConstants.PREFIX + "task:data:";

    /**
     * 计算Score：priority * 1e12 + timestamp
     * 这样可以确保：
     * 1. 低priority值（高优先级）的任务排在前面
     * 2. 相同优先级的任务按时间先后排序
     */
    private double calculateScore(int priority, long timestamp) {
        return priority * 1_000_000_000_000L + timestamp;
    }

    @Override
    public void enqueue(Task task) {
        enqueue(task, task.getPriority() != null ? task.getPriority() : PRIORITY_NORMAL);
    }

    @Override
    public void enqueue(Task task, int priority) {
        try {
            long timestamp = System.currentTimeMillis();
            double score = calculateScore(priority, timestamp);

            // 存储任务ID到有序集合
            redisTemplate.opsForZSet().add(QUEUE_KEY, task.getId(), score);

            // 更新任务优先级
            if (task.getPriority() == null || task.getPriority() != priority) {
                task.setPriority(priority);
                taskMapper.updateById(task);
            }

            log.debug("Task enqueued: taskId={}, priority={}, score={}", task.getId(), priority, score);
        } catch (Exception e) {
            log.error("Failed to enqueue task: taskId={}", task.getId(), e);
            throw new RuntimeException("入队失败", e);
        }
    }

    @Override
    public Task dequeue() {
        try {
            // 获取并移除score最小的元素
            Set<ZSetOperations.TypedTuple<String>> result = redisTemplate.opsForZSet()
                    .popMin(QUEUE_KEY, 1);

            if (result == null || result.isEmpty()) {
                return null;
            }

            String taskId = result.iterator().next().getValue();
            if (taskId == null) {
                return null;
            }

            // 从数据库获取完整任务信息
            Task task = taskMapper.selectById(taskId);
            if (task == null) {
                log.warn("Task not found in database: taskId={}", taskId);
                return dequeue(); // 递归获取下一个
            }

            return task;
        } catch (Exception e) {
            log.error("Failed to dequeue task", e);
            return null;
        }
    }

    @Override
    public List<Task> dequeue(int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }

        try {
            // 批量获取并移除
            Set<ZSetOperations.TypedTuple<String>> results = redisTemplate.opsForZSet()
                    .popMin(QUEUE_KEY, limit);

            if (results == null || results.isEmpty()) {
                return Collections.emptyList();
            }

            List<String> taskIds = results.stream()
                    .map(ZSetOperations.TypedTuple::getValue)
                    .filter(id -> id != null)
                    .toList();

            if (taskIds.isEmpty()) {
                return Collections.emptyList();
            }

            // 批量查询任务
            return taskMapper.selectBatchIds(taskIds);
        } catch (Exception e) {
            log.error("Failed to dequeue tasks", e);
            return Collections.emptyList();
        }
    }

    @Override
    public Task peek() {
        try {
            Set<String> result = redisTemplate.opsForZSet().range(QUEUE_KEY, 0, 0);
            if (result == null || result.isEmpty()) {
                return null;
            }

            String taskId = result.iterator().next();
            return taskMapper.selectById(taskId);
        } catch (Exception e) {
            log.error("Failed to peek task", e);
            return null;
        }
    }

    @Override
    public int size() {
        try {
            Long size = redisTemplate.opsForZSet().size(QUEUE_KEY);
            return size != null ? size.intValue() : 0;
        } catch (Exception e) {
            log.error("Failed to get queue size", e);
            return 0;
        }
    }

    @Override
    public int size(int priority) {
        try {
            // 计算该优先级的score范围
            double minScore = calculateScore(priority, 0);
            double maxScore = calculateScore(priority, Long.MAX_VALUE);

            Long count = redisTemplate.opsForZSet().count(QUEUE_KEY, minScore, maxScore);
            return count != null ? count.intValue() : 0;
        } catch (Exception e) {
            log.error("Failed to get queue size for priority: {}", priority, e);
            return 0;
        }
    }

    @Override
    public boolean adjustPriority(String taskId, int newPriority) {
        try {
            // 获取当前score
            Double currentScore = redisTemplate.opsForZSet().score(QUEUE_KEY, taskId);
            if (currentScore == null) {
                return false;
            }

            // 提取原始时间戳
            long timestamp = (long) (currentScore % 1_000_000_000_000L);

            // 计算新score
            double newScore = calculateScore(newPriority, timestamp);

            // 更新Redis
            redisTemplate.opsForZSet().add(QUEUE_KEY, taskId, newScore);

            // 更新数据库
            Task task = new Task();
            task.setId(taskId);
            task.setPriority(newPriority);
            taskMapper.updateById(task);

            log.info("Task priority adjusted: taskId={}, newPriority={}", taskId, newPriority);
            return true;
        } catch (Exception e) {
            log.error("Failed to adjust task priority: taskId={}", taskId, e);
            return false;
        }
    }

    @Override
    public boolean remove(String taskId) {
        try {
            Long removed = redisTemplate.opsForZSet().remove(QUEUE_KEY, taskId);
            return removed != null && removed > 0;
        } catch (Exception e) {
            log.error("Failed to remove task: taskId={}", taskId, e);
            return false;
        }
    }

    @Override
    public boolean contains(String taskId) {
        try {
            Double score = redisTemplate.opsForZSet().score(QUEUE_KEY, taskId);
            return score != null;
        } catch (Exception e) {
            log.error("Failed to check task existence: taskId={}", taskId, e);
            return false;
        }
    }

    @Override
    public int getPosition(String taskId) {
        try {
            Long rank = redisTemplate.opsForZSet().rank(QUEUE_KEY, taskId);
            return rank != null ? rank.intValue() : -1;
        } catch (Exception e) {
            log.error("Failed to get task position: taskId={}", taskId, e);
            return -1;
        }
    }

    @Override
    public void clear() {
        try {
            redisTemplate.delete(QUEUE_KEY);
            log.info("Task priority queue cleared");
        } catch (Exception e) {
            log.error("Failed to clear queue", e);
        }
    }
}
