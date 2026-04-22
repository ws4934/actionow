package com.actionow.agent.config.controller;

import com.actionow.agent.saa.factory.SaaAgentFactory;
import com.actionow.agent.config.dto.AgentConfigRequest;
import com.actionow.agent.config.dto.AgentConfigResponse;
import com.actionow.agent.config.dto.AgentConfigVersionResponse;
import com.actionow.agent.config.service.AgentConfigService;
import com.actionow.common.core.result.PageResult;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireSystemTenant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Agent 配置控制器
 *
 * @author Actionow
 */
@Slf4j
@Tag(name = "Agent Config", description = "Agent 配置管理")
@RestController
@RequestMapping("/agent/configs")
@RequiredArgsConstructor
@RequireSystemTenant(minRole = "ADMIN")
public class AgentConfigController {

    private final AgentConfigService agentConfigService;
    private final SaaAgentFactory saaAgentFactory;

    @Operation(summary = "分页查询 Agent 配置")
    @GetMapping
    public Result<PageResult<AgentConfigResponse>> page(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Long current,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") Long size,
            @Parameter(description = "Agent 类型") @RequestParam(required = false) String agentType,
            @Parameter(description = "是否启用") @RequestParam(required = false) Boolean enabled,
            @Parameter(description = "LLM Provider ID") @RequestParam(required = false) String llmProviderId) {
        return Result.success(agentConfigService.findPage(current, size, agentType, enabled, llmProviderId));
    }

    @Operation(summary = "获取所有启用的 Agent 配置")
    @GetMapping("/enabled")
    public Result<List<AgentConfigResponse>> listEnabled() {
        return Result.success(agentConfigService.findAllEnabled());
    }

    @Operation(summary = "根据 ID 获取 Agent 配置")
    @GetMapping("/{id}")
    public Result<AgentConfigResponse> getById(@PathVariable String id) {
        return Result.success(agentConfigService.getById(id));
    }

    @Operation(summary = "根据 Agent 类型获取配置")
    @GetMapping("/type/{agentType}")
    public Result<AgentConfigResponse> getByAgentType(@PathVariable String agentType) {
        return Result.success(agentConfigService.getByAgentType(agentType.toUpperCase()));
    }

    @Operation(summary = "获取解析后的完整提示词")
    @GetMapping("/type/{agentType}/prompt")
    public Result<String> getResolvedPrompt(@PathVariable String agentType) {
        return Result.success(agentConfigService.getResolvedPrompt(agentType.toUpperCase()));
    }

    @Operation(summary = "创建 Agent 配置")
    @PostMapping
    public Result<AgentConfigResponse> create(@Valid @RequestBody AgentConfigRequest request) {
        return Result.success(agentConfigService.create(request));
    }

    @Operation(summary = "更新 Agent 配置")
    @PutMapping("/{id}")
    public Result<AgentConfigResponse> update(@PathVariable String id,
                                               @Valid @RequestBody AgentConfigRequest request) {
        return Result.success(agentConfigService.update(id, request));
    }

    @Operation(summary = "删除 Agent 配置")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        agentConfigService.delete(id);
        return Result.success();
    }

    @Operation(summary = "启用/禁用 Agent 配置")
    @PutMapping("/{id}/toggle")
    public Result<Void> toggleEnabled(@PathVariable String id,
                                       @RequestParam Boolean enabled) {
        agentConfigService.toggleEnabled(id, enabled);
        return Result.success();
    }

    @Operation(summary = "获取配置版本历史")
    @GetMapping("/{id}/versions")
    public Result<List<AgentConfigVersionResponse>> getVersionHistory(@PathVariable String id) {
        return Result.success(agentConfigService.getVersionHistory(id));
    }

    @Operation(summary = "回滚到指定版本")
    @PostMapping("/{id}/rollback/{version}")
    public Result<AgentConfigResponse> rollback(@PathVariable String id,
                                                 @PathVariable Integer version) {
        return Result.success(agentConfigService.rollback(id, version));
    }

    @Operation(summary = "强制刷新缓存并重建 Agent（热更新）")
    @PostMapping("/reload")
    public Result<Void> reload() {
        log.info("开始执行完整热更新...");

        // 1. 刷新配置缓存（Redis + 本地）
        agentConfigService.refreshCache();

        // 2. 强制重建 Agent 系统（包括 LLM 缓存、工具缓存、Agent 实例）
        saaAgentFactory.forceRebuild();

        log.info("热更新完成: Agent 系统已完全重建");
        return Result.success();
    }
}
