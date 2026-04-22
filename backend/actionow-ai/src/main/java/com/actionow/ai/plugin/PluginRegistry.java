package com.actionow.ai.plugin;

import com.actionow.ai.plugin.model.ResponseMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 插件注册中心
 * 管理所有AI模型插件的注册和查找
 *
 * @author Actionow
 */
@Slf4j
@Component
public class PluginRegistry {

    private final Map<String, AiModelPlugin> plugins = new ConcurrentHashMap<>();

    /**
     * 注册插件
     *
     * @param plugin 插件实例
     */
    public void register(AiModelPlugin plugin) {
        if (plugin == null || plugin.getPluginId() == null) {
            throw new IllegalArgumentException("Plugin or plugin ID cannot be null");
        }

        String pluginId = plugin.getPluginId().toLowerCase();
        if (plugins.containsKey(pluginId)) {
            log.warn("Plugin {} already registered, will be replaced", pluginId);
        }

        plugins.put(pluginId, plugin);
        log.info("Registered plugin: {} ({}), supported types: {}, supported modes: {}",
            pluginId, plugin.getPluginName(),
            plugin.getSupportedTypes(), plugin.getSupportedModes());
    }

    /**
     * 注销插件
     *
     * @param pluginId 插件ID
     */
    public void unregister(String pluginId) {
        if (pluginId == null) return;

        AiModelPlugin removed = plugins.remove(pluginId.toLowerCase());
        if (removed != null) {
            try {
                removed.destroy();
            } catch (Exception e) {
                log.warn("Error destroying plugin {}: {}", pluginId, e.getMessage());
            }
            log.info("Unregistered plugin: {}", pluginId);
        }
    }

    /**
     * 获取插件
     *
     * @param pluginId 插件ID
     * @return 插件实例
     * @throws IllegalArgumentException 如果插件不存在
     */
    public AiModelPlugin getPlugin(String pluginId) {
        AiModelPlugin plugin = plugins.get(pluginId.toLowerCase());
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin not found: " + pluginId);
        }
        return plugin;
    }

    /**
     * 获取插件（可选）
     *
     * @param pluginId 插件ID
     * @return 插件实例
     */
    public Optional<AiModelPlugin> findPlugin(String pluginId) {
        return Optional.ofNullable(plugins.get(pluginId.toLowerCase()));
    }

    /**
     * 检查插件是否存在
     *
     * @param pluginId 插件ID
     * @return 是否存在
     */
    public boolean hasPlugin(String pluginId) {
        return plugins.containsKey(pluginId.toLowerCase());
    }

    /**
     * 获取所有已注册的插件
     *
     * @return 插件列表
     */
    public List<AiModelPlugin> getAllPlugins() {
        return new ArrayList<>(plugins.values());
    }

    /**
     * 获取所有插件ID
     *
     * @return 插件ID列表
     */
    public Set<String> getPluginIds() {
        return new HashSet<>(plugins.keySet());
    }

    /**
     * 根据生成类型查找支持的插件
     *
     * @param generationType 生成类型（IMAGE/VIDEO/AUDIO/TEXT）
     * @return 支持该类型的插件列表
     */
    public List<AiModelPlugin> findPluginsByType(String generationType) {
        return plugins.values().stream()
            .filter(p -> p.getSupportedTypes().contains(generationType.toUpperCase()))
            .toList();
    }

    /**
     * 根据响应模式查找支持的插件
     *
     * @param mode 响应模式
     * @return 支持该模式的插件列表
     */
    public List<AiModelPlugin> findPluginsByMode(ResponseMode mode) {
        return plugins.values().stream()
            .filter(p -> p.supportsMode(mode))
            .toList();
    }

    /**
     * 获取插件信息摘要
     *
     * @return 插件信息列表
     */
    public List<PluginInfo> getPluginInfos() {
        return plugins.values().stream()
            .map(p -> new PluginInfo(
                p.getPluginId(),
                p.getPluginName(),
                p.getVersion(),
                p.getDescription(),
                p.getSupportedTypes(),
                p.getSupportedModes()
            ))
            .toList();
    }

    /**
     * 插件信息
     */
    public record PluginInfo(
        String pluginId,
        String pluginName,
        String version,
        String description,
        Set<String> supportedTypes,
        Set<ResponseMode> supportedModes
    ) {}

    /**
     * 销毁所有插件
     */
    public void destroyAll() {
        plugins.values().forEach(plugin -> {
            try {
                plugin.destroy();
            } catch (Exception e) {
                log.warn("Error destroying plugin {}: {}", plugin.getPluginId(), e.getMessage());
            }
        });
        plugins.clear();
        log.info("All plugins destroyed");
    }
}
