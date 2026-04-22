package com.actionow.agent.tool.service.impl;

import com.actionow.agent.entity.AgentSkillEntity;
import com.actionow.agent.mapper.AgentSkillMapper;
import com.actionow.agent.registry.DatabaseSkillRegistry;
import com.actionow.agent.tool.dto.ToolInfo;
import com.actionow.agent.tool.registry.ProjectToolRegistry;
import com.actionow.agent.tool.service.ToolCatalogService;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Tool Catalog 查询服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolCatalogServiceImpl implements ToolCatalogService {

    private final ProjectToolRegistry toolRegistry;
    private final AgentSkillMapper skillMapper;
    private final DatabaseSkillRegistry skillRegistry;

    @Override
    public PageResult<ToolInfo> findPage(Long current, Long size, String keyword, String actionType, String tag, String workspaceId) {
        long safeCurrent = current != null && current > 0 ? current : 1L;
        long safeSize = size != null && size > 0 ? size : 20L;

        Map<String, Set<String>> skillNamesByToolId = buildSkillNamesByToolId(workspaceId);
        List<ToolInfo> filtered = toolRegistry.getAllProjectTools().stream()
                .map(tool -> attachSkillNames(toolRegistry.getProjectTool(tool.getToolId()).orElse(tool), skillNamesByToolId))
                .filter(tool -> matchesKeyword(tool, keyword))
                .filter(tool -> matchesActionType(tool, actionType))
                .filter(tool -> matchesTag(tool, tag))
                .sorted(Comparator.comparing(ToolInfo::getToolId, String.CASE_INSENSITIVE_ORDER))
                .toList();

        int fromIndex = Math.toIntExact(Math.min((safeCurrent - 1) * safeSize, filtered.size()));
        int toIndex = Math.toIntExact(Math.min(fromIndex + safeSize, filtered.size()));
        return PageResult.of(safeCurrent, safeSize, (long) filtered.size(), filtered.subList(fromIndex, toIndex));
    }

    @Override
    public ToolInfo getTool(String toolId, String workspaceId) {
        ToolInfo tool = toolRegistry.getProjectTool(toolId)
                .orElseThrow(() -> new BusinessException("工具不存在或未注册: " + toolId));
        return attachSkillNames(tool, buildSkillNamesByToolId(workspaceId));
    }

    @Override
    public List<ToolInfo> getToolsForSkill(String skillName, String workspaceId) {
        AgentSkillEntity skill = skillMapper.selectVisibleByName(skillName, workspaceId);
        if (skill == null || skill.getDeleted() != 0) {
            throw new BusinessException("当前工作空间不可见 Skill: " + skillName);
        }
        Map<String, Set<String>> skillNamesByToolId = buildSkillNamesByToolId(workspaceId);
        return toolRegistry.getProjectTools(skill.getGroupedToolIds()).stream()
                .map(tool -> attachSkillNames(tool, skillNamesByToolId))
                .toList();
    }

    private boolean matchesKeyword(ToolInfo tool, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.toLowerCase(Locale.ROOT);
        return contains(tool.getToolId(), normalized)
                || contains(tool.getToolName(), normalized)
                || contains(tool.getDisplayName(), normalized)
                || contains(tool.getDescription(), normalized)
                || contains(tool.getSummary(), normalized)
                || contains(tool.getPurpose(), normalized)
                || containsAny(tool.getTags(), normalized)
                || containsAny(tool.getSkillNames(), normalized);
    }

    private boolean matchesActionType(ToolInfo tool, String actionType) {
        if (actionType == null || actionType.isBlank()) {
            return true;
        }
        return actionType.equalsIgnoreCase(tool.getActionType());
    }

    private boolean matchesTag(ToolInfo tool, String tag) {
        if (tag == null || tag.isBlank()) {
            return true;
        }
        return tool.getTags() != null && tool.getTags().stream().anyMatch(value -> tag.equalsIgnoreCase(value));
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private boolean containsAny(List<String> values, String keyword) {
        return values != null && values.stream().anyMatch(value -> contains(value, keyword));
    }

    private ToolInfo attachSkillNames(ToolInfo tool, Map<String, Set<String>> skillNamesByToolId) {
        if (tool == null) {
            return null;
        }
        List<String> skillNames = new ArrayList<>(skillNamesByToolId.getOrDefault(tool.getToolId(), Set.of()));
        tool.setSkillNames(skillNames);
        return tool;
    }

    private Map<String, Set<String>> buildSkillNamesByToolId(String workspaceId) {
        Map<String, Set<String>> skillNamesByToolId = new LinkedHashMap<>();

        Map<String, AgentSkillEntity> visibleSkills = skillRegistry.getCacheSnapshotForWorkspace(workspaceId);
        for (AgentSkillEntity skill : visibleSkills.values()) {
            if (skill.getGroupedToolIds() == null || skill.getGroupedToolIds().isEmpty()) {
                continue;
            }

            for (String configuredToolId : skill.getGroupedToolIds()) {
                String normalizedToolId = toolRegistry.getProjectTool(configuredToolId)
                        .map(ToolInfo::getToolId)
                        .orElse(configuredToolId);

                skillNamesByToolId
                        .computeIfAbsent(normalizedToolId, key -> new LinkedHashSet<>())
                        .add(skill.getName());
            }
        }

        return skillNamesByToolId;
    }
}
