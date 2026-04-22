package com.actionow.task.service;

import com.actionow.task.config.TaskRuntimeConfigService;
import com.actionow.task.entity.BatchJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 批量作业并发控制服务 — 第 2、3 层并发控制
 * <p>
 * 本服务负责三层并发控制体系中的后两层（详见 TaskRuntimeConfigService.MQ_TASK_CONCURRENCY 注释）：
 * <pre>
 * 第 2 层（Workspace 限流）：WORKSPACE_BATCH_LIMIT
 *   - Key: actionow:throttle:workspace:{workspaceId}
 *   - 限制单个工作空间同时运行的 Batch 子项总数
 *   - 防止单 Workspace 独占全部 MQ Consumer 线程
 *
 * 第 3 层（BatchJob 限流）：BatchJob.maxConcurrency
 *   - Key: actionow:batch:running:{batchJobId}
 *   - 限制单个 BatchJob 内的并行子项数
 *   - 允许大 Job 不阻塞小 Job 的执行
 * </pre>
 * 两层检查通过原子 Lua 脚本完成，避免并发竞争下的超限问题。
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchConcurrencyService {

    private final StringRedisTemplate stringRedisTemplate;
    private final TaskRuntimeConfigService taskRuntimeConfig;

    private static final String BATCH_RUNNING_KEY = "actionow:batch:running:";
    private static final String WORKSPACE_THROTTLE_KEY = "actionow:throttle:workspace:";
    private static final long KEY_EXPIRE_HOURS = 24;

    /**
     * Lua 脚本：原子检查两级限制并自增
     * KEYS[1] = batch running key
     * KEYS[2] = workspace throttle key
     * ARGV[1] = batch max concurrency
     * ARGV[2] = workspace max concurrency
     * 返回 1 = 获取成功，0 = 失败
     */
    private static final String ACQUIRE_LUA =
            "local batchCurrent = tonumber(redis.call('GET', KEYS[1]) or '0') " +
            "local wsCurrent = tonumber(redis.call('GET', KEYS[2]) or '0') " +
            "local batchMax = tonumber(ARGV[1]) " +
            "local wsMax = tonumber(ARGV[2]) " +
            "if batchCurrent < batchMax and wsCurrent < wsMax then " +
            "  redis.call('INCR', KEYS[1]) " +
            "  redis.call('EXPIRE', KEYS[1], 86400) " +
            "  redis.call('INCR', KEYS[2]) " +
            "  redis.call('EXPIRE', KEYS[2], 86400) " +
            "  return 1 " +
            "end " +
            "return 0";

    /**
     * Lua 脚本：释放 permit（两级同时自减）
     */
    private static final String RELEASE_LUA =
            "local batchCurrent = tonumber(redis.call('GET', KEYS[1]) or '0') " +
            "local wsCurrent = tonumber(redis.call('GET', KEYS[2]) or '0') " +
            "if batchCurrent > 0 then redis.call('DECR', KEYS[1]) end " +
            "if wsCurrent > 0 then redis.call('DECR', KEYS[2]) end " +
            "return 1";

    /**
     * 尝试获取并发 permit
     *
     * @param batchJob 批量作业
     * @return true=获取成功，false=被限流
     */
    public boolean tryAcquire(BatchJob batchJob) {
        String batchKey = BATCH_RUNNING_KEY + batchJob.getId();
        String wsKey = WORKSPACE_THROTTLE_KEY + batchJob.getWorkspaceId();
        int batchMax = batchJob.getMaxConcurrency() != null ? batchJob.getMaxConcurrency() : 5;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(ACQUIRE_LUA, Long.class);
        Long result = stringRedisTemplate.execute(script,
                Arrays.asList(batchKey, wsKey),
                String.valueOf(batchMax),
                String.valueOf(taskRuntimeConfig.getWorkspaceBatchLimit()));

        boolean acquired = result != null && result == 1L;
        if (!acquired) {
            log.debug("并发限流: batchJobId={}, workspaceId={}", batchJob.getId(), batchJob.getWorkspaceId());
        }
        return acquired;
    }

    /**
     * 释放并发 permit
     *
     * @param batchJobId  批量作业ID
     * @param workspaceId 工作空间ID
     */
    public void release(String batchJobId, String workspaceId) {
        String batchKey = BATCH_RUNNING_KEY + batchJobId;
        String wsKey = WORKSPACE_THROTTLE_KEY + workspaceId;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(RELEASE_LUA, Long.class);
        stringRedisTemplate.execute(script, Arrays.asList(batchKey, wsKey));
    }

    /**
     * 获取当前 batch 运行中的任务数
     */
    public int getRunningCount(String batchJobId) {
        String key = BATCH_RUNNING_KEY + batchJobId;
        String val = stringRedisTemplate.opsForValue().get(key);
        return val != null ? Integer.parseInt(val) : 0;
    }

    /**
     * 清理 batch 的并发计数器（batch 结束时调用）
     */
    public void cleanup(String batchJobId) {
        stringRedisTemplate.delete(BATCH_RUNNING_KEY + batchJobId);
    }
}
