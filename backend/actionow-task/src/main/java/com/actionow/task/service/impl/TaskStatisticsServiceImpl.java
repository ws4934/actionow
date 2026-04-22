package com.actionow.task.service.impl;

import com.actionow.task.constant.TaskConstants;
import com.actionow.task.mapper.TaskStatisticsMapper;
import com.actionow.task.service.TaskPriorityQueueService;
import com.actionow.task.service.TaskStatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 任务统计服务实现
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskStatisticsServiceImpl implements TaskStatisticsService {

    private final TaskStatisticsMapper taskStatisticsMapper;
    private final TaskPriorityQueueService priorityQueueService;
    private final StringRedisTemplate redisTemplate;

    @Value("${actionow.task.max-concurrent-tasks:10}")
    private int maxConcurrent;

    private static final String STATS_PREFIX = "actionow:task:stats:";

    @Override
    public TaskStatsSummary getWorkspaceSummary(String workspaceId) {
        Map<String, Object> stats = taskStatisticsMapper.selectStatsByWorkspace(workspaceId);
        return buildSummary(stats);
    }

    @Override
    public TaskStatsSummary getUserSummary(String userId) {
        Map<String, Object> stats = taskStatisticsMapper.selectStatsByCreator(userId);
        return buildSummary(stats);
    }

    @Override
    public DailyStats getDailyStats(String workspaceId, LocalDate date) {
        Map<String, Object> stats = taskStatisticsMapper.selectDailyStats(workspaceId, date);
        return buildDailyStats(date, stats);
    }

    @Override
    public List<DailyStats> getStatsTrend(String workspaceId, LocalDate startDate, LocalDate endDate) {
        List<Map<String, Object>> statsList = taskStatisticsMapper.selectStatsTrend(workspaceId, startDate, endDate);
        List<DailyStats> result = new ArrayList<>();

        for (Map<String, Object> stats : statsList) {
            Object dateObj = stats.get("date");
            LocalDate date = dateObj instanceof LocalDate ? (LocalDate) dateObj
                    : LocalDate.parse(dateObj.toString());
            result.add(buildDailyStats(date, stats));
        }

        return result;
    }

    @Override
    public List<ProviderUsageStats> getProviderUsageStats(String workspaceId) {
        List<Map<String, Object>> statsList = taskStatisticsMapper.selectProviderUsageStats(workspaceId);
        List<ProviderUsageStats> result = new ArrayList<>();

        for (Map<String, Object> stats : statsList) {
            long total = getLong(stats, "total");
            long success = getLong(stats, "success");
            long failed = getLong(stats, "failed");
            double successRate = total > 0 ? (double) success / total : 0;

            result.add(new ProviderUsageStats(
                    getString(stats, "providerId"),
                    getString(stats, "providerName"),
                    getString(stats, "providerType"),
                    total,
                    success,
                    failed,
                    successRate,
                    getLong(stats, "avgDurationMs"),
                    getLong(stats, "totalCredits")
            ));
        }

        return result;
    }

    @Override
    public Map<String, Long> getTaskTypeDistribution(String workspaceId) {
        List<Map<String, Object>> distribution = taskStatisticsMapper.selectTaskTypeDistribution(workspaceId);
        Map<String, Long> result = new LinkedHashMap<>();

        for (Map<String, Object> item : distribution) {
            String type = getString(item, "type");
            long count = getLong(item, "count");
            result.put(type, count);
        }

        return result;
    }

    @Override
    public QueueStatus getQueueStatus() {
        int totalQueued = priorityQueueService.size();
        int highPriority = priorityQueueService.size(TaskPriorityQueueService.PRIORITY_HIGHEST)
                + priorityQueueService.size(TaskPriorityQueueService.PRIORITY_HIGH);
        int normalPriority = priorityQueueService.size(TaskPriorityQueueService.PRIORITY_NORMAL);
        int lowPriority = priorityQueueService.size(TaskPriorityQueueService.PRIORITY_LOW)
                + priorityQueueService.size(TaskPriorityQueueService.PRIORITY_LOWEST);

        // 从Redis获取当前运行中的任务数
        String runningKey = STATS_PREFIX + "running:count";
        String runningCountStr = redisTemplate.opsForValue().get(runningKey);
        int currentlyRunning = runningCountStr != null ? Integer.parseInt(runningCountStr) : 0;

        double utilizationRate = maxConcurrent > 0 ? (double) currentlyRunning / maxConcurrent : 0;

        return new QueueStatus(
                totalQueued,
                highPriority,
                normalPriority,
                lowPriority,
                currentlyRunning,
                maxConcurrent,
                utilizationRate
        );
    }

    /**
     * 增加运行中任务计数
     */
    public void incrementRunningCount() {
        String key = STATS_PREFIX + "running:count";
        redisTemplate.opsForValue().increment(key);
    }

    /**
     * 减少运行中任务计数
     */
    public void decrementRunningCount() {
        String key = STATS_PREFIX + "running:count";
        redisTemplate.opsForValue().decrement(key);
    }

    // ==================== 私有方法 ====================

    private TaskStatsSummary buildSummary(Map<String, Object> stats) {
        if (stats == null || stats.isEmpty()) {
            return new TaskStatsSummary(0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        long total = getLong(stats, "total");
        long pending = getLong(stats, "pending");
        long running = getLong(stats, "running");
        long completed = getLong(stats, "completed");
        long failed = getLong(stats, "failed");
        long cancelled = getLong(stats, "cancelled");
        long avgDuration = getLong(stats, "avgDurationMs");
        long totalCredits = getLong(stats, "totalCredits");

        double successRate = (completed + failed) > 0
                ? (double) completed / (completed + failed)
                : 0;

        return new TaskStatsSummary(
                total, pending, running, completed, failed, cancelled,
                successRate, avgDuration, totalCredits
        );
    }

    private DailyStats buildDailyStats(LocalDate date, Map<String, Object> stats) {
        if (stats == null || stats.isEmpty()) {
            return new DailyStats(date, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        long total = getLong(stats, "total");
        long completed = getLong(stats, "completed");
        long failed = getLong(stats, "failed");
        long avgDuration = getLong(stats, "avgDurationMs");
        long credits = getLong(stats, "credits");
        long peakHour = getLong(stats, "peakHour");
        long peakHourCount = getLong(stats, "peakHourCount");

        double successRate = (completed + failed) > 0
                ? (double) completed / (completed + failed)
                : 0;

        return new DailyStats(
                date, total, completed, failed,
                successRate, avgDuration, credits,
                peakHour, peakHourCount
        );
    }

    private long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : "";
    }
}
