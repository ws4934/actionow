package com.actionow.user.config;

import com.actionow.common.security.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 用户服务配置校验器
 * 在应用启动时校验 JWT 密钥等关键配置
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserConfigValidator implements ApplicationListener<ApplicationReadyEvent> {

    private static final String DEFAULT_JWT_SECRET = "actionow-default-secret-key-please-change-in-production";
    private static final int MIN_JWT_SECRET_LENGTH = 32;

    private final JwtProperties jwtProperties;
    private final Environment environment;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        validateJwtSecret();
    }

    private void validateJwtSecret() {
        String secret = jwtProperties.getSecret();
        boolean isProduction = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "prod".equalsIgnoreCase(profile)
                        || "production".equalsIgnoreCase(profile));

        if (DEFAULT_JWT_SECRET.equals(secret)) {
            if (isProduction) {
                log.error("===========================================");
                log.error("CRITICAL: JWT secret is using default value!");
                log.error("User service signs tokens — this is a critical security risk.");
                log.error("Set JWT_SECRET environment variable or actionow.jwt.secret in configuration.");
                log.error("===========================================");
                throw new IllegalStateException("Cannot start in production with default JWT secret");
            } else {
                log.warn("SECURITY WARNING: JWT secret is using default value. Change in production.");
            }
        }

        if (secret != null && secret.length() < MIN_JWT_SECRET_LENGTH) {
            if (isProduction) {
                log.error("JWT secret too short ({} chars, minimum {})", secret.length(), MIN_JWT_SECRET_LENGTH);
                throw new IllegalStateException("JWT secret too short for production use");
            } else {
                log.warn("JWT secret is short ({} chars). Recommended minimum: {}", secret.length(), MIN_JWT_SECRET_LENGTH);
            }
        }

        log.info("User service JWT configuration validated");
    }
}
