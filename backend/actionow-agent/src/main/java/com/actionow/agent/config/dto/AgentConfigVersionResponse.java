package com.actionow.agent.config.dto;

import com.actionow.agent.config.entity.AgentConfigVersion;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 配置版本响应 DTO
 *
 * @author Actionow
 */
@Data
public class AgentConfigVersionResponse {

    private String id;

    /**
     * 关联的 AgentConfig ID
     */
    private String agentConfigId;

    /**
     * 版本号
     */
    private Integer versionNumber;

    /**
     * 提示词内容快照
     */
    private String promptContent;

    /**
     * 包含列表快照
     */
    private List<String> includes;

    /**
     * LLM Provider ID 快照
     */
    private String llmProviderId;

    /**
     * 默认 Skill 列表快照
     */
    private List<String> defaultSkillNames;

    /**
     * 允许 Skill 白名单快照
     */
    private List<String> allowedSkillNames;

    /**
     * Skill 加载模式快照
     */
    private String skillLoadMode;

    /**
     * 执行模式快照
     */
    private String executionMode;

    /**
     * 是否协调者快照
     */
    private Boolean isCoordinator;

    /**
     * 子 Agent 快照
     */
    private List<String> subAgentTypes;

    /**
     * 是否支持独立调用快照
     */
    private Boolean standaloneEnabled;

    /**
     * 变更说明
     */
    private String changeSummary;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 从实体转换
     */
    public static AgentConfigVersionResponse fromEntity(AgentConfigVersion entity) {
        if (entity == null) {
            return null;
        }
        AgentConfigVersionResponse response = new AgentConfigVersionResponse();
        response.setId(entity.getId());
        response.setAgentConfigId(entity.getAgentConfigId());
        response.setVersionNumber(entity.getVersionNumber());
        response.setPromptContent(entity.getPromptContent());
        response.setIncludes(entity.getIncludes());
        response.setLlmProviderId(entity.getLlmProviderId());
        response.setDefaultSkillNames(entity.getDefaultSkillNames());
        response.setAllowedSkillNames(entity.getAllowedSkillNames());
        response.setSkillLoadMode(entity.getSkillLoadMode());
        response.setExecutionMode(entity.getExecutionMode());
        response.setIsCoordinator(entity.getIsCoordinator());
        response.setSubAgentTypes(entity.getSubAgentTypes());
        response.setStandaloneEnabled(entity.getStandaloneEnabled());
        response.setChangeSummary(entity.getChangeSummary());
        response.setCreatedAt(entity.getCreatedAt());
        return response;
    }
}
