package com.actionow.common.mq.outbox;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Outbox 消息实体
 * <p>
 * Transactional Outbox 模式的核心实体：消息先写入数据库 outbox 表（与业务操作同一事务），
 * 再由后台轮询器异步投递到 MQ，保证"业务操作"与"消息发送"的原子性。
 *
 * @author Actionow
 */
@Data
public class OutboxMessage {

    /** 消息ID（UUIDv7，与 MessageWrapper.messageId 一致） */
    private String id;

    /** RabbitMQ Exchange */
    private String exchange;

    /** RabbitMQ Routing Key */
    private String routingKey;

    /** 消息类型（如 TASK_CREATED, TASK_STATUS_CHANGED） */
    private String messageType;

    /** 完整的 MessageWrapper JSON（包含 payload、traceId、workspaceId 等） */
    private String messageJson;

    /** 消息状态: PENDING → SENT / FAILED */
    private String status;

    /** 发送重试次数 */
    private int retryCount;

    /** 最大重试次数 */
    private int maxRetries;

    /** 最后一次错误信息 */
    private String lastError;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 发送成功时间 */
    private LocalDateTime sentAt;

    /** 下次重试时间 */
    private LocalDateTime nextRetryAt;

    // ==================== 状态常量 ====================

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";

    /** 默认最大重试次数 */
    public static final int DEFAULT_MAX_RETRIES = 10;
}
