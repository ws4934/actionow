package com.actionow.ai.config;

import com.actionow.ai.plugin.PluginExecutor;
import com.actionow.ai.plugin.PluginRegistry;
import com.actionow.ai.plugin.auth.AuthStrategyFactory;
import com.actionow.ai.plugin.groovy.GroovyRequestBuilder;
import com.actionow.ai.plugin.groovy.GroovyResponseMapper;
import com.actionow.ai.plugin.groovy.GroovySandboxConfig;
import com.actionow.ai.plugin.groovy.GroovyScriptCache;
import com.actionow.ai.plugin.groovy.GroovyScriptEngine;
import com.actionow.ai.plugin.groovy.GroovyScriptValidator;
import com.actionow.ai.plugin.groovy.binding.BindingFactory;
import com.actionow.ai.plugin.http.PluginHttpClient;
import com.actionow.ai.plugin.impl.GroovyPlugin;
import com.actionow.ai.plugin.polling.PollingManager;
import com.actionow.ai.plugin.resilience.PluginResilienceConfig;
import com.actionow.common.core.security.InternalAuthProperties;
import com.actionow.ai.service.AssetInputResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 插件基础设施配置
 * 创建插件系统所需的基础 Bean
 *
 * @author Actionow
 */
@Slf4j
@Configuration
public class PluginAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AuthStrategyFactory authStrategyFactory() {
        return new AuthStrategyFactory();
    }

    @Bean
    @ConditionalOnMissingBean
    public PluginResilienceConfig pluginResilienceConfig(AiRuntimeConfigService runtimeConfig) {
        return new PluginResilienceConfig(runtimeConfig);
    }

    // ========== Groovy 脚本引擎相关 Bean ==========
    // GroovySandboxConfig 是 @Component，自动注册

    @Bean
    @ConditionalOnMissingBean
    public GroovyScriptCache groovyScriptCache() {
        return new GroovyScriptCache();
    }

    @Bean
    @ConditionalOnMissingBean
    public GroovyScriptValidator groovyScriptValidator(GroovySandboxConfig sandboxConfig) {
        return new GroovyScriptValidator(sandboxConfig);
    }

    @Bean
    @ConditionalOnMissingBean
    public GroovyScriptEngine groovyScriptEngine(GroovyScriptCache scriptCache,
                                                  GroovySandboxConfig sandboxConfig,
                                                  GroovyScriptValidator scriptValidator,
                                                  AiRuntimeConfigService runtimeConfig) {
        return new GroovyScriptEngine(scriptCache, sandboxConfig, scriptValidator, runtimeConfig);
    }

    @Bean
    @ConditionalOnMissingBean
    public GroovyRequestBuilder groovyRequestBuilder(GroovyScriptEngine scriptEngine,
                                                      AssetInputResolver assetInputResolver) {
        return new GroovyRequestBuilder(scriptEngine, assetInputResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    public GroovyResponseMapper groovyResponseMapper(GroovyScriptEngine scriptEngine,
                                                      BindingFactory bindingFactory) {
        return new GroovyResponseMapper(scriptEngine, bindingFactory);
    }

    // ========== 插件相关 Bean ==========

    @Bean
    @ConditionalOnMissingBean
    public PluginHttpClient pluginHttpClient(AuthStrategyFactory authStrategyFactory,
                                              PluginResilienceConfig resilienceConfig,
                                              InternalAuthProperties internalAuthProperties,
                                              AiRuntimeConfigService aiRuntimeConfig) {
        return new PluginHttpClient(authStrategyFactory, resilienceConfig, internalAuthProperties, aiRuntimeConfig);
    }

    @Bean
    @ConditionalOnMissingBean
    public PluginRegistry pluginRegistry() {
        return new PluginRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public PluginExecutor pluginExecutor(PluginRegistry pluginRegistry, PluginHttpClient pluginHttpClient,
                                          PollingManager pollingManager) {
        return new PluginExecutor(pluginRegistry, pluginHttpClient, pollingManager);
    }

    // ========== 插件实现 Bean ==========

    @Bean
    public GroovyPlugin groovyPlugin(AuthStrategyFactory authStrategyFactory,
                                     PluginHttpClient httpClient,
                                     GroovyScriptEngine scriptEngine,
                                     GroovyRequestBuilder requestBuilder,
                                     GroovyResponseMapper responseMapper,
                                     BindingFactory bindingFactory) {
        log.info("Creating GroovyPlugin bean");
        return new GroovyPlugin(authStrategyFactory, httpClient, scriptEngine, requestBuilder, responseMapper, bindingFactory);
    }
}
