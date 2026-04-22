package com.actionow.agent.core.execution;

import com.actionow.agent.config.AgentRuntimeConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 执行注册表
 * 跟踪正在进行的 Agent 执行，并提供取消信号机制
 *
 * 高并发优化：
 * - 添加全局并发限制（DynamicSemaphore）防止系统过载
 * - 支持运行时动态调整最大并发数（通过 AgentRuntimeConfigService）
 *
 * @author Actionow
 */
@Slf4j
@Component
public class ExecutionRegistry {

    /**
     * 全局并发限制信号量（支持运行时 resize）
     */
    private final DynamicSemaphore globalConcurrencyLimiter;

    /**
     * 运行时配置服务
     */
    private final AgentRuntimeConfigService runtimeConfig;

    /**
     * 正在执行的会话及其取消信号
     * Key: sessionId
     * Value: 取消信号（true 表示已请求取消）
     */
    private final ConcurrentHashMap<String, AtomicBoolean> activeExecutions = new ConcurrentHashMap<>();

    /**
     * 执行上下文存储
     * Key: sessionId
     * Value: 执行上下文
     */
    private final ConcurrentHashMap<String, ExecutionContext> executionContexts = new ConcurrentHashMap<>();

    public ExecutionRegistry(AgentRuntimeConfigService runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
        int initialMax = runtimeConfig.getMaxConcurrentExecutions();
        this.globalConcurrencyLimiter = new DynamicSemaphore(initialMax);

        // 注册 Semaphore resize 回调
        runtimeConfig.registerSemaphoreResizeCallback((oldValue, newValue) -> {
            try {
                int newMax = Integer.parseInt(newValue);
                int oldMax = globalConcurrencyLimiter.getMaxPermits();
                globalConcurrencyLimiter.resize(newMax);
                log.info("DynamicSemaphore resized: {} → {}", oldMax, newMax);
            } catch (Exception e) {
                log.error("Failed to resize DynamicSemaphore: {}", e.getMessage());
            }
        });

        log.info("ExecutionRegistry initialized: maxConcurrentExecutions={}, acquireTimeoutMs={}, staleThresholdMs={}",
                initialMax, runtimeConfig.getAcquireTimeoutMs(), runtimeConfig.getExecutionStaleThresholdMs());
    }

    /**
     * 尝试获取执行许可
     * 用于全局并发控制，防止系统过载
     *
     * @return true 如果成功获取许可
     * @throws InterruptedException 如果等待时被中断
     */
    public boolean tryAcquirePermit() throws InterruptedException {
        long timeoutMs = runtimeConfig.getAcquireTimeoutMs();
        boolean acquired = globalConcurrencyLimiter.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
        if (!acquired) {
            log.warn("Failed to acquire execution permit within {}ms, current active: {}/{}",
                    timeoutMs, getActiveExecutionCount(), globalConcurrencyLimiter.getMaxPermits());
        }
        return acquired;
    }

    /**
     * 释放执行许可
     * 执行完成后调用，释放一个并发槽位
     */
    public void releasePermit() {
        globalConcurrencyLimiter.release();
        log.debug("Execution permit released, available: {}/{}",
                globalConcurrencyLimiter.availablePermits(), globalConcurrencyLimiter.getMaxPermits());
    }

    /**
     * 获取可用许可数
     */
    public int getAvailablePermits() {
        return globalConcurrencyLimiter.availablePermits();
    }

    /**
     * 注册新的执行
     *
     * @param sessionId 会话 ID
     * @return 取消信号引用，执行过程中应定期检查
     */
    public AtomicBoolean registerExecution(String sessionId) {
        AtomicBoolean cancelSignal = new AtomicBoolean(false);
        AtomicBoolean existing = activeExecutions.putIfAbsent(sessionId, cancelSignal);
        if (existing != null) {
            log.warn("Session {} already has an active execution, using existing signal", sessionId);
            return existing;
        }

        // 创建执行上下文
        ExecutionContext context = new ExecutionContext(sessionId, System.currentTimeMillis());
        executionContexts.put(sessionId, context);

        log.debug("Execution registered for session: {}, active: {}/{}",
                sessionId, getActiveExecutionCount(), globalConcurrencyLimiter.getMaxPermits());
        return cancelSignal;
    }

