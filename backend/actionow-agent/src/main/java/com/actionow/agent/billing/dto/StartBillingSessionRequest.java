package com.actionow.agent.billing.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 启动计费会话请求
 *
 * @author Actionow
 */
@Data
public class StartBillingSessionRequest {

    /**
     * 工作空间ID
     */
    @NotBlank(message = "工作空间ID不能为空")
    private String workspaceId;

    /**
     * 关联的 Agent 会话ID
     */
    @NotBlank(message = "会话ID不能为空")
    private String conversationId;

    /**
     * 用户ID
     */
    @NotBlank(message = "用户ID不能为空")
    private String userId;

    /**
     * Agent 类型
     */
    @NotBlank(message = "Agent类型不能为空")
    private String agentType;

    /**
     * 初始冻结金额（积分）
     */
    @NotNull(message = "冻结金额不能为空")
    @Min(value = 1, message = "冻结金额必须大于0")
    private Long frozenAmount;

    /**
     * 指定模型ID（可选，不传则使用默认配置）
     */
    private String modelId;
}
