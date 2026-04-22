package com.actionow.common.mq.producer;

import com.actionow.common.mq.message.MessageWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 消息生产者
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送消息到指定交换机
     */
    public <T> void send(String exchange, String routingKey, MessageWrapper<T> message) {
        log.debug("发送消息: exchange={}, routingKey={}, messageId={}, type={}",
                exchange, routingKey, message.getMessageId(), message.getMessageType());
        rabbitTemplate.convertAndSend(exchange, routingKey, message);
    }

    /**
     * 发送直接交换机消息
     */
    public <T> void sendDirect(String routingKey, MessageWrapper<T> message) {
        send(com.actionow.common.mq.constant.MqConstants.EXCHANGE_DIRECT, routingKey, message);
    }

    /**
     * 发送 Topic 消息
     */
    public <T> void sendTopic(String routingKey, MessageWrapper<T> message) {
        send(com.actionow.common.mq.constant.MqConstants.EXCHANGE_TOPIC, routingKey, message);
    }

    /**
     * 发送消息（便捷方法）
     */
    public <T> void send(String exchange, String routingKey, String messageType, T payload) {
        MessageWrapper<T> message = MessageWrapper.wrap(messageType, payload);
        send(exchange, routingKey, message);
    }

    /**
     * 发送直接交换机消息（便捷方法）
     */
    public <T> void sendDirect(String routingKey, String messageType, T payload) {
        MessageWrapper<T> message = MessageWrapper.wrap(messageType, payload);
        sendDirect(routingKey, message);
    }
}
