package com.actionow.common.mq.outbox;

import com.actionow.common.core.id.UuidGenerator;
import com.actionow.common.mq.message.MessageWrapper;
import com.actionow.common.mq.producer.MessageProducer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * 事务性消息生产者（Transactional Outbox 模式）
 * <p>
 * 替代直接调用 {@link MessageProducer#send}，将消息先写入 outbox 表（与业务操作同一事务），
 * 由 {@link OutboxMessagePublisher} 异步轮询投递到 RabbitMQ。
 * <p>
 * <b>使用方式：</b>在 @Transactional 方法中调用 {@link #sendInTransaction} 代替 {@code messageProducer.send()}
 *
 * <pre>
 * // Before (直接发送，非原子)：
 * taskMapper.insert(task);
 * messageProducer.send(exchange, routingKey, message); // 可能失败但事务已提交
 *
 * // After (事务性发送，原子)：
 * taskMapper.insert(task);
 * transactionalMessageProducer.sendInTransaction(exchange, routingKey, message); // 同事务写 outbox
 * </pre>
 *
 * @author Actionow
 */
public class TransactionalMessageProducer {

    private static final Logger log = LoggerFactory.getLogger(TransactionalMessageProducer.class);

    private final OutboxMessageRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public TransactionalMessageProducer(OutboxMessageRepository outboxRepository,
                                         ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 事务性发送消息
     * <p>
     * 将消息写入 outbox 表，必须在 @Transactional 上下文中调用。
     * 如果业务事务回滚，outbox 消息也会一同回滚，保证原子性。
     *
     * @param exchange   RabbitMQ Exchange
     * @param routingKey RabbitMQ Routing Key
     * @param message    消息包装体
     * @param <T>        消息体类型
     */
    public <T> void sendInTransaction(String exchange, String routingKey, MessageWrapper<T> message) {
        String messageJson;
        try {
            messageJson = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            log.error("序列化 outbox 消息失败: messageId={}, type={}", message.getMessageId(), message.getMessageType(), e);
            throw new RuntimeException("Outbox 消息序列化失败", e);
        }

        OutboxMessage outbox = new OutboxMessage();
        outbox.setId(message.getMessageId() != null ? message.getMessageId() : UuidGenerator.generateShortId());
        outbox.setExchange(exchange);
        outbox.setRoutingKey(routingKey);
        outbox.setMessageType(message.getMessageType());
        outbox.setMessageJson(messageJson);
        outbox.setStatus(OutboxMessage.STATUS_PENDING);
        outbox.setRetryCount(0);
        outbox.setMaxRetries(OutboxMessage.DEFAULT_MAX_RETRIES);
        outbox.setCreatedAt(LocalDateTime.now());

        outboxRepository.insert(outbox);

        log.debug("Outbox 消息已写入: id={}, type={}, exchange={}, routingKey={}",
                outbox.getId(), outbox.getMessageType(), exchange, routingKey);
    }

    /**
     * 事务性发送消息（便捷方法：自动包装 MessageWrapper）
     */
    public <T> void sendInTransaction(String exchange, String routingKey, String messageType, T payload) {
        MessageWrapper<T> message = MessageWrapper.wrap(messageType, payload);
        sendInTransaction(exchange, routingKey, message);
    }

    /**
     * 事务性发送直接交换机消息
     */
    public <T> void sendDirectInTransaction(String routingKey, MessageWrapper<T> message) {
        sendInTransaction(com.actionow.common.mq.constant.MqConstants.EXCHANGE_DIRECT, routingKey, message);
    }

    /**
     * 事务性发送直接交换机消息（便捷方法）
     */
    public <T> void sendDirectInTransaction(String routingKey, String messageType, T payload) {
        MessageWrapper<T> message = MessageWrapper.wrap(messageType, payload);
        sendDirectInTransaction(routingKey, message);
    }
}
