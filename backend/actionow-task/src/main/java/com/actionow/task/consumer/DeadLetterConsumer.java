package com.actionow.task.consumer;

import com.actionow.common.mq.constant.MqConstants;
import com.actionow.common.mq.message.MessageWrapper;
import com.actionow.common.mq.producer.MessageProducer;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 死信队列消费者
 * 处理无法正常消费的消息，记录日志并发送告警
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "actionow.task.compensation.enabled", havingValue = "true", matchIfMissing = true)
public class DeadLetterConsumer {

    private final MessageProducer messageProducer;

    /**
     * 消费死信队列消息
     */
    @RabbitListener(queues = MqConstants.QUEUE_DEAD_LETTER)
    public void handleDeadLetter(Message message, Channel channel) {
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            String routingKey = message.getMessageProperties().getReceivedRoutingKey();
            String exchange = message.getMessageProperties().getReceivedExchange();
            String messageId = message.getMessageProperties().getMessageId();

            log.error("收到死信消息: messageId={}, exchange={}, routingKey={}, body={}",
                    messageId, exchange, routingKey, body);

            // 记录死信消息详情
            logDeadLetterDetails(message);

            // 发送系统告警通知
            sendDeadLetterAlert(messageId, exchange, routingKey, body);

            // 确认消息（死信消息不再重试）
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);

        } catch (Exception e) {
            log.error("处理死信消息异常: {}", e.getMessage(), e);
            try {
                // 处理异常也要确认，避免死循环
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            } catch (Exception ackEx) {
                log.error("确认死信消息失败: {}", ackEx.getMessage());
            }
        }
    }

    /**
     * 记录死信消息详情
     */
    private void logDeadLetterDetails(Message message) {
        var props = message.getMessageProperties();

        log.error("死信消息详情: " +
                        "messageId={}, " +
                        "correlationId={}, " +
                        "originalExchange={}, " +
                        "originalRoutingKey={}, " +
                        "redelivered={}, " +
                        "headers={}",
                props.getMessageId(),
                props.getCorrelationId(),
                props.getHeader("x-first-death-exchange"),
                props.getHeader("x-first-death-queue"),
                props.getRedelivered(),
                props.getHeaders());
    }

    /**
     * 发送死信消息告警
     */
    private void sendDeadLetterAlert(String messageId, String exchange, String routingKey, String body) {
        try {
            Map<String, Object> alertPayload = new HashMap<>();
            alertPayload.put("alertType", "DEAD_LETTER_RECEIVED");
            alertPayload.put("alertLevel", "WARN");
            alertPayload.put("source", "actionow-task");
            alertPayload.put("messageId", messageId);
            alertPayload.put("originalExchange", exchange);
            alertPayload.put("originalRoutingKey", routingKey);
            alertPayload.put("message", String.format(
                    "死信消息: messageId=%s, exchange=%s, routingKey=%s",
                    messageId, exchange, routingKey));
            alertPayload.put("timestamp", LocalDateTime.now().toString());

            MessageWrapper<Map<String, Object>> message = MessageWrapper.wrap(
                    MqConstants.Alert.MSG_TYPE, alertPayload);
            messageProducer.send(MqConstants.EXCHANGE_DIRECT, MqConstants.Alert.ROUTING, message);
        } catch (Exception e) {
            log.error("发送死信告警失败: messageId={}", messageId, e);
        }
    }
}
