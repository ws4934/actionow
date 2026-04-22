package com.actionow.ai.plugin.exception;

/**
 * 提供商未找到异常
 *
 * @author Actionow
 */
public class ProviderNotFoundException extends PluginException {

    public ProviderNotFoundException(String providerId) {
        super("PROVIDER_NOT_FOUND", "提供商不存在: " + providerId, providerId);
    }
}
