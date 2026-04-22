package com.actionow.agent.config.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * Agent 配置请求 DTO
 *
 * @author Actionow
 */
@Data
public class AgentConfigRequest {

    /**
     * Agent 类型
     */
    @NotBlank(message = "Agent 类型不能为空")
    private String agentType;

    /**
     * Agent 显示名称
     */
    @NotBlank(message = "Agent 名称不能为空")
    private String agentName;

    /**
     * 关联的 LLM Provider ID
     */
    private String llmProviderId;

    /**
     * 提示词内容
     */
    @NotBlank(message = "提示词内容不能为空")
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
     * 变更说明（用于版本记录）
     */
    private String changeSummary;

    // ==================== 自定义 Agent 扩展字段 ====================

    /**
     * 是否为协调者 Agent
     * 协调者可以将任务委派给子 Agent
     */
    private Boolean isCoordinator;

    /**
     * 子 Agent 类型列表
     * 当 isCoordinator=true 时，指定可以委派任务的子 Agent
     */
    private List<String> subAgentTypes;

    /**
     * Agent 作用域
     * SYSTEM: 系统级，所有工作空间可用
     * WORKSPACE: 工作空间级，仅指定工作空间可用
     * USER: 用户级，仅创建者可用
     */
    private String scope;

    /**
     * 所属工作空间 ID（scope=WORKSPACE 时必填）
     */
    private String workspaceId;

    /**
     * 是否支持独立调用
     * true: 可以直接调用此 Agent，无需通过协调者
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
}
