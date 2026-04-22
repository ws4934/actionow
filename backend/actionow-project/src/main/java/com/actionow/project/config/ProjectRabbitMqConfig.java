package com.actionow.project.config;

import com.actionow.common.mq.constant.MqConstants;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Project 服务 RabbitMQ 队列/绑定配置
 * 基础设施（Exchange、MessageConverter、RabbitTemplate）由 actionow-common-mq 提供
 *
 * @author Actionow
 */
@Configuration
public class ProjectRabbitMqConfig {

    @Bean
    public Queue fileQueue() {
        return QueueBuilder.durable(MqConstants.File.QUEUE)
                .withArgument("x-dead-letter-exchange", MqConstants.EXCHANGE_DEAD_LETTER)
                .withArgument("x-dead-letter-routing-key", MqConstants.QUEUE_DEAD_LETTER)
                .build();
    }

    @Bean
    public Queue inspirationTaskQueue() {
        return QueueBuilder.durable(MqConstants.Inspiration.QUEUE)
                .withArgument("x-dead-letter-exchange", MqConstants.EXCHANGE_DEAD_LETTER)
                .withArgument("x-dead-letter-routing-key", MqConstants.QUEUE_DEAD_LETTER)
                .build();
    }

    @Bean
    public Binding fileUploadedBinding(Queue fileQueue, DirectExchange directExchange) {
        return BindingBuilder.bind(fileQueue).to(directExchange).with(MqConstants.File.ROUTING_UPLOADED);
    }

    @Bean
    public Binding fileThumbnailBinding(Queue fileQueue, DirectExchange directExchange) {
        return BindingBuilder.bind(fileQueue).to(directExchange).with(MqConstants.File.ROUTING_THUMBNAIL_REQUEST);
    }

    @Bean
    public Binding inspirationTaskBinding(Queue inspirationTaskQueue, DirectExchange directExchange) {
        return BindingBuilder.bind(inspirationTaskQueue).to(directExchange).with(MqConstants.Task.ROUTING_COMPLETED);
    }
}
