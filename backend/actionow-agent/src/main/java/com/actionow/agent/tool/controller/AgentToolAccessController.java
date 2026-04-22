package com.actionow.agent.tool.controller;

import com.actionow.agent.tool.dto.AgentToolAccessRequest;
import com.actionow.agent.tool.dto.AgentToolAccessResponse;
import com.actionow.agent.tool.dto.ToolInfo;
import com.actionow.agent.tool.service.AgentToolAccessService;
import com.actionow.common.core.result.PageResult;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireSystemTenant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Agent 工具访问权限控制器
 *
 * @author Actionow
 */
@Tag(name = "Agent Tool Access", description = "Agent 工具访问权限管理")
@RestController
@RequestMapping("/agent/tool-access")
@RequiredArgsConstructor
@RequireSystemTenant(minRole = "ADMIN")
public class AgentToolAccessController {

    private final AgentToolAccessService agentToolAccessService;

    @Operation(summary = "分页查询工具权限")
    @GetMapping
    public Result<PageResult<AgentToolAccessResponse>> page(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Long current,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") Long size,
            @Parameter(description = "Agent 类型") @RequestParam(required = false) String agentType,
            @Parameter(description = "工具分类") @RequestParam(required = false) String toolCategory,
            @Parameter(description = "工具 ID") @RequestParam(required = false) String toolId,
            @Parameter(description = "是否启用") @RequestParam(required = false) Boolean enabled) {
        return Result.success(agentToolAccessService.findPage(current, size, agentType, toolCategory, toolId, enabled));
    }

    @Operation(summary = "根据 ID 获取工具权限")
    @GetMapping("/{id}")
    public Result<AgentToolAccessResponse> getById(@PathVariable String id) {
        return Result.success(agentToolAccessService.getById(id));
    }

    @Operation(summary = "根据 Agent 类型获取工具权限")
    @GetMapping("/agent/{agentType}")
    public Result<List<AgentToolAccessResponse>> getByAgentType(@PathVariable String agentType) {
        return Result.success(agentToolAccessService.getByAgentType(agentType.toUpperCase()));
    }

    @Operation(summary = "根据 Agent 类型和分类获取工具权限")
    @GetMapping("/agent/{agentType}/category/{category}")
    public Result<List<AgentToolAccessResponse>> getByAgentTypeAndCategory(
            @PathVariable String agentType, @PathVariable String category) {
        return Result.success(agentToolAccessService.getByAgentTypeAndCategory(
                agentType.toUpperCase(), category.toUpperCase()));
    }

    @Operation(summary = "根据工具 ID 获取权限（哪些 Agent 可使用）")
    @GetMapping("/tool/{toolId}")
    public Result<List<AgentToolAccessResponse>> getByToolId(@PathVariable String toolId) {
        return Result.success(agentToolAccessService.getByToolId(toolId));
    }

    @Operation(summary = "获取 Agent 可用的所有工具")
    @GetMapping("/agent/{agentType}/tools")
    public Result<List<ToolInfo>> getAvailableTools(
            @PathVariable String agentType,
            @Parameter(description = "用户 ID") @RequestParam(required = false) String userId) {
        return Result.success(agentToolAccessService.getAvailableTools(agentType.toUpperCase(), userId));
    }

    @Operation(summary = "创建工具权限")
    @PostMapping
    public Result<AgentToolAccessResponse> create(@Valid @RequestBody AgentToolAccessRequest request) {
        return Result.success(agentToolAccessService.create(request));
    }

    @Operation(summary = "批量创建工具权限")
    @PostMapping("/batch")
    public Result<List<AgentToolAccessResponse>> createBatch(@Valid @RequestBody List<AgentToolAccessRequest> requests) {
        return Result.success(agentToolAccessService.createBatch(requests));
    }

    @Operation(summary = "更新工具权限")
    @PutMapping("/{id}")
    public Result<AgentToolAccessResponse> update(@PathVariable String id,
                                                   @Valid @RequestBody AgentToolAccessRequest request) {
        return Result.success(agentToolAccessService.update(id, request));
    }

    @Operation(summary = "删除工具权限")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        agentToolAccessService.delete(id);
        return Result.success();
    }

    @Operation(summary = "启用/禁用工具权限")
    @PutMapping("/{id}/toggle")
    public Result<Void> toggleEnabled(@PathVariable String id,
                                       @RequestParam Boolean enabled) {
        agentToolAccessService.toggleEnabled(id, enabled);
        return Result.success();
    }

    @Operation(summary = "检查工具访问权限")
    @GetMapping("/check")
    public Result<Boolean> checkAccess(
            @RequestParam String agentType,
            @RequestParam String toolCategory,
            @RequestParam String toolId) {
        return Result.success(agentToolAccessService.hasAccess(
                agentType.toUpperCase(), toolCategory.toUpperCase(), toolId));
    }

    @Operation(summary = "刷新缓存")
    @PostMapping("/cache/refresh")
    public Result<Void> refreshCache() {
        agentToolAccessService.refreshCache();
        return Result.success();
    }
}
