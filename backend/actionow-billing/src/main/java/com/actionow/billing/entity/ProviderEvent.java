package com.actionow.billing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 支付渠道回调事件实体
 */
@Data
@TableName("public.t_provider_event")
public class ProviderEvent implements Serializable {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String provider;

    @TableField("event_id")
    private String eventId;

    @TableField("event_type")
    private String eventType;

    @TableField("resource_id")
    private String resourceId;

    @TableField("event_created_at")
    private LocalDateTime eventCreatedAt;

    @TableField("signature_verified")
    private Boolean signatureVerified;

    @TableField("process_status")
    private String processStatus;

    @TableField("process_result")
    private String processResult;

    @TableField("processed_at")
    private LocalDateTime processedAt;

    @TableField("payload_raw")
    private String payloadRaw;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
