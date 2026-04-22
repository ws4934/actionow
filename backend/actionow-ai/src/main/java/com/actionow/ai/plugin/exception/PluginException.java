package com.actionow.ai.plugin.exception;

import lombok.Getter;

/**
 * 插件异常基类
 *
 * @author Actionow
 */
@Getter
public class PluginException extends RuntimeException {

    private final String errorCode;
    private final String providerId;

    public PluginException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.providerId = null;
    }

    public PluginException(String errorCode, String message, String providerId) {
        super(message);
        this.errorCode = errorCode;
        this.providerId = providerId;
    }

    public PluginException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.providerId = null;
    }

    public PluginException(String errorCode, String message, String providerId, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.providerId = providerId;
    }

    /**
     * 创建插件未找到异常
     */
    public static PluginNotFoundException pluginNotFound(String pluginId) {
        return new PluginNotFoundException(pluginId);
    }

    /**
     * 创建提供商未找到异常
     */
    public static ProviderNotFoundException providerNotFound(String providerId) {
        return new ProviderNotFoundException(providerId);
    }

    /**
     * 创建配置无效异常
     */
    public static PluginConfigException invalidConfig(String message) {
        return new PluginConfigException(message);
    }

    /**
     * 创建认证失败异常
     */
    public static PluginAuthException authFailed(String message) {
        return new PluginAuthException(message);
    }

    /**
     * 创建执行失败异常
     */
    public static PluginExecutionException executionFailed(String message, String providerId) {
        return new PluginExecutionException(message, providerId);
    }

    /**
     * 创建限流异常
     */
    public static PluginRateLimitException rateLimitExceeded(String providerId) {
        return new PluginRateLimitException(providerId);
    }

    /**
     * 创建熔断异常
     */
    public static PluginCircuitBreakerException circuitBreakerOpen(String providerId) {
        return new PluginCircuitBreakerException(providerId);
    }

    /**
     * 创建超时异常
     */
    public static PluginTimeoutException timeout(String message, String providerId) {
        return new PluginTimeoutException(message, providerId);
    }
}
