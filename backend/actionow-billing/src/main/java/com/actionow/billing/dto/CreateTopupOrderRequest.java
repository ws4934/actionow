package com.actionow.billing.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建充值订单请求
 */
@Data
public class CreateTopupOrderRequest {

    /** 可选，未传则使用用户上下文中的 workspaceId */
    private String workspaceId;

    @NotNull(message = "充值金额不能为空")
    @Min(value = 1, message = "充值金额必须大于0")
    private Long amountMinor;

    @NotBlank(message = "币种不能为空")
    private String currency;

    @NotBlank(message = "支付渠道不能为空")
    private String provider;

    /** 可选：业务积分，不传默认 1:1 映射 amountMinor */
    private Long pointsAmount;

    private String paymentMethod;

    private String description;
}
