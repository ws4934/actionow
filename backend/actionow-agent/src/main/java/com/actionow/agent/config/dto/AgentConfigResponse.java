package com.actionow.agent.config.dto;

import com.actionow.agent.config.entity.AgentConfigEntity;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 配置响应 DTO
 *
 * @author Actionow
 */
@Data
public class AgentConfigResponse {

    private String id;

    /**
     * Agent 类型
     */
    private String agentType;

    /**
     * Agent 显示名称
     */
    private String agentName;

    /**
     * 关联的 LLM Provider ID
     */
    private String llmProviderId;

    /**
     * 提示词内容
     */
    private String promptContent;

    /**
     * 包含的其他 prompt_key 列表
     */
    private List<String> includes;

    /**
     * 默认 Skill 列表。
     */
    private List<String> defaultSkillNames;

    /**
     * 允许 Skill 白名单。
     */
    private List<String> allowedSkillNames;

    /**
     * Skill 加载模式。
     */
    private String skillLoadMode;

    /**
     * 执行模式。
     */
    private String executionMode;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 是否为系统配置
     */
    private Boolean isSystem;

    /**
     * 描述
     */
    private String description;

    /**
     * 当前版本号
     */
    private Integer currentVersion;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    // ==================== 自定义 Agent 扩展字段 ====================

    /**
     * 是否为协调者 Agent
     */
    private Boolean isCoordinator;

    /**
     * 子 Agent 类型列表
     */
    private List<String> subAgentTypes;

    /**
     * Agent 作用域 (SYSTEM, WORKSPACE, USER)
     */
    private String scope;

    /**
     * 所属工作空间 ID
     */
    private String workspaceId;

    /**
     * 创建者用户 ID
     */
    private String creatorId;

    /**
     * 是否支持独立调用
     */
    private Boolean standaloneEnabled;

    /**
     * Agent 图标 URL
     */
    private String iconUrl;

    /**
     * Agent 分类标签
     */
    private List<String> tags;

    /**
     * 从实体转换
     */
    public static AgentConfigResponse fromEntity(AgentConfigEntity entity) {
        if (entity == null) {
            return null;
        }
        AgentConfigResponse response = new AgentConfigResponse();
        response.setId(entity.getId());
        response.setAgentType(entity.getAgentType());
        response.setAgentName(entity.getAgentName());
        response.setLlmProviderId(entity.getLlmProviderId());
        response.setPromptContent(entity.getPromptContent());
        response.setIncludes(entity.getIncludes());
        response.setDefaultSkillNames(entity.getDefaultSkillNames());
        response.setAllowedSkillNames(entity.getAllowedSkillNames());
        response.setSkillLoadMode(entity.getSkillLoadMode());
        response.setExecutionMode(entity.getExecutionMode());
        response.setEnabled(entity.getEnabled());
        response.setIsSystem(entity.getIsSystem());
        response.setDescription(entity.getDescription());
        response.setCurrentVersion(entity.getCurrentVersion());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());

        // 自定义 Agent 扩展字段
        response.setIsCoordinator(entity.getIsCoordinator());
        response.setSubAgentTypes(entity.getSubAgentTypes());
        response.setScope(entity.getScope());
        response.setWorkspaceId(entity.getWorkspaceId());
        response.setCreatorId(entity.getCreatorId());
        response.setStandaloneEnabled(entity.getStandaloneEnabled());
        response.setIconUrl(entity.getIconUrl());
        response.setTags(entity.getTags());
        return response;
    }
}
