package com.actionow.ai.plugin.auth.impl;

import com.actionow.ai.plugin.auth.AuthConfig;
import com.actionow.ai.plugin.auth.AuthenticationStrategy;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

/**
 * API Key认证策略
 * 支持通过Header或Query参数传递API Key
 *
 * @author Actionow
 */
public class ApiKeyAuthStrategy implements AuthenticationStrategy {

    @Override
    public String getType() {
        return AuthConfig.AuthType.API_KEY;
    }

    @Override
    public String getDisplayName() {
        return "API Key认证";
    }

    @Override
    public void applyAuth(HttpHeaders headers, AuthConfig config) {
        if (!StringUtils.hasText(config.getApiKey())) {
            throw new IllegalArgumentException("API Key is required");
        }

        String headerName = StringUtils.hasText(config.getApiKeyHeader())
            ? config.getApiKeyHeader()
            : "Authorization";

        String prefix = config.getApiKeyPrefix() != null
            ? config.getApiKeyPrefix()
            : "Bearer ";

        // 确保前缀末尾有空格（如果不为空）
        if (StringUtils.hasText(prefix) && !prefix.endsWith(" ")) {
            prefix = prefix + " ";
        }

        String value = prefix + config.getApiKey();
        headers.set(headerName, value.trim());
    }

    @Override
    public ValidationResult validate(AuthConfig config) {
        if (!StringUtils.hasText(config.getApiKey())) {
            return ValidationResult.failure("API Key不能为空");
        }
        return ValidationResult.success();
    }

    @Override
    public String[] getSensitiveFields() {
        return new String[]{"apiKey"};
    }
}
