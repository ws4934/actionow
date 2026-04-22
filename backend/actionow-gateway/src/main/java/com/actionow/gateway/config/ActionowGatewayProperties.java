package com.actionow.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 网关配置属性
 *
 * @author Actionow
 */
@Data
@Component
@ConfigurationProperties(prefix = "actionow.gateway")
public class ActionowGatewayProperties {

    /**
     * 白名单路径
     */
    private List<String> whitelist;

    /**
     * JWT 配置
     */
    private JwtConfig jwt = new JwtConfig();

    /**
     * 限流配置
     */
    private RateLimitConfig rateLimit = new RateLimitConfig();

    /**
     * CORS 配置
     */
    private CorsConfig cors = new CorsConfig();

    /**
     * 日志配置
     */
    private LogConfig log = new LogConfig();

    @Data
    public static class JwtConfig {
        private String secret;
        private String header = "Authorization";
        private String prefix = "Bearer ";
    }

    @Data
    public static class RateLimitConfig {
        /**
         * 是否启用限流
         */
        private boolean enabled = true;

        /**
         * 全局限流（每秒请求数）
         */
        private int globalLimit = 10000;

        /**
         * IP限流（每分钟请求数）
         */
        private int ipLimit = 100;

        /**
         * IP限流时间窗口（秒）
         */
        private int ipWindow = 60;

        /**
         * 用户限流（每分钟请求数）
         */
        private int userLimit = 1000;

        /**
         * 用户限流时间窗口（秒）
         */
        private int userWindow = 60;

        /**
         * API特定限流配置
         * key: 路径前缀, value: 每秒请求数
         */
        private Map<String, Integer> apiLimits = new HashMap<>();
    }

    @Data
    public static class CorsConfig {
        /**
         * 是否启用CORS
         */
        private boolean enabled = true;

        /**
         * 允许的源
         */
        private List<String> allowedOrigins = List.of("*");

        /**
         * 允许的方法
         */
        private List<String> allowedMethods = List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH");

        /**
         * 允许的请求头
         */
        private List<String> allowedHeaders = List.of("*");

        /**
         * 是否允许携带凭证
         */
        private boolean allowCredentials = true;

        /**
         * 预检请求缓存时间（秒）
         */
        private long maxAge = 3600;
    }

    @Data
    public static class LogConfig {
        /**
         * 是否启用请求日志
         */
        private boolean enabled = true;

        /**
         * 是否记录请求头
         */
        private boolean logHeaders = true;

        /**
         * 是否记录请求体
         */
        private boolean logBody = false;

        /**
         * 需要记录请求体的路径
         */
        private List<String> logBodyPaths = List.of("/api/user/auth/login", "/api/user/auth/register");

        /**
         * 敏感header列表（需脱敏）
         */
        private List<String> sensitiveHeaders = List.of("Authorization", "X-Api-Key");

        /**
         * 敏感字段列表（需脱敏）
         */
        private List<String> sensitiveFields = List.of("password", "token", "secret");
    }
}
