package com.actionow.billing.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 支付订单实体
 */
@Data
@TableName(value = "public.t_payment_order", autoResultMap = true)
public class PaymentOrder implements Serializable {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    @TableField("order_no")
    private String orderNo;

    @TableField("workspace_id")
    private String workspaceId;

    @TableField("user_id")
    private String userId;

    private String provider;

    @TableField("order_type")
    private String orderType;

    @TableField("biz_type")
    private String bizType;

    @TableField("amount_minor")
    private Long amountMinor;

    private String currency;

    @TableField("points_amount")
    private Long pointsAmount;

    private String status;

    @TableField("provider_payment_id")
    private String providerPaymentId;

    @TableField("provider_session_id")
    private String providerSessionId;

    @TableField("provider_invoice_id")
    private String providerInvoiceId;

    @TableField("fail_code")
    private String failCode;

    @TableField("fail_message")
    private String failMessage;

    @TableField("paid_at")
    private LocalDateTime paidAt;

    @TableField("expired_at")
    private LocalDateTime expiredAt;

    @TableField(value = "meta", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> meta;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @Version
    private Integer version;
}
