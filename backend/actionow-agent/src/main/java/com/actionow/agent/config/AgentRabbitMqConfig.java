package com.actionow.agent.config;

import com.actionow.common.mq.constant.MqConstants;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 服务 RabbitMQ 队列/绑定配置
 * 基础设施（Exchange、MessageConverter、RabbitTemplate）由 actionow-common-mq 提供
 *
 * @author Actionow
 */
@Configuration
public class AgentRabbitMqConfig {

    @Bean
    public Queue missionQueue() {
        return QueueBuilder.durable(MqConstants.Mission.QUEUE)
                .withArgument("x-dead-letter-exchange", MqConstants.EXCHANGE_DEAD_LETTER)
                .withArgument("x-dead-letter-routing-key", MqConstants.QUEUE_DEAD_LETTER)
                .build();
    }

    @Bean
    public Queue missionTaskCallbackQueue() {
        return QueueBuilder.durable(MqConstants.Mission.QUEUE_TASK_CALLBACK)
                .withArgument("x-dead-letter-exchange", MqConstants.EXCHANGE_DEAD_LETTER)
                .withArgument("x-dead-letter-routing-key", MqConstants.QUEUE_DEAD_LETTER)
                .build();
    }

    @Bean
    public Binding missionStepBinding(Queue missionQueue, DirectExchange directExchange) {
        return BindingBuilder.bind(missionQueue).to(directExchange).with(MqConstants.Mission.ROUTING_STEP_EXECUTE);
    }

    @Bean
    public Binding missionTaskCallbackBinding(Queue missionTaskCallbackQueue, DirectExchange directExchange) {
        return BindingBuilder.bind(missionTaskCallbackQueue).to(directExchange).with(MqConstants.Mission.ROUTING_TASK_CALLBACK);
    }
}
