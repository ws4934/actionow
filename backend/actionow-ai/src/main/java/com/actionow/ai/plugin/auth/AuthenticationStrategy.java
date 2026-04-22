package com.actionow.ai.plugin.auth;

import org.springframework.http.HttpHeaders;

/**
 * 认证策略接口
 * 定义不同的认证方式，如API Key、AK/SK、Bearer Token等
 *
 * @author Actionow
 */
public interface AuthenticationStrategy {

    /**
     * 获取认证类型
     *
     * @return 认证类型标识
     */
    String getType();

    /**
     * 获取认证类型显示名称
     *
     * @return 显示名称
     */
    String getDisplayName();

    /**
     * 将认证信息应用到HTTP请求头
     *
     * @param headers HTTP请求头
     * @param config 认证配置
     */
    void applyAuth(HttpHeaders headers, AuthConfig config);

    /**
     * 刷新凭证（如果需要）
     * 对于OAuth等需要刷新token的场景
     *
     * @param config 认证配置
     * @return 刷新后的配置，如果无需刷新返回原配置
     */
    default AuthConfig refreshIfNeeded(AuthConfig config) {
        return config;
    }

    /**
     * 验证认证配置是否有效
     *
     * @param config 认证配置
     * @return 验证结果
     */
    default ValidationResult validate(AuthConfig config) {
        return ValidationResult.success();
    }

    /**
     * 获取需要脱敏的字段
     *
     * @return 需要脱敏的字段名数组
     */
    default String[] getSensitiveFields() {
        return new String[]{"apiKey", "secretKey", "token", "password"};
    }

    /**
     * 验证结果
     */
    record ValidationResult(boolean valid, String message) {
        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult failure(String message) {
            return new ValidationResult(false, message);
        }
    }
}
