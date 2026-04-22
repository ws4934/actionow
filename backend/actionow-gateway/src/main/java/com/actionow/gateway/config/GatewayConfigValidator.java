package com.actionow.gateway.config;

import com.actionow.common.core.security.InternalAuthProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 网关配置校验器
 * 在应用启动时校验关键配置项
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GatewayConfigValidator implements ApplicationListener<ApplicationReadyEvent> {

    private static final String DEFAULT_JWT_SECRET = "actionow-default-secret-key-please-change-in-production";
    private static final String DEFAULT_INTERNAL_SECRET = "actionow-dev-internal-secret-key-32chars";
    private static final int MIN_JWT_SECRET_LENGTH = 32;
    private static final int MIN_INTERNAL_SECRET_LENGTH = 32;

    private final ActionowGatewayProperties gatewayProperties;
    private final InternalAuthProperties internalAuthProperties;
    private final Environment environment;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        validateJwtSecret();
        validateInternalAuthSecret();
        validateCorsConfig();
        logSecurityWarnings();
    }

    /**
     * 校验JWT密钥配置
     */
    private void validateJwtSecret() {
        String secret = gatewayProperties.getJwt().getSecret();
        String[] activeProfiles = environment.getActiveProfiles();
        boolean isProduction = Arrays.stream(activeProfiles)
                .anyMatch(profile -> "prod".equalsIgnoreCase(profile)
                        || "production".equalsIgnoreCase(profile));

        // 检查是否使用默认密钥
        if (DEFAULT_JWT_SECRET.equals(secret)) {
            String message = "JWT secret is using default value! This is a critical security risk.";
            if (isProduction) {
                log.error("===========================================");
                log.error("CRITICAL SECURITY ERROR: {}", message);
                log.error("Application startup blocked in production mode.");
                log.error("Please set JWT_SECRET environment variable or actionow.gateway.jwt.secret in configuration.");
                log.error("===========================================");
                throw new IllegalStateException("Cannot start in production with default JWT secret");
            } else {
                log.warn("===========================================");
                log.warn("SECURITY WARNING: {}", message);
                log.warn("This is acceptable for development but MUST be changed in production.");
                log.warn("===========================================");
            }
        }

        // 检查密钥长度
        if (secret != null && secret.length() < MIN_JWT_SECRET_LENGTH) {
            String message = String.format("JWT secret is too short (%d chars). Minimum recommended length is %d characters.",
                    secret.length(), MIN_JWT_SECRET_LENGTH);
            if (isProduction) {
                log.error("SECURITY ERROR: {}", message);
                throw new IllegalStateException("JWT secret too short for production use");
            } else {
                log.warn("SECURITY WARNING: {}", message);
            }
        }

        log.info("JWT configuration validated successfully");
    }

    /**
     * 校验内部服务认证密钥配置
     */
    private void validateInternalAuthSecret() {
        String secret = internalAuthProperties.getAuthSecret();
        String[] activeProfiles = environment.getActiveProfiles();
        boolean isProduction = Arrays.stream(activeProfiles)
                .anyMatch(profile -> "prod".equalsIgnoreCase(profile)
                        || "production".equalsIgnoreCase(profile));

        if (!internalAuthProperties.isConfigured()) {
            String message = "Internal auth secret is not configured! Service-to-service authentication is disabled.";
            if (isProduction) {
                log.error("===========================================");
                log.error("CRITICAL SECURITY ERROR: {}", message);
                log.error("Application startup blocked in production mode.");
                log.error("Please set INTERNAL_AUTH_SECRET environment variable.");
                log.error("===========================================");
                throw new IllegalStateException("Cannot start in production without internal auth secret");
            } else {
                log.warn("===========================================");
                log.warn("SECURITY WARNING: {}", message);
                log.warn("This is acceptable for development but MUST be configured in production.");
                log.warn("===========================================");
            }
            return;
        }

        // 检查是否使用默认密钥
        if (DEFAULT_INTERNAL_SECRET.equals(secret)) {
            String message = "Internal auth secret is using default value! This is a critical security risk.";
            if (isProduction) {
                log.error("===========================================");
                log.error("CRITICAL SECURITY ERROR: {}", message);
                log.error("Please set INTERNAL_AUTH_SECRET environment variable.");
                log.error("===========================================");
                throw new IllegalStateException("Cannot start in production with default internal auth secret");
            } else {
                log.warn("SECURITY WARNING: {}", message);
            }
        }

        // 检查密钥长度
        if (secret.length() < MIN_INTERNAL_SECRET_LENGTH) {
            String message = String.format("Internal auth secret is too short (%d chars). Minimum recommended length is %d characters.",
                    secret.length(), MIN_INTERNAL_SECRET_LENGTH);
            if (isProduction) {
                log.error("SECURITY ERROR: {}", message);
                throw new IllegalStateException("Internal auth secret too short for production use");
            } else {
                log.warn("SECURITY WARNING: {}", message);
            }
        }

        log.info("Internal auth configuration validated successfully");
    }

    /**
     * 校验CORS配置
     */
    private void validateCorsConfig() {
        ActionowGatewayProperties.CorsConfig corsConfig = gatewayProperties.getCors();
        String[] activeProfiles = environment.getActiveProfiles();
        boolean isProduction = Arrays.stream(activeProfiles)
                .anyMatch(profile -> "prod".equalsIgnoreCase(profile)
                        || "production".equalsIgnoreCase(profile));

        if (corsConfig.isEnabled() && corsConfig.getAllowedOrigins() != null) {
            boolean hasWildcard = corsConfig.getAllowedOrigins().contains("*");
            if (hasWildcard && isProduction) {
                log.warn("===========================================");
                log.warn("SECURITY WARNING: CORS is configured with wildcard (*) origin in production.");
                log.warn("This may expose your API to cross-origin attacks.");
                log.warn("Consider specifying explicit allowed origins for production.");
                log.warn("===========================================");
            }
        }
    }

    /**
     * 记录安全相关警告
     */
    private void logSecurityWarnings() {
        // 检查限流是否启用
        if (!gatewayProperties.getRateLimit().isEnabled()) {
            log.warn("SECURITY WARNING: Rate limiting is disabled. This may expose the system to abuse.");
        }

        // 检查Actuator暴露的端点
        log.info("Gateway security validation completed");
    }
}
