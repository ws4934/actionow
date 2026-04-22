package com.actionow.agent.config;

import com.actionow.common.redis.config.RuntimeConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Agent 模块运行时配置服务
 * 管理 Agent 并发、会话、Mission 等核心运行参数
 *
 * @author Actionow
 */
@Slf4j
@Component
public class AgentRuntimeConfigService extends RuntimeConfigService {

    // ==================== 配置键常量 ====================

    public static final String MAX_CONCURRENT_EXECUTIONS    = "runtime.agent.max_concurrent_executions";
    public static final String ACQUIRE_TIMEOUT_MS           = "runtime.agent.acquire_timeout_ms";
    public static final String EXECUTION_STALE_THRESHOLD_MS = "runtime.agent.execution_stale_threshold_ms";
    public static final String MAX_ITERATIONS               = "runtime.agent.max_iterations";
    public static final String DEFAULT_MODEL                = "runtime.agent.default_model";
    public static final String SESSION_MAX_ACTIVE_PER_SCOPE = "runtime.agent.session.max_active_per_scope";
    public static final String SESSION_MAX_ACTIVE_GLOBAL    = "runtime.agent.session.max_active_global";
    public static final String MISSION_MAX_STEPS            = "runtime.agent.mission.max_steps";
    public static final String MISSION_LOOP_FAIL_THRESHOLD  = "runtime.agent.mission.loop_fail_threshold";
    public static final String MISSION_MAX_RETRIES          = "runtime.agent.mission.max_retries";
    public static final String MISSION_MAX_CONCURRENT_PER_WS = "runtime.agent.mission.max_concurrent_per_workspace";
    public static final String RAG_ENABLED                   = "runtime.agent.rag_enabled";
    public static final String BILLING_IDLE_TIMEOUT_MINUTES  = "runtime.agent.billing.idle_timeout_minutes";
    public static final String BILLING_BATCH_SIZE            = "runtime.agent.billing.batch_size";
    public static final String BILLING_MAX_RETRY_COUNT       = "runtime.agent.billing.max_retry_count";
    public static final String BILLING_DEFAULT_FREEZE_AMOUNT = "runtime.agent.billing.default_freeze_amount";
    public static final String BILLING_FREEZE_THRESHOLD_RATIO = "runtime.agent.billing.freeze_threshold_ratio";
    public static final String BILLING_DEFAULT_INPUT_PRICE   = "runtime.agent.billing.default_input_price";
    public static final String BILLING_DEFAULT_OUTPUT_PRICE  = "runtime.agent.billing.default_output_price";
    public static final String BILLING_COMPENSATION_TIMEOUT_HOURS = "runtime.agent.billing.compensation_timeout_hours";
    public static final String SESSION_MAX_TOTAL_PER_USER    = "runtime.agent.session.max_total_per_user";
    public static final String MISSION_MAX_CONTEXT_STEPS     = "runtime.agent.mission.max_context_steps";
    public static final String MISSION_MAX_STEP_SUMMARY_CHARS = "runtime.agent.mission.max_step_summary_chars";
    public static final String MISSION_LOOP_WARN_THRESHOLD   = "runtime.agent.mission.loop_warn_threshold";
    public static final String SESSION_CACHE_MAX_SIZE        = "runtime.agent.session_cache.max_size";
    public static final String SESSION_CACHE_EXPIRE_MINUTES  = "runtime.agent.session_cache.expire_minutes";
    public static final String MISSION_SSE_TIMEOUT_MS        = "runtime.agent.mission.sse_timeout_ms";

    /**
     * Semaphore resize 回调（由 ExecutionRegistry 注册）
     */
    private volatile BiConsumer<String, String> semaphoreResizeCallback;

    public AgentRuntimeConfigService(StringRedisTemplate redisTemplate,
                                      RedisMessageListenerContainer listenerContainer) {
        super(redisTemplate, listenerContainer);
    }

    @Override
    protected String getPrefix() {
        return "runtime.agent";
    }

