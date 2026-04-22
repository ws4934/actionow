package com.actionow.ai.plugin.polling;

import com.actionow.ai.config.AiRuntimeConfigService;
import com.actionow.ai.plugin.AiModelPlugin;
import com.actionow.ai.plugin.exception.PluginRateLimitException;
import com.actionow.ai.plugin.model.PluginConfig;
import com.actionow.ai.plugin.model.PluginExecutionResult;
import com.actionow.ai.plugin.model.PluginExecutionResult.ExecutionStatus;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 轮询管理器（统一版）
 * 管理所有异步任务的状态轮询，是轮询调度的唯一入口
 *
 * 核心改进：
 * 1. 统一 PollingState 替代分散的多个 Map，消除状态撕裂风险
 * 2. 容量上限（MAX_ACTIVE_POLLS），防止内存无限增长
 * 3. 内置 CompletableFuture 支持外部 await
 * 4. 自动清理已完成/过期的轮询任务
 *
 * @author Actionow
 */
@Slf4j
@Component
public class PollingManager {

    // ========== 配置常量 ==========

    private static final int DEFAULT_INTERVAL_MS = 2000;
    private static final int DEFAULT_MAX_ATTEMPTS = 60;
    private static final int CORE_POOL_SIZE = 5;

    /** 指数退避最大间隔（毫秒）：30 秒 */
    private static final int MAX_BACKOFF_INTERVAL_MS = 30_000;

    /** 指数退避因子：每次轮询后间隔乘以此系数 */
    private static final double BACKOFF_MULTIPLIER = 1.5;

    /** 编译时兜底最大并发轮询任务数 */
    private static final int DEFAULT_MAX_ACTIVE_POLLS = 100;

    /** 清理间隔（毫秒），每60秒扫描一次 */
    private static final long CLEANUP_INTERVAL_MS = 60_000;

    /** 已完成任务保留时长（毫秒），用于支持结果查询 */
    private static final long COMPLETED_RETENTION_MS = 60_000;

    /** 超时缓冲（毫秒），在任务预计最大轮询时间之上额外宽限 */
    private static final long STALE_BUFFER_MS = 120_000;

    // ========== 核心组件 ==========

    /** 轮询调度线程池 */
    private final ScheduledExecutorService scheduler;

    /** 轮询执行线程池（虚拟线程），解耦上传等重IO操作 */
    private final ExecutorService pollExecutor;

    /** 清理调度线程池 */
    private final ScheduledExecutorService cleanupScheduler;

    /** 统一状态 Map — 轮询状态的唯一数据源 */
    private final ConcurrentHashMap<String, PollingState> activePolls = new ConcurrentHashMap<>();

    /** 容量信号量 — 精确控制并发轮询数，替代 ConcurrentHashMap.size() 检查 */
    private final Semaphore pollSemaphore;

    /** 运行时配置服务 */
    private final AiRuntimeConfigService runtimeConfig;

    public PollingManager(AiRuntimeConfigService runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
        this.pollSemaphore = new Semaphore(runtimeConfig.getMaxActivePolls());
        AtomicInteger pollCounter = new AtomicInteger(0);
        this.scheduler = new ScheduledThreadPoolExecutor(
            CORE_POOL_SIZE,
            r -> {
                Thread thread = new Thread(r, "polling-worker-" + pollCounter.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        );

        this.pollExecutor = Executors.newVirtualThreadPerTaskExecutor();

        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "polling-cleanup");
            t.setDaemon(true);
            return t;
        });

        cleanupScheduler.scheduleWithFixedDelay(
            this::cleanupCompletedAndStaleTasks,
            CLEANUP_INTERVAL_MS,
            CLEANUP_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );

