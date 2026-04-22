package com.actionow.ai.plugin.exception;

/**
 * 插件未找到异常
 *
 * @author Actionow
 */
public class PluginNotFoundException extends PluginException {

    public PluginNotFoundException(String pluginId) {
        super("PLUGIN_NOT_FOUND", "插件不存在: " + pluginId);
    }
}
