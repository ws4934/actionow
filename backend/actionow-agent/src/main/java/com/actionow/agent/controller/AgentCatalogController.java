package com.actionow.agent.controller;

import com.actionow.agent.config.dto.AgentConfigResponse;
import com.actionow.agent.config.service.AgentConfigService;
import com.actionow.agent.resolution.dto.ResolvedAgentProfile;
import com.actionow.agent.resolution.dto.ResolvedSkillInfo;
import com.actionow.agent.resolution.service.AgentResolutionService;
import com.actionow.agent.tool.dto.ToolInfo;
import com.actionow.common.core.context.UserContextHolder;
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

import java.util.List;

/**
 * Agent 目录与解析查询控制器。
 */
@Tag(name = "Agent Catalog", description = "Agent 目录与解析结果查询")
@RestController
@RequestMapping("/agent/agents")
@RequiredArgsConstructor
@RequireWorkspaceMember
public class AgentCatalogController {

    private final AgentConfigService agentConfigService;
    private final AgentResolutionService agentResolutionService;

    @Operation(summary = "查询当前工作空间可用的 Agent")
    @GetMapping("/available")
    public Result<List<AgentConfigResponse>> listAvailableAgents() {
        return Result.success(agentConfigService.findAvailableAgents(
                UserContextHolder.getWorkspaceId(),
                UserContextHolder.getUserId()));
    }

    @Operation(summary = "查询当前工作空间可独立调用的 Agent")
    @GetMapping("/standalone")
    public Result<List<AgentConfigResponse>> listStandaloneAgents() {
        return Result.success(agentConfigService.findStandaloneAgents(
                UserContextHolder.getWorkspaceId(),
                UserContextHolder.getUserId()));
    }

    @Operation(summary = "查询当前工作空间可用的协调者 Agent")
    @GetMapping("/coordinators")
    public Result<List<AgentConfigResponse>> listCoordinatorAgents() {
        return Result.success(agentConfigService.findCoordinators(
                UserContextHolder.getWorkspaceId(),
                UserContextHolder.getUserId()));
    }

    @Operation(summary = "查询 Agent 解析结果")
    @GetMapping("/{agentType}/resolved")
    public Result<ResolvedAgentProfile> getResolvedAgent(
            @PathVariable String agentType,
            @Parameter(description = "消息/会话级 Skill 覆盖列表，可重复传参：?skillNames=a&skillNames=b")
            @RequestParam(required = false) List<String> skillNames) {
        return Result.success(agentResolutionService.resolve(
                agentType.toUpperCase(),
                UserContextHolder.getWorkspaceId(),
                UserContextHolder.getUserId(),
                skillNames));
    }

    @Operation(summary = "查询 Agent 最终解析的 Skill 列表")
    @GetMapping("/{agentType}/skills")
    public Result<List<ResolvedSkillInfo>> getResolvedSkills(
            @PathVariable String agentType,
            @RequestParam(required = false) List<String> skillNames) {
        ResolvedAgentProfile profile = agentResolutionService.resolve(
                agentType.toUpperCase(),
                UserContextHolder.getWorkspaceId(),
                UserContextHolder.getUserId(),
                skillNames);
        return Result.success(profile.getResolvedSkills());
    }

    @Operation(summary = "查询 Agent 最终可见工具列表")
    @GetMapping("/{agentType}/tools")
    public Result<List<ToolInfo>> getResolvedTools(
            @PathVariable String agentType,
            @RequestParam(required = false) List<String> skillNames) {
        ResolvedAgentProfile profile = agentResolutionService.resolve(
                agentType.toUpperCase(),
                UserContextHolder.getWorkspaceId(),
                UserContextHolder.getUserId(),
                skillNames);
        return Result.success(profile.getResolvedTools());
    }
}
