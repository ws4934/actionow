package com.actionow.wallet.config;

import com.actionow.common.mq.constant.MqConstants;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wallet 服务 RabbitMQ 队列/绑定配置
 * 基础设施（Exchange、MessageConverter、RabbitTemplate）由 actionow-common-mq 提供
 *
 * @author Actionow
 */
@Configuration
public class WalletMqConfig {

    @Bean
    public Queue walletQueue() {
        return QueueBuilder.durable(MqConstants.Wallet.QUEUE)
                .withArgument("x-dead-letter-exchange", MqConstants.EXCHANGE_DEAD_LETTER)
                .withArgument("x-dead-letter-routing-key", MqConstants.QUEUE_DEAD_LETTER)
                .build();
    }

    @Bean
    public Binding walletCreateCompensationBinding(Queue walletQueue, DirectExchange directExchange) {
        return BindingBuilder.bind(walletQueue).to(directExchange).with(MqConstants.Wallet.ROUTING_CREATE_COMPENSATION);
    }
}
