package com.actionow.gateway.config;

import com.actionow.common.redis.config.RuntimeConfigService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Gateway 模块运行时配置服务
 * 管理限流和 CORS 等核心网关运行参数
 *
 * @author Actionow
 */
@Slf4j
@Component
public class GatewayRuntimeConfigService extends RuntimeConfigService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Integer>> API_LIMITS_TYPE = new TypeReference<>() {};

    /**
     * API 限流默认值（JSON）
     */
    private static final String DEFAULT_API_LIMITS = """
            {"/api/ai/model-providers":10,"/api/ai/groovy-templates":10,"/api/tasks/ai/":10,"/api/files/upload":5,"/api/user/auth/login":10,"/api/user/auth/register":5}""";

    // ==================== 限流配置键 ====================

    public static final String RATE_LIMIT_ENABLED      = "runtime.gateway.rate_limit.enabled";
    public static final String RATE_LIMIT_GLOBAL       = "runtime.gateway.rate_limit.global_limit";
    public static final String RATE_LIMIT_IP_LIMIT     = "runtime.gateway.rate_limit.ip_limit";
    public static final String RATE_LIMIT_IP_WINDOW    = "runtime.gateway.rate_limit.ip_window";
    public static final String RATE_LIMIT_USER_LIMIT   = "runtime.gateway.rate_limit.user_limit";
    public static final String RATE_LIMIT_USER_WINDOW  = "runtime.gateway.rate_limit.user_window";
    public static final String RATE_LIMIT_API_LIMITS   = "runtime.gateway.rate_limit.api_limits";

    // ==================== 日志配置键 ====================

    public static final String LOG_ENABLED             = "runtime.gateway.log.enabled";
    public static final String LOG_HEADERS             = "runtime.gateway.log.log_headers";
    public static final String LOG_BODY                = "runtime.gateway.log.log_body";

    // ==================== CORS 配置键 ====================

    public static final String CORS_ENABLED            = "runtime.gateway.cors.enabled";
    public static final String CORS_ALLOWED_ORIGINS    = "runtime.gateway.cors.allowed_origins";
    public static final String CORS_ALLOW_CREDENTIALS  = "runtime.gateway.cors.allow_credentials";
    public static final String CORS_MAX_AGE            = "runtime.gateway.cors.max_age";

    public GatewayRuntimeConfigService(StringRedisTemplate redisTemplate,
                                        RedisMessageListenerContainer listenerContainer) {
        super(redisTemplate, listenerContainer);
    }

    @Override
    protected String getPrefix() {
        return "runtime.gateway";
    }

    @Override
    protected void registerDefaults(Map<String, String> defaults) {
        // 限流
        defaults.put(RATE_LIMIT_ENABLED, "true");
        defaults.put(RATE_LIMIT_GLOBAL, "1000");
        defaults.put(RATE_LIMIT_IP_LIMIT, "200");
        defaults.put(RATE_LIMIT_IP_WINDOW, "60");
        defaults.put(RATE_LIMIT_USER_LIMIT, "300");
        defaults.put(RATE_LIMIT_USER_WINDOW, "60");
        defaults.put(RATE_LIMIT_API_LIMITS, DEFAULT_API_LIMITS);

        // 日志
        defaults.put(LOG_ENABLED, "true");
        defaults.put(LOG_HEADERS, "true");
        defaults.put(LOG_BODY, "true");

        // CORS
        defaults.put(CORS_ENABLED, "true");
        defaults.put(CORS_ALLOWED_ORIGINS, "*");
        defaults.put(CORS_ALLOW_CREDENTIALS, "true");
        defaults.put(CORS_MAX_AGE, "3600");
    }

    // ==================== 限流 Getters ====================

    public boolean isRateLimitEnabled() {
        return getBoolean(RATE_LIMIT_ENABLED);
    }

    public int getRateLimitGlobal() {
        return getInt(RATE_LIMIT_GLOBAL);
    }

    public int getRateLimitIpLimit() {
        return getInt(RATE_LIMIT_IP_LIMIT);
    }

    public int getRateLimitIpWindow() {
        return getInt(RATE_LIMIT_IP_WINDOW);
    }

    public int getRateLimitUserLimit() {
        return getInt(RATE_LIMIT_USER_LIMIT);
    }

    public int getRateLimitUserWindow() {
        return getInt(RATE_LIMIT_USER_WINDOW);
    }

    // ==================== CORS Getters ====================

    public boolean isCorsEnabled() {
        return getBoolean(CORS_ENABLED);
    }

    public List<String> getCorsAllowedOrigins() {
        String value = getString(CORS_ALLOWED_ORIGINS);
        if (value == null || value.isBlank()) {
            return Collections.singletonList("*");
        }
        return Arrays.asList(value.split(","));
    }

    public boolean isCorsAllowCredentials() {
        return getBoolean(CORS_ALLOW_CREDENTIALS);
    }

    public long getCorsMaxAge() {
        return getLong(CORS_MAX_AGE);
    }

    // ==================== API 限流 Getters ====================

    /**
     * 获取 API 特定限流配置
     * JSON 格式: {"路径前缀": 每秒限制数}
     *
     * @return API 限流映射表，解析失败时返回空 Map
     */
    public Map<String, Integer> getApiLimits() {
        String json = getString(RATE_LIMIT_API_LIMITS);
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return OBJECT_MAPPER.readValue(json, API_LIMITS_TYPE);
        } catch (JsonProcessingException e) {
            log.error("API限流配置JSON解析失败，使用空配置: {}", json, e);
            return Collections.emptyMap();
        }
    }

    // ==================== 日志 Getters ====================

    public boolean isLogEnabled() {
        return getBoolean(LOG_ENABLED);
    }

    public boolean isLogHeaders() {
        return getBoolean(LOG_HEADERS);
    }

    public boolean isLogBody() {
        return getBoolean(LOG_BODY);
    }
}
