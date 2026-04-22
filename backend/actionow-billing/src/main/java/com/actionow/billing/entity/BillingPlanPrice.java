package com.actionow.billing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 订阅套餐价格目录
 */
@Data
@TableName(value = "public.t_billing_plan_price", autoResultMap = true)
public class BillingPlanPrice implements Serializable {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String provider;

    @TableField("plan_code")
    private String planCode;

    @TableField("workspace_plan_type")
    private String workspacePlanType;

    @TableField("billing_cycle")
    private String billingCycle;

    private String currency;

    @TableField("amount_minor")
    private Long amountMinor;

    @TableField("is_metered")
    private Boolean metered;

    @TableField("usage_type")
    private String usageType;

    @TableField("stripe_product_id")
    private String stripeProductId;

    @TableField("stripe_price_id")
    private String stripePriceId;

    private String status;

    @TableField(value = "meta", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> meta;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("created_by")
    private String createdBy;

    @TableField("updated_by")
    private String updatedBy;

    private Integer deleted;

    @Version
    private Integer version;
}

