package com.actionow.ai.plugin.exception;

/**
 * 插件配置异常
 *
 * @author Actionow
 */
public class PluginConfigException extends PluginException {

    public PluginConfigException(String message) {
        super("INVALID_CONFIG", message);
    }

    public PluginConfigException(String message, Throwable cause) {
        super("INVALID_CONFIG", message, cause);
    }
}
