package com.actionow.ai.config;

import com.actionow.ai.plugin.AiModelPlugin;
import com.actionow.ai.plugin.PluginRegistry;
import com.actionow.ai.plugin.http.PluginHttpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;
import java.util.List;

/**
 * 插件注册配置
 * 在应用启动完成后注册所有插件
 * 使用 ApplicationRunner 确保所有 Bean 都已创建完成
 *
 * @author Actionow
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class PluginRegistrationConfig implements ApplicationRunner {

    private final List<AiModelPlugin> plugins;
    private final PluginRegistry pluginRegistry;
    private final PluginHttpClient pluginHttpClient;

    @Override
    public void run(ApplicationArguments args) {
        registerPlugins();
    }

    /**
     * 注册所有插件
     */
    private void registerPlugins() {
        if (plugins != null && !plugins.isEmpty()) {
            log.info("Auto-registering {} plugins...", plugins.size());
            plugins.forEach(plugin -> {
                try {
                    pluginRegistry.register(plugin);
                    log.debug("Registered plugin: {} ({})", plugin.getPluginId(), plugin.getPluginName());
                } catch (Exception e) {
                    log.error("Failed to register plugin {}: {}", plugin.getPluginId(), e.getMessage());
                }
            });
            log.info("Plugin registration completed. Total plugins: {}", pluginRegistry.getPluginIds().size());
        } else {
            log.info("No plugins found to register");
        }
    }

    @PreDestroy
    public void destroy() {
        log.info("Shutting down plugin system...");
        try {
            pluginHttpClient.shutdown();
        } catch (Exception e) {
            log.warn("Error shutting down HTTP client: {}", e.getMessage());
        }
        // PollingManager 由 @PreDestroy 自行管理生命周期，无需在此关闭
        try {
            pluginRegistry.destroyAll();
        } catch (Exception e) {
            log.warn("Error destroying registry: {}", e.getMessage());
        }
        log.info("Plugin system shutdown completed");
    }
}
