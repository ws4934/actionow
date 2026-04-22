package com.actionow.ai.plugin.exception;

/**
 * 插件限流异常
 *
 * @author Actionow
 */
public class PluginRateLimitException extends PluginException {

    public PluginRateLimitException(String providerId) {
        super("RATE_LIMIT_EXCEEDED", "请求过于频繁，请稍后重试", providerId);
    }

    public PluginRateLimitException(String message, String providerId) {
        super("RATE_LIMIT_EXCEEDED", message, providerId);
    }
}
