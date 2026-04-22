package com.actionow.ai.plugin.exception;

/**
 * 插件认证异常
 *
 * @author Actionow
 */
public class PluginAuthException extends PluginException {

    public PluginAuthException(String message) {
        super("AUTH_FAILED", message);
    }

    public PluginAuthException(String message, Throwable cause) {
        super("AUTH_FAILED", message, cause);
    }
}
