package com.actionow.agent.billing.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 结算计费会话请求
 *
 * @author Actionow
 */
@Data
public class SettleBillingRequest {

    /**
     * 计费会话ID
     */
    @NotBlank(message = "计费会话ID不能为空")
    private String billingSessionId;

    /**
     * 工作空间ID
     */
    @NotBlank(message = "工作空间ID不能为空")
    private String workspaceId;

    /**
     * 操作人ID
     */
    @NotBlank(message = "操作人ID不能为空")
    private String operatorId;

    /**
     * 是否强制结算（即使会话仍为活跃状态）
     */
    private Boolean forceSettle;
}
