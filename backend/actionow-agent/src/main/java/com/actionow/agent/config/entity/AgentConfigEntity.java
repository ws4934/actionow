package com.actionow.agent.config.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.actionow.common.data.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Agent 配置实体
 * 整合提示词 + LLM 选择，一个 Agent 类型一条记录
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_agent_config", autoResultMap = true)
public class AgentConfigEntity extends BaseEntity {

    /**
     * Agent 类型（唯一）
     * COORDINATOR, SCRIPT_EXPERT, EPISODE_EXPERT, STORYBOARD_EXPERT,
     * CHARACTER_EXPERT, SCENE_EXPERT, PROP_EXPERT, STYLE_EXPERT, MULTIMODAL_EXPERT
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
    @TableField(value = "\"includes\"", typeHandler = JacksonTypeHandler.class)
    private List<String> includes;

    /**
     * 可用的 AI Provider 类型列表
     * 如 ["IMAGE", "VIDEO", "AUDIO", "TEXT"]
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> aiProviderTypes;

    /**
     * 默认 Skill 列表。
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> defaultSkillNames;

    /**
     * 允许加载的 Skill 白名单。
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
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
     * 是否为系统配置（不可删除）
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

    // ==================== 自定义 Agent 扩展字段 ====================

    /**
     * 是否为协调者 Agent
     * 协调者可以将任务委派给子 Agent
     */
    private Boolean isCoordinator;

    /**
     * 子 Agent 类型列表
     * 当 isCoordinator=true 时，指定可以委派任务的子 Agent
     * 如 ["SCRIPT_EXPERT", "CHARACTER_EXPERT", "my_custom_agent"]
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
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
     * 创建者用户 ID（scope=USER 时必填）
     */
    private String creatorId;

    /**
     * 是否支持独立调用
     * true: 可以直接调用此 Agent，无需通过协调者
     * false: 只能作为子 Agent 被协调者调用
     */
    private Boolean standaloneEnabled;

    /**
     * Agent 图标 URL
     */
    private String iconUrl;

    /**
     * Agent 分类标签
     * 如 ["创作", "优化", "分析"]
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;
}
