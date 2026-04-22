package com.actionow.ai.plugin.exception;

/**
 * 插件执行异常
 *
 * @author Actionow
 */
public class PluginExecutionException extends PluginException {

    public PluginExecutionException(String message, String providerId) {
        super("EXECUTION_FAILED", message, providerId);
    }

    public PluginExecutionException(String message, String providerId, Throwable cause) {
        super("EXECUTION_FAILED", message, providerId, cause);
    }
}
