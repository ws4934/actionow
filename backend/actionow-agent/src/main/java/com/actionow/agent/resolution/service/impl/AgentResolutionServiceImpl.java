package com.actionow.agent.resolution.service.impl;

import com.actionow.agent.config.constant.AgentExecutionMode;
import com.actionow.agent.config.constant.AgentSkillLoadMode;
import com.actionow.agent.config.dto.AgentConfigResponse;
import com.actionow.agent.config.entity.AgentConfigEntity;
import com.actionow.agent.config.service.AgentConfigService;
import com.actionow.agent.entity.AgentSkillEntity;
import com.actionow.agent.registry.DatabaseSkillRegistry;
import com.actionow.agent.resolution.dto.ResolvedAgentProfile;
import com.actionow.agent.resolution.dto.ResolvedSkillInfo;
import com.actionow.agent.resolution.service.AgentResolutionService;
import com.actionow.agent.tool.dto.ToolInfo;
import com.actionow.agent.tool.service.AgentToolAccessService;
import com.actionow.agent.tool.registry.ProjectToolRegistry;
import com.actionow.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Agent 解析服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentResolutionServiceImpl implements AgentResolutionService {

    private final AgentConfigService agentConfigService;
    private final DatabaseSkillRegistry skillRegistry;
    private final ProjectToolRegistry toolRegistry;
    private final AgentToolAccessService toolAccessService;

    @Override
    public ResolvedAgentProfile resolve(String agentType, String workspaceId, String userId, List<String> requestedSkillNames) {
        AgentConfigEntity config = requireVisibleAgent(agentType, workspaceId, userId);

        Map<String, AgentSkillEntity> visibleSkills = skillRegistry.getCacheSnapshotForWorkspace(workspaceId);
        List<String> finalSkillNames = resolveSkillNames(config, visibleSkills, requestedSkillNames);
        List<ResolvedSkillInfo> resolvedSkills = buildResolvedSkills(finalSkillNames, visibleSkills);
        List<String> resolvedToolIds = collectToolIds(resolvedSkills);
        List<ToolInfo> resolvedTools = toolAccessService.getAvailableTools(config.getAgentType(), userId, resolvedToolIds);

        return ResolvedAgentProfile.builder()
                .agentType(config.getAgentType())
                .agentName(config.getAgentName())
                .coordinator(Boolean.TRUE.equals(config.getIsCoordinator()))
                .standaloneEnabled(config.getStandaloneEnabled())
                .workspaceId(workspaceId)
                .userId(userId)
                .llmProviderId(config.getLlmProviderId())
                .resolvedPrompt(agentConfigService.getResolvedPrompt(config.getAgentType()))
                .skillLoadMode(AgentSkillLoadMode.fromCode(config.getSkillLoadMode()).name())
                .executionMode(AgentExecutionMode.fromCode(config.getExecutionMode()).name())
                .defaultSkillNames(copyList(config.getDefaultSkillNames()))
                .allowedSkillNames(copyList(config.getAllowedSkillNames()))
                .requestedSkillNames(requestedSkillNames != null ? copyList(requestedSkillNames) : null)
                .resolvedSkillNames(finalSkillNames)
                .resolvedSkills(resolvedSkills)
                .subAgentTypes(copyList(resolveSubAgentTypes(config)))
                .resolvedToolIds(resolvedToolIds)
                .resolvedTools(resolvedTools)
                .build();
    }

    private AgentConfigEntity requireVisibleAgent(String agentType, String workspaceId, String userId) {
        List<AgentConfigResponse> visibleAgents = agentConfigService.findAvailableAgents(workspaceId, userId);
        boolean visible = visibleAgents.stream()
                .anyMatch(agent -> agent.getAgentType().equalsIgnoreCase(agentType));
        if (!visible) {
            throw new BusinessException("当前工作空间不可见 Agent: " + agentType);
        }
        return agentConfigService.getEntityByAgentType(agentType);
    }

    private List<String> resolveSkillNames(AgentConfigEntity config,
                                           Map<String, AgentSkillEntity> visibleSkills,
                                           List<String> requestedSkillNames) {
        AgentSkillLoadMode loadMode = AgentSkillLoadMode.fromCode(config.getSkillLoadMode());
        if (Boolean.TRUE.equals(config.getIsCoordinator())
                && requestedSkillNames == null
                && (config.getDefaultSkillNames() == null || config.getDefaultSkillNames().isEmpty())) {
            return List.of();
        }
        Set<String> candidateNames = new LinkedHashSet<>();

        if (requestedSkillNames != null) {
            candidateNames.addAll(requestedSkillNames);
        } else {
            switch (loadMode) {
                case DEFAULT_ONLY -> candidateNames.addAll(copyList(config.getDefaultSkillNames()));
                case REQUEST_SCOPED -> candidateNames.addAll(copyList(config.getDefaultSkillNames()));
                case DISABLED -> {
                    // keep empty
                }
                case ALL_ENABLED -> candidateNames.addAll(visibleSkills.keySet());
            }
        }

        if (loadMode == AgentSkillLoadMode.DISABLED) {
            return List.of();
        }

        List<String> allowed = copyList(config.getAllowedSkillNames());
        Set<String> visibleNames = visibleSkills.keySet();

        List<String> result = new ArrayList<>();
        for (String name : candidateNames) {
            if (name == null || name.isBlank()) {
                continue;
            }
            if (!visibleNames.contains(name)) {
                log.warn("Agent {} 请求的 Skill 在当前工作空间不可见，已跳过: {}", config.getAgentType(), name);
                continue;
            }
            if (!allowed.isEmpty() && allowed.stream().noneMatch(allowedName -> allowedName.equalsIgnoreCase(name))) {
                log.warn("Agent {} 请求的 Skill 不在 allowedSkillNames 中，已跳过: {}", config.getAgentType(), name);
                continue;
            }
            result.add(name);
        }
        return result;
    }

    private List<ResolvedSkillInfo> buildResolvedSkills(List<String> skillNames, Map<String, AgentSkillEntity> visibleSkills) {
        List<ResolvedSkillInfo> result = new ArrayList<>();
        for (String skillName : skillNames) {
            AgentSkillEntity skill = visibleSkills.get(skillName);
            if (skill == null) {
                continue;
            }
            result.add(ResolvedSkillInfo.builder()
                    .name(skill.getName())
                    .displayName(skill.getDisplayName())
                    .description(skill.getDescription())
                    .scope(skill.getScope())
                    .workspaceId(skill.getWorkspaceId())
                    .version(skill.getVersion())
                    .enabled(skill.getEnabled())
                    .toolIds(copyList(skill.getGroupedToolIds()))
                    .build());
        }
        return result;
    }

    private List<String> collectToolIds(List<ResolvedSkillInfo> resolvedSkills) {
        Set<String> toolIds = new LinkedHashSet<>();
        for (ResolvedSkillInfo skill : resolvedSkills) {
            if (skill.getToolIds() == null) {
                continue;
            }
            for (String toolId : skill.getToolIds()) {
                toolRegistry.getProjectTool(toolId)
                        .map(ToolInfo::getToolId)
                        .ifPresent(toolIds::add);
            }
        }
        return new ArrayList<>(toolIds);
    }

    private List<String> resolveSubAgentTypes(AgentConfigEntity config) {
        if (!Boolean.TRUE.equals(config.getIsCoordinator())) {
            return List.of();
        }
        if (config.getSubAgentTypes() != null && !config.getSubAgentTypes().isEmpty()) {
            return config.getSubAgentTypes();
        }
        return List.of("UNIVERSAL");
    }

    private List<String> copyList(List<String> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(source);
    }
}
