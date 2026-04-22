package com.actionow.system.controller;

import com.actionow.common.core.result.PageResult;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireSystemTenant;
import com.actionow.common.security.util.SecurityUtils;
import com.actionow.system.dto.SystemConfigGroupedResponse;
import com.actionow.system.dto.SystemConfigRequest;
import com.actionow.system.dto.SystemConfigResponse;
import com.actionow.system.service.SystemConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统配置控制器
 *
 * @author Actionow
 */
@RestController
@RequestMapping("/system/configs")
@RequiredArgsConstructor
@RequireSystemTenant(minRole = "ADMIN")
public class SystemConfigController {

    private final SystemConfigService configService;

    /**
     * 分页查询配置列表
     */
    @GetMapping
    public Result<PageResult<SystemConfigResponse>> listPage(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "20") Long size,
            @RequestParam(required = false) String configType,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String module) {
        return Result.success(configService.listPage(current, size, configType, scope, keyword, module));
    }

    /**
     * 创建配置
     */
    @PostMapping
    public Result<SystemConfigResponse> create(@Valid @RequestBody SystemConfigRequest request) {
        String operatorId = SecurityUtils.requireCurrentUserId();
        return Result.success(configService.create(request, operatorId));
    }

    /**
     * 更新配置
     */
    @PutMapping("/{id}")
    public Result<SystemConfigResponse> update(@PathVariable String id,
                                               @RequestBody SystemConfigRequest request) {
        String operatorId = SecurityUtils.requireCurrentUserId();
        return Result.success(configService.update(id, request, operatorId));
    }

    /**
     * 删除配置
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        String operatorId = SecurityUtils.requireCurrentUserId();
        configService.delete(id, operatorId);
        return Result.success();
    }

    /**
     * 获取配置详情
     */
    @GetMapping("/{id}")
    public Result<SystemConfigResponse> getById(@PathVariable String id) {
        return Result.success(configService.getById(id));
    }

    /**
     * 获取配置值
     */
    @GetMapping("/value")
    public Result<String> getConfigValue(@RequestParam String configKey,
                                         @RequestParam(defaultValue = "GLOBAL") String scope,
                                         @RequestParam(required = false) String scopeId,
                                         @RequestParam(required = false) String defaultValue) {
        return Result.success(configService.getConfigValueMasked(configKey, scope, scopeId, defaultValue));
    }

    /**
     * 按模块分组查询配置
     */
    @GetMapping("/grouped")
    public Result<List<SystemConfigGroupedResponse>> listGrouped() {
        return Result.success(configService.listGroupedByModule());
    }

    /**
     * 获取全局配置列表
     */
    @GetMapping("/global")
    public Result<List<SystemConfigResponse>> listGlobalConfigs() {
        return Result.success(configService.listGlobalConfigs());
    }

    /**
     * 按类型获取配置列表
     */
    @GetMapping("/by-type")
    public Result<List<SystemConfigResponse>> listByType(@RequestParam String configType,
                                                          @RequestParam(defaultValue = "GLOBAL") String scope) {
        return Result.success(configService.listByType(configType, scope));
    }

    /**
     * 获取工作空间配置列表
     */
    @GetMapping("/workspace/{workspaceId}")
    public Result<List<SystemConfigResponse>> listByWorkspace(@PathVariable String workspaceId) {
        return Result.success(configService.listByWorkspace(workspaceId));
    }

    /**
     * 刷新配置缓存
     */
    @PostMapping("/refresh-cache")
    public Result<Void> refreshCache() {
        configService.refreshCache();
        return Result.success();
    }
}
