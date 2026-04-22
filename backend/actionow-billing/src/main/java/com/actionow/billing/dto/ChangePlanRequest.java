package com.actionow.billing.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 变更订阅计划请求
 */
@Data
public class ChangePlanRequest {

    /** 可选，未传则使用用户上下文中的 workspaceId */
    private String workspaceId;

    @NotBlank(message = "目标计划不能为空")
    private String targetPlan;

    @NotBlank(message = "支付渠道不能为空")
    private String provider;

    /** MONTHLY / YEARLY */
    private String billingCycle = "MONTHLY";

    /** IMMEDIATE / NEXT_PERIOD */
    private String effectiveMode = "NEXT_PERIOD";
}
