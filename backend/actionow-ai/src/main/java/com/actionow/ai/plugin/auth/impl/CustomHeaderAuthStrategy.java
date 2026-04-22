package com.actionow.ai.plugin.auth.impl;

import com.actionow.ai.plugin.auth.AuthConfig;
import com.actionow.ai.plugin.auth.AuthenticationStrategy;
import org.springframework.http.HttpHeaders;

import java.util.Map;

/**
 * 自定义Header认证策略
 * 允许用户配置任意的请求头
 *
 * @author Actionow
 */
public class CustomHeaderAuthStrategy implements AuthenticationStrategy {

    @Override
    public String getType() {
        return AuthConfig.AuthType.CUSTOM;
    }

    @Override
    public String getDisplayName() {
        return "自定义Header认证";
    }

    @Override
    public void applyAuth(HttpHeaders headers, AuthConfig config) {
        Map<String, String> customHeaders = config.getCustomHeaders();
        if (customHeaders == null || customHeaders.isEmpty()) {
            return;
        }

        customHeaders.forEach((name, value) -> {
            if (name != null && value != null) {
                headers.set(name, value);
            }
        });
    }

    @Override
    public ValidationResult validate(AuthConfig config) {
        Map<String, String> customHeaders = config.getCustomHeaders();
        if (customHeaders == null || customHeaders.isEmpty()) {
            return ValidationResult.failure("自定义Header不能为空");
        }

        // 检查是否有空的键或值
        for (Map.Entry<String, String> entry : customHeaders.entrySet()) {
            if (entry.getKey() == null || entry.getKey().trim().isEmpty()) {
                return ValidationResult.failure("Header名称不能为空");
            }
        }

        return ValidationResult.success();
    }

    @Override
    public String[] getSensitiveFields() {
        // 自定义头中可能包含敏感信息
        return new String[]{"customHeaders"};
    }
}
