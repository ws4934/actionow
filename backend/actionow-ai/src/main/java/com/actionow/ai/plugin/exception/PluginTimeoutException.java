package com.actionow.ai.plugin.exception;

/**
 * 插件超时异常
 *
 * @author Actionow
 */
public class PluginTimeoutException extends PluginException {

    public PluginTimeoutException(String message, String providerId) {
        super("TIMEOUT", message, providerId);
    }

    public PluginTimeoutException(String message, String providerId, Throwable cause) {
        super("TIMEOUT", message, providerId, cause);
    }
}
