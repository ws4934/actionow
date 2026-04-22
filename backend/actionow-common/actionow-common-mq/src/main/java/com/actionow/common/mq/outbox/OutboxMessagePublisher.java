package com.actionow.common.mq.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Outbox 消息轮询投递器
 * <p>
 * 定期扫描 t_outbox_message 表中的 PENDING 消息，投递到 RabbitMQ。
 * <p>
 * <b>关键设计：</b>
 * <ul>
 *   <li>使用 SELECT ... FOR UPDATE SKIP LOCKED 支持多实例并发</li>
 *   <li>每条消息独立处理，失败不影响后续消息</li>
 *   <li>指数退避重试（5s → 10s → 20s → 40s → ...，上限 1h）</li>
 *   <li>达到最大重试次数后标记为 FAILED（需人工干预）</li>
 *   <li>直接发送 JSON 字节，不经过 MessageConverter 二次序列化</li>
 * </ul>
 *
 * @author Actionow
 */
public class OutboxMessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxMessagePublisher.class);

    private final OutboxMessageRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;

    /** 每次扫描的批量大小 */
    private static final int BATCH_SIZE = 50;

    /** 已发送消息的保留时长（小时） */
    private static final int SENT_RETENTION_HOURS = 24;

    /** 重试基础间隔（秒） */
    private static final long RETRY_BASE_INTERVAL_SECONDS = 5;

    /** 重试最大间隔（秒） */
    private static final long RETRY_MAX_INTERVAL_SECONDS = 3600;

    /** 空闲计数器（连续无消息时降低日志频率） */
    private final AtomicInteger idleCount = new AtomicInteger(0);

    public OutboxMessagePublisher(OutboxMessageRepository outboxRepository,
                                   RabbitTemplate rabbitTemplate) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 定期扫描并发送 outbox 消息
     * <p>
     * 默认每 2 秒执行一次，保证消息的近实时投递。
     * 使用 fixedDelay 确保上一次执行完成后才开始下一次。
     */
    @Scheduled(fixedDelayString = "${actionow.outbox.poll-interval-ms:2000}")
    public void pollAndPublish() {
        try {
            List<OutboxMessage> messages = outboxRepository.findPendingMessages(BATCH_SIZE);

            if (messages.isEmpty()) {
                int idle = idleCount.incrementAndGet();
                if (idle == 1 || idle % 150 == 0) { // 首次和每5分钟打一次日志
                    log.debug("Outbox 轮询: 无待发送消息 (idleCount={})", idle);
                }
                return;
            }

            idleCount.set(0);
            log.info("Outbox 轮询: 发现 {} 条待发送消息", messages.size());

            int successCount = 0;
            int failCount = 0;

            for (OutboxMessage msg : messages) {
                try {
                    publishSingleMessage(msg);
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    handlePublishFailure(msg, e);
                }
            }

            if (failCount > 0) {
                log.warn("Outbox 批次完成: 成功={}, 失败={}", successCount, failCount);
            } else {
                log.info("Outbox 批次完成: 全部成功 ({}条)", successCount);
            }

        } catch (Exception e) {
            log.error("Outbox 轮询异常", e);
        }
    }

    /**
     * 发送单条消息到 RabbitMQ
     * <p>
     * 直接发送存储的 JSON 字节作为 AMQP Message，不经过 MessageConverter 二次序列化。
     * 这样保证消费端收到的 JSON 格式与 MessageProducer 直接发送时完全一致。
     */
    @Transactional(rollbackFor = Exception.class)
    public void publishSingleMessage(OutboxMessage msg) {
        try {
            // 构建 AMQP Message，直接使用存储的 JSON 字节
            MessageProperties props = new MessageProperties();
            props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            props.setContentEncoding(StandardCharsets.UTF_8.name());

            byte[] body = msg.getMessageJson().getBytes(StandardCharsets.UTF_8);
            Message amqpMessage = new Message(body, props);

            rabbitTemplate.send(msg.getExchange(), msg.getRoutingKey(), amqpMessage);
            outboxRepository.markSent(msg.getId());

            log.debug("Outbox 消息发送成功: id={}, type={}", msg.getId(), msg.getMessageType());
        } catch (Exception e) {
            throw new RuntimeException("Outbox 消息发送失败: id=" + msg.getId(), e);
        }
    }

    /**
     * 处理发送失败：指数退避重试
     */
    private void handlePublishFailure(OutboxMessage msg, Exception e) {
        String errorMsg = e.getMessage();
        if (errorMsg != null && errorMsg.length() > 500) {
            errorMsg = errorMsg.substring(0, 500);
        }

        // 指数退避: 5s, 10s, 20s, 40s, 80s, ... 最大 1h
        long delaySeconds = Math.min(
                RETRY_BASE_INTERVAL_SECONDS * (1L << msg.getRetryCount()),
                RETRY_MAX_INTERVAL_SECONDS
        );
        LocalDateTime nextRetryAt = LocalDateTime.now().plusSeconds(delaySeconds);

        try {
            outboxRepository.markRetryFailed(msg.getId(), errorMsg, nextRetryAt);
        } catch (Exception repoEx) {
            log.error("更新 outbox 消息失败状态异常: msgId={}", msg.getId(), repoEx);
        }

        if (msg.getRetryCount() + 1 >= msg.getMaxRetries()) {
            log.error("Outbox 消息重试耗尽: id={}, type={}, lastError={}",
                    msg.getId(), msg.getMessageType(), errorMsg);
        } else {
            log.warn("Outbox 消息发送失败，将在 {}s 后重试: id={}, retry={}/{}, error={}",
                    delaySeconds, msg.getId(), msg.getRetryCount() + 1, msg.getMaxRetries(), errorMsg);
        }
    }

    /**
     * 定期清理已发送的旧消息（每小时执行一次）
     */
    @Scheduled(fixedDelayString = "${actionow.outbox.cleanup-interval-ms:3600000}")
    public void cleanupOldMessages() {
        try {
            int deleted = outboxRepository.cleanupSentMessages(SENT_RETENTION_HOURS);
            if (deleted > 0) {
                log.info("Outbox 清理: 删除 {} 条已发送的旧消息（保留 {}h）", deleted, SENT_RETENTION_HOURS);
            }
        } catch (Exception e) {
            log.warn("Outbox 清理异常", e);
        }
    }

    /**
     * 定期统计日志（每 5 分钟）
     */
    @Scheduled(fixedDelayString = "${actionow.outbox.stats-interval-ms:300000}")
    public void logStats() {
        try {
            int pending = outboxRepository.countByStatus(OutboxMessage.STATUS_PENDING);
            int failed = outboxRepository.countByStatus(OutboxMessage.STATUS_FAILED);

            if (pending > 0 || failed > 0) {
                log.info("Outbox 统计: pending={}, failed={}", pending, failed);
            }

            if (failed > 0) {
                log.warn("存在 {} 条 FAILED 状态的 outbox 消息，需要人工排查", failed);
            }
        } catch (Exception e) {
            log.debug("Outbox 统计查询异常", e);
        }
    }
}
