package com.actionow.ai.plugin.exception;

/**
 * 插件熔断异常
 *
 * @author Actionow
 */
public class PluginCircuitBreakerException extends PluginException {

    public PluginCircuitBreakerException(String providerId) {
        super("CIRCUIT_BREAKER_OPEN", "服务暂时不可用，熔断器已打开", providerId);
    }
}
