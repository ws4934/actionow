package com.actionow.ai.config;

import com.actionow.common.redis.config.RuntimeConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * AI 模块运行时配置服务
 * 管理 AI 轮询、限流、重试、Groovy 执行等核心运行参数
 *
 * @author Actionow
 */
@Slf4j
@Component
public class AiRuntimeConfigService extends RuntimeConfigService {

    // ==================== 配置键常量 ====================

    public static final String MAX_ACTIVE_POLLS             = "runtime.ai.max_active_polls";
    public static final String DEFAULT_RATE_LIMIT           = "runtime.ai.default_rate_limit";
    public static final String DEFAULT_MAX_RETRIES          = "runtime.ai.default_max_retries";
    public static final String GROOVY_MAX_EXECUTION_TIME_MS = "runtime.ai.groovy_max_execution_time_ms";
    public static final String FAILURE_RATE_THRESHOLD       = "runtime.ai.failure_rate_threshold";
    public static final String ALERT_ENABLED               = "runtime.ai.alert_enabled";
    public static final String ALERT_ERROR_RATE_THRESHOLD  = "runtime.ai.alert_error_rate_threshold";
    public static final String ALERT_RESPONSE_TIME_THRESHOLD_MS = "runtime.ai.alert_response_time_threshold_ms";
    public static final String ALERT_CONSECUTIVE_FAILURES  = "runtime.ai.alert_consecutive_failures_threshold";
    public static final String HTTP_CONNECT_TIMEOUT_MS     = "runtime.ai.http_connect_timeout_ms";
    public static final String HTTP_READ_TIMEOUT_SECONDS   = "runtime.ai.http_read_timeout_seconds";
    public static final String HTTP_MAX_CONNECTIONS         = "runtime.ai.http_max_connections";
    public static final String HTTP_MAX_CONNECTIONS_PER_ROUTE = "runtime.ai.http_max_connections_per_route";
    public static final String HTTP_MAX_IN_MEMORY_SIZE_BYTES = "runtime.ai.http_max_in_memory_size_bytes";

    public AiRuntimeConfigService(StringRedisTemplate redisTemplate,
                                   RedisMessageListenerContainer listenerContainer) {
        super(redisTemplate, listenerContainer);
    }

    @Override
    protected String getPrefix() {
        return "runtime.ai";
    }

    @Override
    protected void registerDefaults(Map<String, String> defaults) {
        defaults.put(MAX_ACTIVE_POLLS, "100");
        defaults.put(DEFAULT_RATE_LIMIT, "60");
        defaults.put(DEFAULT_MAX_RETRIES, "3");
        // 300s 默认值：满足大视频上传场景（OssBinding.uploadFromUrl 会从外部下载并上传到 OSS，
        // 视频资源可能需要数分钟）。请求/响应映射脚本实际执行时间很短，大值只是兜底。
        defaults.put(GROOVY_MAX_EXECUTION_TIME_MS, "300000");
        defaults.put(FAILURE_RATE_THRESHOLD, "50");
        defaults.put(ALERT_ENABLED, "true");
        defaults.put(ALERT_ERROR_RATE_THRESHOLD, "0.1");
        defaults.put(ALERT_RESPONSE_TIME_THRESHOLD_MS, "30000");
        defaults.put(ALERT_CONSECUTIVE_FAILURES, "5");
        defaults.put(HTTP_CONNECT_TIMEOUT_MS, "10000");
        defaults.put(HTTP_READ_TIMEOUT_SECONDS, "60");
        defaults.put(HTTP_MAX_CONNECTIONS, "500");
        defaults.put(HTTP_MAX_CONNECTIONS_PER_ROUTE, "50");
        // 32MB 响应缓冲：平衡 OOM 防护（10 并发 × 32MB = 320MB）与大图兼容性（支持 4K Base64）
        defaults.put(HTTP_MAX_IN_MEMORY_SIZE_BYTES, String.valueOf(32 * 1024 * 1024));
    }

    // ==================== Named Getters ====================

    public int getMaxActivePolls() {
        return getInt(MAX_ACTIVE_POLLS);
    }

    public int getDefaultRateLimit() {
        return getInt(DEFAULT_RATE_LIMIT);
    }

    public int getDefaultMaxRetries() {
        return getInt(DEFAULT_MAX_RETRIES);
    }

    public long getGroovyMaxExecutionTimeMs() {
        return getLong(GROOVY_MAX_EXECUTION_TIME_MS);
    }

    public float getFailureRateThreshold() {
        return getFloat(FAILURE_RATE_THRESHOLD);
    }

    public boolean isAlertEnabled() {
        return getBoolean(ALERT_ENABLED);
    }

    public float getAlertErrorRateThreshold() {
        return getFloat(ALERT_ERROR_RATE_THRESHOLD);
    }

    public long getAlertResponseTimeThresholdMs() {
        return getLong(ALERT_RESPONSE_TIME_THRESHOLD_MS);
    }

    public int getAlertConsecutiveFailuresThreshold() {
        return getInt(ALERT_CONSECUTIVE_FAILURES);
    }

    public int getHttpConnectTimeoutMs() {
        return getInt(HTTP_CONNECT_TIMEOUT_MS);
    }

    public int getHttpReadTimeoutSeconds() {
        return getInt(HTTP_READ_TIMEOUT_SECONDS);
    }

    public int getHttpMaxConnections() {
        return getInt(HTTP_MAX_CONNECTIONS);
    }

    public int getHttpMaxConnectionsPerRoute() {
        return getInt(HTTP_MAX_CONNECTIONS_PER_ROUTE);
    }

    public int getHttpMaxInMemorySizeBytes() {
        return getInt(HTTP_MAX_IN_MEMORY_SIZE_BYTES);
    }
}
