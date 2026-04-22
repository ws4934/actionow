package com.actionow.common.mq.message;

import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.id.UuidGenerator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 消息包装类
 *
 * @param <T> 消息体类型
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageWrapper<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息ID
     */
    private String messageId;

    /**
     * 消息类型
     */
    private String messageType;

    /**
     * 消息体
     */
    private T payload;

    /**
     * 发送时间
     */
    private LocalDateTime sendTime;

    /**
     * 发送者用户ID
     */
    private String senderId;

    /**
     * 工作空间ID
     */
    private String workspaceId;

    /**
     * 租户Schema
     */
    private String tenantSchema;

    /**
     * 请求ID（用于链路追踪）
     */
    private String traceId;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 创建消息包装
     */
    public static <T> MessageWrapper<T> wrap(String messageType, T payload) {
        return MessageWrapper.<T>builder()
                .messageId(UuidGenerator.generateShortId())
                .messageType(messageType)
                .payload(payload)
                .sendTime(LocalDateTime.now())
                .senderId(UserContextHolder.getUserId())
                .workspaceId(UserContextHolder.getWorkspaceId())
                .tenantSchema(UserContextHolder.getTenantSchema())
                .traceId(UserContextHolder.getRequestId())
                .retryCount(0)
                .build();
    }

    /**
     * 增加重试次数
     */
    public MessageWrapper<T> incrementRetry() {
        this.retryCount = (this.retryCount == null ? 0 : this.retryCount) + 1;
        return this;
    }
}
