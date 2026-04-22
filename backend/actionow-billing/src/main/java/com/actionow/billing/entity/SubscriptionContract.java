package com.actionow.billing.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 订阅合约实体
 */
@Data
@TableName(value = "public.t_subscription_contract", autoResultMap = true)
public class SubscriptionContract implements Serializable {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    @TableField("workspace_id")
    private String workspaceId;

    @TableField("user_id")
    private String userId;

    private String provider;

    @TableField("provider_customer_id")
    private String providerCustomerId;

    @TableField("provider_subscription_id")
    private String providerSubscriptionId;

    @TableField("plan_code")
    private String planCode;

    @TableField("billing_cycle")
    private String billingCycle;

    @TableField("amount_minor")
    private Long amountMinor;

    private String currency;

    private String status;

    @TableField("current_period_start")
    private LocalDateTime currentPeriodStart;

    @TableField("current_period_end")
    private LocalDateTime currentPeriodEnd;

    @TableField("trial_end")
    private LocalDateTime trialEnd;

    @TableField("cancel_at_period_end")
    private Boolean cancelAtPeriodEnd;

    @TableField("canceled_at")
    private LocalDateTime canceledAt;

    @TableField("grace_period_end")
    private LocalDateTime gracePeriodEnd;

    @TableField("last_invoice_id")
    private String lastInvoiceId;

    @TableField("last_paid_at")
    private LocalDateTime lastPaidAt;

    @TableField(value = "meta", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> meta;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @Version
    private Integer version;
}
