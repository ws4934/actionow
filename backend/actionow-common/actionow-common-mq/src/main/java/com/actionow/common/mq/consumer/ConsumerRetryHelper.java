package com.actionow.common.mq.consumer;

import com.actionow.common.mq.message.MessageWrapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 消费者重试助手
 * <p>
 * 解决 basicNack(requeue=true) 不递增 MessageWrapper.retryCount 导致
 * 重试上限永远不触发的问题。
 * <p>
 * 原理：不再使用 basicNack(requeue=true)，而是将 retryCount+1 后重新发布消息到
 * 同一队列，然后 ACK 原消息。当达到重试上限时 basicNack(requeue=false) 路由至 DLQ。
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConsumerRetryHelper {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 重试或转入死信队列
     *
     * @param message     消息包装体
     * @param channel     AMQP channel
     * @param deliveryTag 投递标签
     * @param maxRetries  最大重试次数
     * @param exchange    重新发布的目标 exchange
     * @param routingKey  重新发布的目标 routing key
     * @return true=已重试, false=已转入 DLQ
     */
    public boolean retryOrDlq(MessageWrapper<?> message, Channel channel, long deliveryTag,
                               int maxRetries, String exchange, String routingKey) throws IOException {
        int retryCount = message.getRetryCount() == null ? 0 : message.getRetryCount();

        if (retryCount >= maxRetries) {
            log.error("消息重试超限，转入死信队列: messageId={}, type={}, retryCount={}/{}",
                    message.getMessageId(), message.getMessageType(), retryCount, maxRetries);
            channel.basicNack(deliveryTag, false, false);
            return false;
        }

        message.incrementRetry();
        log.warn("消息重试: messageId={}, type={}, retryCount={}/{}",
                message.getMessageId(), message.getMessageType(), message.getRetryCount(), maxRetries);
        rabbitTemplate.convertAndSend(exchange, routingKey, message);
        channel.basicAck(deliveryTag, false);
        return true;
    }
}
