package com.actionow.agent.config.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.actionow.common.data.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Agent 配置版本历史实体
 * 记录 Agent 配置的变更历史，支持回滚
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_agent_config_version", autoResultMap = true)
public class AgentConfigVersion extends BaseEntity {

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
    @TableField(value = "\"includes\"", typeHandler = JacksonTypeHandler.class)
    private List<String> includes;

    /**
     * LLM Provider ID 快照
     */
    private String llmProviderId;

    /**
     * 默认 Skill 列表快照
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> defaultSkillNames;

    /**
     * 允许 Skill 白名单快照
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
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
     * 协调者标志快照
     */
    private Boolean isCoordinator;

    /**
     * 子 Agent 类型快照
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> subAgentTypes;

    /**
     * 独立调用标志快照
     */
    private Boolean standaloneEnabled;

    /**
     * 变更说明
     */
    private String changeSummary;
}
