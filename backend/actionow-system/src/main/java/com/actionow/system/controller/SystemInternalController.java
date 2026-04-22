package com.actionow.system.controller;

import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.IgnoreAuth;
import com.actionow.system.mapper.SystemConfigMapper;
import com.actionow.system.service.PlatformStatsService;
import com.actionow.system.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 系统内部接口（供其他微服务调用）
 *
 * @author Actionow
 */
@RestController
@RequestMapping("/internal/system")
@RequiredArgsConstructor
@IgnoreAuth
public class SystemInternalController {

    private final PlatformStatsService statsService;
    private final SystemConfigService configService;
    private final SystemConfigMapper configMapper;

    /**
     * 记录统计数据
     */
    @PostMapping("/stats/record")
    public Result<Void> recordStats(@RequestParam String metricType,
                                    @RequestParam Long value,
                                    @RequestParam(required = false) String workspaceId) {
        statsService.recordStats(metricType, value, workspaceId);
        return Result.success();
    }

    /**
     * 获取全局配置值
     * 供其他微服务获取敏感配置（如 API Keys）
     *
     * @param configKey 配置键
     * @return 配置值
     */
    @GetMapping("/config/value")
    public Result<String> getConfigValue(@RequestParam String configKey) {
        String value = configService.getConfigValue(configKey, "GLOBAL", null);
        return Result.success(value);
    }

    /**
     * 批量获取配置值（按 key 前缀）
     * 供各模块 RuntimeConfigService 启动时批量加载
     *
     * @param prefix 配置键前缀（如 "runtime.agent"）
     * @return configKey → configValue 映射
     */
    @GetMapping("/config/batch")
    public Result<java.util.Map<String, String>> getConfigBatch(@RequestParam String prefix) {
        java.util.Map<String, String> result = new java.util.LinkedHashMap<>();
        configMapper.selectByKeyPrefix(prefix).forEach(c -> result.put(c.getConfigKey(), c.getConfigValue()));
        return Result.success(result);
    }
}
