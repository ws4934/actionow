package com.actionow.agent.tool.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Agent 工具访问权限请求 DTO
 *
 * @author Actionow
 */
@Data
public class AgentToolAccessRequest {

    /**
     * Agent 类型
     */
    @NotBlank(message = "Agent 类型不能为空")
    private String agentType;

    /**
     * 工具分类：PROJECT | AI
     */
    @NotBlank(message = "工具分类不能为空")
    private String toolCategory;

    /**
     * 工具标识
     */
    @NotBlank(message = "工具 ID 不能为空")
    private String toolId;

    /**
     * 工具显示名称
     */
    private String toolName;

    /**
     * 工具描述
     */
    private String toolDescription;

    /**
     * 访问模式：FULL | READONLY | DISABLED
     */
    private String accessMode;

    /**
     * 每日调用限额
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
