package com.actionow.ai.controller;

import com.actionow.ai.plugin.PluginRegistry;
import com.actionow.ai.plugin.model.ResponseMode;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireSystemTenant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 插件管理控制器
 *
 * @author Actionow
 */
@Slf4j
@RestController
@RequestMapping("/plugins")
@RequiredArgsConstructor
@RequireSystemTenant(minRole = "ADMIN")
public class PluginController {

    private final PluginRegistry pluginRegistry;

    /**
     * 获取所有已注册的插件
     */
    @GetMapping
    public Result<List<PluginInfo>> listPlugins() {
        List<PluginInfo> plugins = pluginRegistry.getPluginInfos().stream()
            .map(info -> new PluginInfo(
                info.pluginId(),
                info.pluginName(),
                info.version(),
                info.description(),
                info.supportedTypes(),
                info.supportedModes().stream().map(ResponseMode::getCode).collect(java.util.stream.Collectors.toSet())
            ))
            .toList();
        return Result.success(plugins);
    }

    /**
     * 获取插件详情
     */
    @GetMapping("/{pluginId}")
    public Result<PluginInfo> getPlugin(@PathVariable String pluginId) {
        return pluginRegistry.findPlugin(pluginId)
            .map(plugin -> {
                PluginInfo info = new PluginInfo(
                    plugin.getPluginId(),
                    plugin.getPluginName(),
                    plugin.getVersion(),
                    plugin.getDescription(),
                    plugin.getSupportedTypes(),
                    plugin.getSupportedModes().stream().map(ResponseMode::getCode).collect(java.util.stream.Collectors.toSet())
                );
                return Result.success(info);
            })
            .orElseGet(() -> Result.fail("插件不存在: " + pluginId));
    }

    /**
     * 根据生成类型查找插件
     */
    @GetMapping("/type/{type}")
    public Result<List<PluginInfo>> findByType(@PathVariable String type) {
        List<PluginInfo> plugins = pluginRegistry.findPluginsByType(type).stream()
            .map(plugin -> new PluginInfo(
                plugin.getPluginId(),
                plugin.getPluginName(),
                plugin.getVersion(),
                plugin.getDescription(),
                plugin.getSupportedTypes(),
                plugin.getSupportedModes().stream().map(ResponseMode::getCode).collect(java.util.stream.Collectors.toSet())
            ))
            .toList();
        return Result.success(plugins);
    }

    /**
     * 根据响应模式查找插件
     */
    @GetMapping("/mode/{mode}")
    public Result<List<PluginInfo>> findByMode(@PathVariable String mode) {
        ResponseMode responseMode = ResponseMode.fromCode(mode);
        List<PluginInfo> plugins = pluginRegistry.findPluginsByMode(responseMode).stream()
            .map(plugin -> new PluginInfo(
                plugin.getPluginId(),
                plugin.getPluginName(),
                plugin.getVersion(),
                plugin.getDescription(),
                plugin.getSupportedTypes(),
                plugin.getSupportedModes().stream().map(ResponseMode::getCode).collect(java.util.stream.Collectors.toSet())
            ))
            .toList();
        return Result.success(plugins);
    }

    /**
     * 插件信息DTO
     */
    public record PluginInfo(
        String pluginId,
        String pluginName,
        String version,
        String description,
        Set<String> supportedTypes,
        Set<String> supportedModes
    ) {}
}
