package com.actionow.billing.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * Stripe 套餐价格创建/更新请求
 */
@Data
public class UpsertStripePlanRequest {

    @NotBlank(message = "计划编码不能为空")
    private String planCode;

    /** Free/Basic/Pro/Enterprise */
    private String workspacePlanType;

    /** MONTHLY / YEARLY */
    private String billingCycle = "MONTHLY";

    private String currency = "USD";

    @NotNull(message = "金额不能为空")
    @Min(value = 0, message = "金额不能为负数")
    private Long amountMinor;

    /** 未传 stripePriceId 时，是否自动创建 Stripe Product/Price */
    private Boolean autoCreateStripeResources = Boolean.TRUE;

    /** 可选：已存在 Stripe Product ID */
    private String stripeProductId;

    /** 可选：已存在 Stripe Price ID */
    private String stripePriceId;

    /** 是否按量计费（metered） */
    private Boolean metered = Boolean.FALSE;

    /** LICENSED / METERED */
    private String usageType;

    private String displayName;

    private String nickname;

    private Boolean active = Boolean.TRUE;

    private Map<String, Object> meta;
}

