package com.actionow.agent.runtime;

import com.actionow.agent.config.constant.AgentExecutionMode;
import com.actionow.agent.resolution.dto.ResolvedAgentProfile;
import com.actionow.agent.tool.dto.ToolInfo;
import com.actionow.agent.tool.registry.ProjectToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 默认工具访问策略。
 *
 * <p>直接工具（CHAT / MISSION / BOTH）由 @ChatDirectTool / @MissionDirectTool 注解声明，
 * 经 ProjectToolScanner 扫描后写入 ToolInfo.directToolMode 字段，
 * 不再硬编码工具名常量。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultToolAccessPolicy implements ToolAccessPolicy {

    private final ProjectToolRegistry toolRegistry;

    @Override
    public List<String> filterToolIds(AgentExecutionMode mode, ResolvedAgentProfile profile) {
        Set<String> allowed = new LinkedHashSet<>();

        // 1. Skill 内的工具（从 ResolvedAgentProfile 获取）
        if (profile != null && profile.getResolvedTools() != null && !profile.getResolvedTools().isEmpty()) {
            profile.getResolvedTools().stream()
                    .filter(ToolInfo::isAvailable)
                    .filter(tool -> isAllowed(mode, tool))
                    .map(ToolInfo::getToolId)
                    .forEach(allowed::add);
        } else if (profile != null && profile.getResolvedToolIds() != null) {
            allowed.addAll(profile.getResolvedToolIds());
        }

        // 2. 直接工具（从注解元数据获取）
        toolRegistry.getAllProjectTools().stream()
                .filter(tool -> isDirectToolForMode(mode, tool))
                .map(ToolInfo::getToolId)
                .forEach(allowed::add);

        return List.copyOf(allowed);
    }

    /**
     * 判断工具是否在当前执行模式下作为直接工具注入。
     * directToolMode: "CHAT" | "MISSION" | "BOTH" | null
     */
    private boolean isDirectToolForMode(AgentExecutionMode mode, ToolInfo tool) {
        String directMode = tool.getDirectToolMode();
        if (directMode == null) {
            return false;
        }
        return switch (directMode) {
            case "BOTH" -> true;
            case "CHAT" -> mode == AgentExecutionMode.CHAT || mode == AgentExecutionMode.BOTH;
            case "MISSION" -> mode == AgentExecutionMode.MISSION || mode == AgentExecutionMode.BOTH;
            default -> false;
        };
    }

    /**
     * 判断 Skill 内的工具是否在当前模式下被允许。
     * 仅过滤掉模式冲突的直接工具（例如 CHAT 模式下不允许 MISSION-only 的直接工具）。
     * 非直接工具（directToolMode=null）始终允许。
     */
    private boolean isAllowed(AgentExecutionMode mode, ToolInfo tool) {
        String directMode = tool.getDirectToolMode();
        if (directMode == null) {
            return true;
        }
        if ("BOTH".equals(directMode)) {
            return true;
        }
        if (mode == AgentExecutionMode.CHAT) {
            return !"MISSION".equals(directMode);
        }
        if (mode == AgentExecutionMode.MISSION) {
            return !"CHAT".equals(directMode);
        }
        return true;
    }
}