    @Override
    protected void registerDefaults(Map<String, String> defaults) {
        defaults.put(MAX_CONCURRENT_EXECUTIONS, "100");
        defaults.put(ACQUIRE_TIMEOUT_MS, "30000");
        defaults.put(EXECUTION_STALE_THRESHOLD_MS, "600000");
        defaults.put(MAX_ITERATIONS, "20");
        defaults.put(DEFAULT_MODEL, "qwen-max");
        defaults.put(SESSION_MAX_ACTIVE_PER_SCOPE, "5");
        defaults.put(SESSION_MAX_ACTIVE_GLOBAL, "3");
        defaults.put(MISSION_MAX_STEPS, "50");
        defaults.put(MISSION_LOOP_FAIL_THRESHOLD, "10");
        defaults.put(MISSION_MAX_RETRIES, "3");
        defaults.put(MISSION_MAX_CONCURRENT_PER_WS, "3");
        defaults.put(RAG_ENABLED, "false");
        defaults.put(BILLING_IDLE_TIMEOUT_MINUTES, "30");
        defaults.put(BILLING_BATCH_SIZE, "50");
        defaults.put(BILLING_MAX_RETRY_COUNT, "3");
        defaults.put(BILLING_DEFAULT_FREEZE_AMOUNT, "100");
        defaults.put(BILLING_FREEZE_THRESHOLD_RATIO, "0.8");
        defaults.put(BILLING_DEFAULT_INPUT_PRICE, "0.5");
        defaults.put(BILLING_DEFAULT_OUTPUT_PRICE, "1.5");
        defaults.put(BILLING_COMPENSATION_TIMEOUT_HOURS, "24");
        defaults.put(SESSION_MAX_TOTAL_PER_USER, "200");
        defaults.put(MISSION_MAX_CONTEXT_STEPS, "10");
        defaults.put(MISSION_MAX_STEP_SUMMARY_CHARS, "500");
        defaults.put(MISSION_LOOP_WARN_THRESHOLD, "5");
        defaults.put(SESSION_CACHE_MAX_SIZE, "10000");
        defaults.put(SESSION_CACHE_EXPIRE_MINUTES, "5");
        defaults.put(MISSION_SSE_TIMEOUT_MS, "1800000");
    }

    @Override
    protected void onConfigChanged(String key, String oldValue, String newValue) {
        if (MAX_CONCURRENT_EXECUTIONS.equals(key) && semaphoreResizeCallback != null) {
            semaphoreResizeCallback.accept(oldValue, newValue);
        }
    }

    /**
     * 注册 Semaphore resize 回调（由 ExecutionRegistry 调用）
     */
    public void registerSemaphoreResizeCallback(BiConsumer<String, String> callback) {
        this.semaphoreResizeCallback = callback;
    }

    // ==================== Named Getters ====================

    public int getMaxConcurrentExecutions() {
        return getInt(MAX_CONCURRENT_EXECUTIONS);
    }

    public long getAcquireTimeoutMs() {
        return getLong(ACQUIRE_TIMEOUT_MS);
    }

    public long getExecutionStaleThresholdMs() {
        return getLong(EXECUTION_STALE_THRESHOLD_MS);
    }

    public int getMaxIterations() {
        return getInt(MAX_ITERATIONS);
    }

    public String getDefaultModel() {
        return getString(DEFAULT_MODEL);
    }

    public int getSessionMaxActivePerScope() {
        return getInt(SESSION_MAX_ACTIVE_PER_SCOPE);
    }

    public int getSessionMaxActiveGlobal() {
        return getInt(SESSION_MAX_ACTIVE_GLOBAL);
    }

    public int getMissionMaxSteps() {
        return getInt(MISSION_MAX_STEPS);
    }

    public int getMissionLoopFailThreshold() {
        return getInt(MISSION_LOOP_FAIL_THRESHOLD);
    }

    public int getMissionMaxRetries() {
        return getInt(MISSION_MAX_RETRIES);
    }

    public int getMissionMaxConcurrentPerWorkspace() {
        return getInt(MISSION_MAX_CONCURRENT_PER_WS);
    }

    public boolean isRagEnabled() {
        return getBoolean(RAG_ENABLED);
    }

    public int getBillingIdleTimeoutMinutes() {
        return getInt(BILLING_IDLE_TIMEOUT_MINUTES);
    }

    public int getBillingBatchSize() {
        return getInt(BILLING_BATCH_SIZE);
    }

    public int getBillingMaxRetryCount() {
        return getInt(BILLING_MAX_RETRY_COUNT);
    }

    public long getBillingDefaultFreezeAmount() {
        return getLong(BILLING_DEFAULT_FREEZE_AMOUNT);
    }

    public float getBillingFreezeThresholdRatio() {
        return getFloat(BILLING_FREEZE_THRESHOLD_RATIO);
    }

    public String getBillingDefaultInputPrice() {
        return getString(BILLING_DEFAULT_INPUT_PRICE);
    }

    public String getBillingDefaultOutputPrice() {
        return getString(BILLING_DEFAULT_OUTPUT_PRICE);
    }

    public int getBillingCompensationTimeoutHours() {
        return getInt(BILLING_COMPENSATION_TIMEOUT_HOURS);
    }

    public int getSessionMaxTotalPerUser() {
        return getInt(SESSION_MAX_TOTAL_PER_USER);
    }

    public int getMissionMaxContextSteps() {
        return getInt(MISSION_MAX_CONTEXT_STEPS);
    }

    public int getMissionMaxStepSummaryChars() {
        return getInt(MISSION_MAX_STEP_SUMMARY_CHARS);
    }

    public int getMissionLoopWarnThreshold() {
        return getInt(MISSION_LOOP_WARN_THRESHOLD);
    }

    public int getSessionCacheMaxSize() {
        return getInt(SESSION_CACHE_MAX_SIZE);
    }

    public int getSessionCacheExpireMinutes() {
        return getInt(SESSION_CACHE_EXPIRE_MINUTES);
    }

    public long getMissionSseTimeoutMs() {
        return getLong(MISSION_SSE_TIMEOUT_MS);
    }
}
