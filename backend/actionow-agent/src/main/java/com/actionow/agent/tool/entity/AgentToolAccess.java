package com.actionow.agent.tool.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.actionow.common.data.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Agent 工具访问权限实体
 * 统一管理 PROJECT 和 AI 两类工具的访问权限
 * 支持多对多关系：同一工具可被多个 Agent 使用
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_agent_tool_access", autoResultMap = true)
public class AgentToolAccess extends BaseEntity {

    /**
     * Agent 类型
     * COORDINATOR, SCRIPT_EXPERT, EPISODE_EXPERT, STORYBOARD_EXPERT,
     * CHARACTER_EXPERT, SCENE_EXPERT, PROP_EXPERT, STYLE_EXPERT, MULTIMODAL_EXPERT
     */
    private String agentType;

    /**
     * 工具分类
     * PROJECT: 业务工具（剧本相关）
     * AI: 生成工具（AI 模型）
     */
    private String toolCategory;

    /**
     * 工具标识
     * PROJECT: 工具名称（如 write_script, create_character）
     * AI: model_provider_id（关联 actionow-ai 的 t_model_provider.id）
     */
    private String toolId;

    /**
     * 工具显示名称（可选覆盖）
     */
    private String toolName;

    /**
     * 工具描述（可选覆盖）
     */
    private String toolDescription;

    /**
     * 访问模式
     * FULL: 完全访问
     * READONLY: 只读
     * DISABLED: 禁用
     */
    private String accessMode;

    /**
     * 每日调用限额，-1 表示无限
     */
    private Integer dailyQuota;

    /**
     * 优先级
     */
    private Integer priority;

    /**
     * 是否启用
     */
    private Boolean enabled;
}