    /**
     * 检查执行是否应该被取消
     *
     * @param sessionId 会话 ID
     * @return true 如果执行应该被取消
     */
    public boolean isCancelled(String sessionId) {
        AtomicBoolean cancelSignal = activeExecutions.get(sessionId);
        return cancelSignal != null && cancelSignal.get();
    }

    /**
     * 请求取消执行
     *
     * @param sessionId 会话 ID
     * @return true 如果成功发送取消信号
     */
    public boolean requestCancellation(String sessionId) {
        AtomicBoolean cancelSignal = activeExecutions.get(sessionId);
        if (cancelSignal == null) {
            log.warn("No active execution found for session: {}", sessionId);
            return false;
        }

        boolean wasNotCancelled = cancelSignal.compareAndSet(false, true);
        if (wasNotCancelled) {
            log.info("Cancellation requested for session: {}", sessionId);

            // 更新执行上下文
            ExecutionContext context = executionContexts.get(sessionId);
            if (context != null) {
                context.setCancelledAt(System.currentTimeMillis());
                context.runCancellationHandler();
            }
        }
        return wasNotCancelled;
    }

    /**
     * 注册取消回调，用于在收到取消请求时主动中断底层执行。
     */
    public void setCancellationHandler(String sessionId, Runnable handler) {
        ExecutionContext context = executionContexts.get(sessionId);
        if (context != null) {
            context.setCancellationHandler(handler);
        }
    }

    /**
     * 注销执行（执行完成或取消后调用）
     *
     * @param sessionId 会话 ID
     */
    public void unregisterExecution(String sessionId) {
        activeExecutions.remove(sessionId);
        executionContexts.remove(sessionId);
        log.debug("Execution unregistered for session: {}", sessionId);
    }

    /**
     * 检查会话是否有正在进行的执行
     * 自动清理超过阈值的卡死执行
     *
     * @param sessionId 会话 ID
     * @return true 如果有活跃的（非卡死的）执行
     */
    public boolean hasActiveExecution(String sessionId) {
        if (!activeExecutions.containsKey(sessionId)) {
            return false;
        }
        // 检查是否为卡死执行
        long staleThresholdMs = runtimeConfig.getExecutionStaleThresholdMs();
        ExecutionContext context = executionContexts.get(sessionId);
        if (context != null && context.getElapsedMs() > staleThresholdMs) {
            log.warn("Stale execution detected for session {}, elapsed {}ms > threshold {}ms. Auto-cleaning.",
                    sessionId, context.getElapsedMs(), staleThresholdMs);
            activeExecutions.remove(sessionId);
            executionContexts.remove(sessionId);
            globalConcurrencyLimiter.release();
            return false;
        }
        return true;
    }

    /**
     * 获取执行上下文
     *
     * @param sessionId 会话 ID
     * @return 执行上下文，如果不存在则返回 null
     */
    public ExecutionContext getExecutionContext(String sessionId) {
        return executionContexts.get(sessionId);
    }

    /**
     * 获取当前活跃执行数量
     *
     * @return 活跃执行数
     */
    public int getActiveExecutionCount() {
        return activeExecutions.size();
    }

    /**
     * 执行上下文
     */
    public static class ExecutionContext {
        private final String sessionId;
        private final long startedAt;
        private volatile long cancelledAt;
        private volatile Runnable cancellationHandler;

        public ExecutionContext(String sessionId, long startedAt) {
            this.sessionId = sessionId;
            this.startedAt = startedAt;
        }

        public String getSessionId() {
            return sessionId;
        }

        public long getStartedAt() {
            return startedAt;
        }

        public long getCancelledAt() {
            return cancelledAt;
        }

        public void setCancelledAt(long cancelledAt) {
            this.cancelledAt = cancelledAt;
        }

        public void setCancellationHandler(Runnable cancellationHandler) {
            this.cancellationHandler = cancellationHandler;
        }

        public void runCancellationHandler() {
            Runnable handler = cancellationHandler;
            if (handler != null) {
                try {
                    handler.run();
                } catch (Exception e) {
                    log.warn("Cancellation handler failed for session {}: {}", sessionId, e.getMessage());
                }
            }
        }

        public long getElapsedMs() {
            return System.currentTimeMillis() - startedAt;
        }

        public boolean isCancelled() {
            return cancelledAt > 0;
        }
    }
}