        log.info("PollingManager initialized: corePool={}, maxActivePolls={}", CORE_POOL_SIZE, runtimeConfig.getMaxActivePolls());
    }

    // ========== 公开 API ==========

    /**
     * 启动轮询任务
     *
     * @param executionId    执行ID
     * @param externalTaskId 外部任务ID
     * @param externalRunId  外部运行ID
     * @param plugin         插件实例
     * @param config         插件配置
     * @param callbackUrl    轮询完成后的回调URL（可选）
     * @param onComplete     完成回调（可选，用于发送HTTP回调等外部逻辑）
     * @return CompletableFuture 用于外部 await
     * @throws PluginRateLimitException 当并发轮询数超过上限时抛出
     */
    public CompletableFuture<PluginExecutionResult> startPolling(
            String executionId,
            String externalTaskId,
            String externalRunId,
            AiModelPlugin plugin,
            PluginConfig config,
            String callbackUrl,
            Consumer<PluginExecutionResult> onComplete) {

        // 容量检查（Semaphore 保证原子性）
        if (!pollSemaphore.tryAcquire()) {
            int maxActivePolls = runtimeConfig.getMaxActivePolls();
            log.warn("Polling capacity exhausted: active={}, max={}, rejected executionId={}",
                activePolls.size(), maxActivePolls, executionId);
            throw new PluginRateLimitException(
                "轮询任务已达上限（" + maxActivePolls + "），请稍后重试", null);
        }

        // 幂等检查
        PollingState existing = activePolls.get(executionId);
        if (existing != null && !existing.isCompleted()) {
            log.warn("Polling task already exists for execution: {}", executionId);
            return existing.getResultFuture();
        }

        // 获取轮询配置
        PluginConfig.PollingConfig pollingConfig = config.getPollingConfig();
        int intervalMs = pollingConfig != null ? pollingConfig.getIntervalMs() : DEFAULT_INTERVAL_MS;
        int maxAttempts = pollingConfig != null ? pollingConfig.getMaxAttempts() : DEFAULT_MAX_ATTEMPTS;

        // 创建统一状态
        PollingState state = new PollingState(
            executionId, externalTaskId, externalRunId,
            plugin, config, intervalMs, maxAttempts,
            callbackUrl, onComplete
        );

        activePolls.put(executionId, state);

        // 调度首次轮询（自调度模式，实际执行在虚拟线程上）
        scheduleNextPoll(state);

        log.info("Started polling: executionId={}, externalTaskId={}, intervalMs={}, maxAttempts={}, activeCount={}",
            executionId, externalTaskId, intervalMs, maxAttempts, activePolls.size());

        return state.getResultFuture();
    }

    /**
     * 停止轮询任务
     *
     * @param executionId 执行ID
     * @return true 如果找到并停止了任务
     */
    public boolean stopPolling(String executionId) {
        PollingState state = activePolls.get(executionId);
        if (state != null && !state.isCompleted()) {
            completePolling(state, PluginExecutionResult.builder()
                .executionId(executionId)
                .status(ExecutionStatus.CANCELLED)
                .errorCode("POLLING_CANCELLED")
                .errorMessage("轮询被手动取消")
                .completedAt(LocalDateTime.now())
                .build());
            log.info("Stopped polling for execution: {}", executionId);
            return true;
        }
        return false;
    }

    /**
     * 阻塞等待轮询结果
     *
     * @param executionId 执行ID
     * @param timeout     超时时间
     * @param unit        时间单位
     * @return 执行结果
     */
    public PluginExecutionResult awaitResult(String executionId, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        PollingState state = activePolls.get(executionId);
        if (state == null) {
            return PluginExecutionResult.failure(executionId, "NOT_FOUND", "未找到轮询任务");
        }
        return state.getResultFuture().get(timeout, unit);
    }

    /**
     * 非阻塞获取轮询结果（仅已完成的）
     *
     * @param executionId 执行ID
     * @return 执行结果，未完成返回 null
     */
    public PluginExecutionResult getResultIfReady(String executionId) {
        PollingState state = activePolls.get(executionId);
        if (state != null && state.getResultFuture().isDone()) {
            try {
                return state.getResultFuture().get();
            } catch (Exception e) {
                log.warn("获取轮询结果失败: executionId={}, error={}", executionId, e.getMessage());
                return null;
            }
        }
        return null;
    }

    /**
     * 检查轮询任务是否活跃（正在执行中）
     */
    public boolean isActive(String executionId) {
        PollingState state = activePolls.get(executionId);
        return state != null && !state.isCompleted();
    }

    /**
     * 获取当前活跃轮询任务数
     */
    public int getActiveCount() {
        return (int) activePolls.values().stream().filter(s -> !s.isCompleted()).count();
    }

    /**
     * 获取所有活跃任务ID
     */
    public Set<String> getActiveTaskIds() {
        return activePolls.keySet();
    }

    /**
     * 获取轮询状态（用于监控）
     */
    public PollingState getPollingState(String executionId) {
        return activePolls.get(executionId);
    }

    /**
     * 获取容量信息
     */
    public Map<String, Object> getCapacityInfo() {
        int active = getActiveCount();
        int maxActivePolls = runtimeConfig.getMaxActivePolls();
        return Map.of(
            "activeCount", active,
            "totalTracked", activePolls.size(),
            "maxCapacity", maxActivePolls,
            "available", maxActivePolls - active
        );
    }

    // ========== 内部逻辑 ==========

    /**
     * 自调度：在虚拟线程上执行轮询，完成后再安排下一次
     * 调度器线程仅负责定时触发（<1ms），重IO在虚拟线程上运行
     */
    private void scheduleNextPoll(PollingState state) {
        if (state.isCancelled() || state.isCompleted()) {
            return;
        }
        try {
            long currentInterval = state.getCurrentIntervalMs();
            ScheduledFuture<?> future = scheduler.schedule(
                () -> pollExecutor.submit(() -> {
                    try {
                        executePoll(state);
                    } finally {
                        if (!state.isCompleted() && !state.isCancelled()) {
                            // 指数退避：每次轮询后增大间隔，避免对外部 API 产生轮询风暴
                            state.applyBackoff(BACKOFF_MULTIPLIER, MAX_BACKOFF_INTERVAL_MS);
                            scheduleNextPoll(state);
                        }
                    }
                }),
                currentInterval,
                TimeUnit.MILLISECONDS
            );
            state.setScheduledFuture(future);
        } catch (RejectedExecutionException e) {
            // Scheduler already shut down — safe to ignore
            log.debug("Skipping poll reschedule, scheduler is shut down: executionId={}", state.getExecutionId());
        }
    }

    /**
     * 执行一次轮询
     */
    private void executePoll(PollingState state) {
        if (state.isCancelled() || state.isCompleted()) {
            return;
        }

        state.incrementAttempt();
        String executionId = state.getExecutionId();
        int attempt = state.getAttemptCount();

        log.debug("Polling attempt {}/{} for execution: {}", attempt, state.getMaxAttempts(), executionId);

        try {
            // 先检查是否超过最大尝试次数
            if (attempt > state.getMaxAttempts()) {
                log.warn("Polling timeout: executionId={}, attempts={}/{}", executionId, attempt, state.getMaxAttempts());
                completePolling(state, PluginExecutionResult.builder()
                    .executionId(executionId)
                    .status(ExecutionStatus.TIMEOUT)
                    .errorCode("POLLING_TIMEOUT")
                    .errorMessage("轮询超时，已达最大尝试次数: " + state.getMaxAttempts())
                    .completedAt(LocalDateTime.now())
                    .build());
                return;
            }

            // 调用插件的轮询方法
            PluginExecutionResult result = state.getPlugin().pollStatus(
                state.getConfig(), state.getExternalTaskId(), state.getExternalRunId());

            state.setLastResult(result);

            // 补充执行ID
            if (result.getExecutionId() == null) {
                result.setExecutionId(executionId);
            }

            // 检查是否到达终态
            if (result.getStatus().isTerminal()) {
                log.info("Polling reached terminal status: executionId={}, status={}, attempts={}",
                    executionId, result.getStatus(), attempt);
                completePolling(state, result);
            } else {
                // 非终态但成功响应：外部服务仍在处理中，重置退避间隔防止不必要的延迟
                state.resetInterval();
            }

        } catch (Exception e) {
            log.error("Polling error for execution {}, attempt {}/{}: {}",
                executionId, attempt, state.getMaxAttempts(), e.getMessage(), e);

            // 达到最大尝试次数后停止
            if (attempt >= state.getMaxAttempts()) {
                completePolling(state, PluginExecutionResult.builder()
                    .executionId(executionId)
                    .status(ExecutionStatus.FAILED)
                    .errorCode("POLLING_ERROR")
                    .errorMessage("轮询失败: " + e.getMessage())
                    .completedAt(LocalDateTime.now())
                    .build());
            }
        }
    }

    /**
     * 完成轮询（唯一的完成路径）
     * 负责：取消调度、设置结果、触发回调
     */
    private void completePolling(PollingState state, PluginExecutionResult result) {
        // 防重入：CAS 设置 completed 标志
        if (!state.markCompleted()) {
            return;
        }

        // 取消定时调度
        state.cancelSchedule();

        // 补充耗时
        long elapsedMs = System.currentTimeMillis() - state.getStartTimeMs();
        if (result.getElapsedTimeMs() == null) {
            result.setElapsedTimeMs(elapsedMs);
        }

        // 完成 CompletableFuture
        state.getResultFuture().complete(result);

        // 触发外部回调（如发送 HTTP callback）
        Consumer<PluginExecutionResult> onComplete = state.getOnComplete();
        if (onComplete != null) {
            try {
                onComplete.accept(result);
            } catch (Exception e) {
                log.error("Polling onComplete callback error: executionId={}, error={}",
                    state.getExecutionId(), e.getMessage(), e);
            }
        }

        log.info("Polling completed: executionId={}, status={}, elapsedMs={}, attempts={}",
            state.getExecutionId(), result.getStatus(), elapsedMs, state.getAttemptCount());
    }

    /**
     * 清理已完成和过期的任务
     */
    private void cleanupCompletedAndStaleTasks() {
        long now = System.currentTimeMillis();
        List<String> toRemove = new ArrayList<>();

        for (Map.Entry<String, PollingState> entry : activePolls.entrySet()) {
            String executionId = entry.getKey();
            PollingState state = entry.getValue();
            long elapsed = now - state.getStartTimeMs();

            if (state.isCompleted()) {
                // 已完成的任务：保留一段时间后清理
                if (elapsed > COMPLETED_RETENTION_MS + state.getMaxAttempts() * state.getIntervalMs()) {
                    toRemove.add(executionId);
                }
            } else if (elapsed > getStaleThresholdMs(state)) {
                // 未完成但超过预计最大轮询时间 + 缓冲：强制完成
                log.warn("Force-cleaning stale polling task: executionId={}, elapsedMs={}, thresholdMs={}",
                    executionId, elapsed, getStaleThresholdMs(state));
                completePolling(state, PluginExecutionResult.builder()
                    .executionId(executionId)
                    .status(ExecutionStatus.TIMEOUT)
                    .errorCode("CLEANUP_TIMEOUT")
                    .errorMessage("任务超时被清理")
                    .completedAt(LocalDateTime.now())
                    .build());
                toRemove.add(executionId);
            }
        }

        for (String id : toRemove) {
            activePolls.remove(id);
            pollSemaphore.release();
        }

        if (!toRemove.isEmpty()) {
            log.info("Cleaned up {} polling tasks, remaining: {}", toRemove.size(), activePolls.size());
        }
    }

    /**
     * 计算单个任务的 stale 超时阈值（毫秒）
     * 基于任务自身配置的 maxAttempts × intervalMs + 缓冲时间
     */
    private long getStaleThresholdMs(PollingState state) {
        long expectedMaxMs = (long) state.getMaxAttempts() * state.getIntervalMs();
        return expectedMaxMs + STALE_BUFFER_MS;
    }

    // ========== 生命周期 ==========

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down PollingManager with {} tracked tasks", activePolls.size());

        // 完成所有活跃任务
        activePolls.values().forEach(state -> {
            if (!state.isCompleted()) {
                completePolling(state, PluginExecutionResult.builder()
                    .executionId(state.getExecutionId())
                    .status(ExecutionStatus.CANCELLED)
                    .errorCode("SHUTDOWN")
                    .errorMessage("服务关闭，轮询终止")
                    .completedAt(LocalDateTime.now())
                    .build());
            }
        });
        activePolls.clear();

        // 关闭调度器
        cleanupScheduler.shutdown();
        scheduler.shutdown();
        pollExecutor.shutdown();
        try {
            if (!pollExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                pollExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            pollExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        try {
            if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("PollingManager shutdown completed");
    }

    // ========== 统一轮询状态 ==========

    /**
     * 轮询状态（单一数据源）
     * 替代原来分散在 4 个 Map 中的状态，消除状态撕裂风险
     */
    @Getter
    public static class PollingState {
        private final String executionId;
        private final String externalTaskId;
        private final String externalRunId;
        private final AiModelPlugin plugin;
        private final PluginConfig config;
        private final int intervalMs;
        private final int maxAttempts;
        private final String callbackUrl;
        private final Consumer<PluginExecutionResult> onComplete;
        private final CompletableFuture<PluginExecutionResult> resultFuture;
        private final long startTimeMs;
        private final LocalDateTime startedAt;
        private final int initialIntervalMs;

        private volatile ScheduledFuture<?> scheduledFuture;
        private volatile int attemptCount;
        private volatile long currentIntervalMs;
        private volatile boolean cancelled;
        private volatile boolean completed;
        private volatile PluginExecutionResult lastResult;

        public PollingState(String executionId, String externalTaskId, String externalRunId,
                           AiModelPlugin plugin, PluginConfig config,
                           int intervalMs, int maxAttempts,
                           String callbackUrl, Consumer<PluginExecutionResult> onComplete) {
            this.executionId = executionId;
            this.externalTaskId = externalTaskId;
            this.externalRunId = externalRunId;
            this.plugin = plugin;
            this.config = config;
            this.intervalMs = intervalMs;
            this.maxAttempts = maxAttempts;
            this.callbackUrl = callbackUrl;
            this.onComplete = onComplete;
            this.resultFuture = new CompletableFuture<>();
            this.startTimeMs = System.currentTimeMillis();
            this.startedAt = LocalDateTime.now();
            this.initialIntervalMs = intervalMs;
            this.currentIntervalMs = intervalMs;
        }

        void setScheduledFuture(ScheduledFuture<?> future) {
            this.scheduledFuture = future;
        }

        void setLastResult(PluginExecutionResult result) {
            this.lastResult = result;
        }

        void incrementAttempt() {
            this.attemptCount++;
        }

        /**
         * CAS 方式标记完成，防止并发完成
         *
         * @return true 如果成功标记（首次完成），false 如果已被其他线程完成
         */
        synchronized boolean markCompleted() {
            if (completed) {
                return false;
            }
            completed = true;
            cancelled = true;
            return true;
        }

        void cancelSchedule() {
            if (scheduledFuture != null) {
                scheduledFuture.cancel(false);
            }
        }

        /**
         * 应用指数退避：增大当前轮询间隔
         *
         * @param multiplier   退避因子
         * @param maxIntervalMs 最大间隔（毫秒）
         */
        void applyBackoff(double multiplier, long maxIntervalMs) {
            this.currentIntervalMs = Math.min((long) (this.currentIntervalMs * multiplier), maxIntervalMs);
        }

        /**
         * 重置间隔到初始值（收到有进展的响应时调用）
         */
        void resetInterval() {
            this.currentIntervalMs = this.initialIntervalMs;
        }

        /**
         * 获取已运行时长
         */
        public Duration getElapsedTime() {
            return Duration.between(startedAt, LocalDateTime.now());
        }
    }
}
