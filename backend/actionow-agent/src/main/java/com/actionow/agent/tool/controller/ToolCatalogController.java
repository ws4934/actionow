package com.actionow.agent.tool.controller;

import com.actionow.agent.tool.dto.ToolInfo;
import com.actionow.agent.tool.service.ToolCatalogService;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.result.PageResult;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireWorkspaceMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tool Catalog 查询控制器。
 */
@Tag(name = "Tool Catalog", description = "工具目录查询")
@RestController
@RequestMapping("/agent/tools")
@RequiredArgsConstructor
@RequireWorkspaceMember
public class ToolCatalogController {

    private final ToolCatalogService toolCatalogService;

    @Operation(summary = "分页查询工具目录")
    @GetMapping
    public Result<PageResult<ToolInfo>> page(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Long current,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") Long size,
            @Parameter(description = "关键词（toolId/toolName/displayName/description/summary/tags）") @RequestParam(required = false) String keyword,
            @Parameter(description = "动作类型：READ | SEARCH | WRITE | GENERATE | CONTROL | UNKNOWN") @RequestParam(required = false) String actionType,
            @Parameter(description = "标签") @RequestParam(required = false) String tag) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        return Result.success(toolCatalogService.findPage(current, size, keyword, actionType, tag, workspaceId));
    }

    @Operation(summary = "获取工具详情")
    @GetMapping("/{toolId}")
    public Result<ToolInfo> getTool(@PathVariable String toolId) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        return Result.success(toolCatalogService.getTool(toolId, workspaceId));
    }
}
