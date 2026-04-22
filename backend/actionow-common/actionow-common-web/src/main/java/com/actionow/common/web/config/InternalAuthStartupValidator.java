package com.actionow.common.web.config;

import com.actionow.common.core.security.InternalAuthProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 内部服务认证启动校验（高安全模式，严格失败）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InternalAuthStartupValidator implements ApplicationRunner {

    private static final String DEFAULT_INTERNAL_SECRET = "actionow-default-internal-secret-change-in-production";
    private static final int MIN_INTERNAL_SECRET_LENGTH = 32;

    private final InternalAuthProperties internalAuthProperties;

    @Override
    public void run(ApplicationArguments args) {
        if (!internalAuthProperties.isConfigured()) {
            throw new IllegalStateException("INTERNAL_AUTH_SECRET is required for service startup");
        }

        String secret = internalAuthProperties.getAuthSecret();
        if (DEFAULT_INTERNAL_SECRET.equals(secret)) {
            throw new IllegalStateException("Default INTERNAL_AUTH_SECRET is forbidden");
        }
        if (secret.length() < MIN_INTERNAL_SECRET_LENGTH) {
            throw new IllegalStateException("INTERNAL_AUTH_SECRET is too short, minimum length is "
                    + MIN_INTERNAL_SECRET_LENGTH);
        }

        log.info("Internal auth startup validation passed");
    }
}
