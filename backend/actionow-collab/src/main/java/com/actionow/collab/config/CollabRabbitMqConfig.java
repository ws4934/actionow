package com.actionow.collab.config;

import com.actionow.collab.constant.CollabConstants;
import com.actionow.common.mq.constant.MqConstants;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 协作服务 RabbitMQ 配置
 * 定义协作服务特有的交换机、队列和绑定
 * 基础设施（MessageConverter、RabbitTemplate、DirectExchange）由 actionow-common-mq 提供
 *
 * @author Actionow
 */
@Configuration
public class CollabRabbitMqConfig {

    /**
     * 协作交换机
     */
    @Bean
    public TopicExchange collabExchange() {
        return new TopicExchange(CollabConstants.MqExchange.COLLAB_EXCHANGE, true, false);
    }

    /**
     * 实体创建队列
     */
    @Bean
    public Queue entityCreatedQueue() {
        return QueueBuilder.durable(CollabConstants.MqQueue.ENTITY_CREATED)
                .withArgument("x-message-ttl", 60000)
                .build();
    }

    /**
     * 实体更新队列
     */
    @Bean
    public Queue entityUpdatedQueue() {
        return QueueBuilder.durable(CollabConstants.MqQueue.ENTITY_UPDATED)
                .withArgument("x-message-ttl", 60000)
                .build();
    }

    /**
     * 实体删除队列
     */
    @Bean
    public Queue entityDeletedQueue() {
        return QueueBuilder.durable(CollabConstants.MqQueue.ENTITY_DELETED)
                .withArgument("x-message-ttl", 60000)
                .build();
    }

    /**
     * Agent 活动队列
     */
    @Bean
    public Queue agentActivityQueue() {
        return QueueBuilder.durable(CollabConstants.MqQueue.AGENT_ACTIVITY)
                .withArgument("x-message-ttl", 30000)
                .build();
    }

    /**
     * WebSocket 通知队列（任务状态、通用通知）
     * 注意：DLX 通过 RabbitMQ Policy 统一配置（见 docker/init-rabbitmq/），
     * 不在 Queue arguments 中声明，避免与已有队列声明冲突。
     */
    @Bean
    public Queue wsNotificationQueue() {
        return QueueBuilder.durable(MqConstants.Ws.QUEUE_NOTIFICATION)
                .withArgument("x-message-ttl", 60000)
                .build();
    }


    /**
     * 绑定：实体创建
     */
    @Bean
    public Binding entityCreatedBinding(Queue entityCreatedQueue, TopicExchange collabExchange) {
        return BindingBuilder.bind(entityCreatedQueue)
                .to(collabExchange)
                .with(CollabConstants.MqRoutingKey.ENTITY_CREATED);
    }

    /**
     * 绑定：实体更新
     */
    @Bean
    public Binding entityUpdatedBinding(Queue entityUpdatedQueue, TopicExchange collabExchange) {
        return BindingBuilder.bind(entityUpdatedQueue)
                .to(collabExchange)
                .with(CollabConstants.MqRoutingKey.ENTITY_UPDATED);
    }

    /**
     * 绑定：实体删除
     */
    @Bean
    public Binding entityDeletedBinding(Queue entityDeletedQueue, TopicExchange collabExchange) {
        return BindingBuilder.bind(entityDeletedQueue)
                .to(collabExchange)
                .with(CollabConstants.MqRoutingKey.ENTITY_DELETED);
    }

    /**
     * 绑定：Agent 活动
     */
    @Bean
    public Binding agentActivityBinding(Queue agentActivityQueue, TopicExchange collabExchange) {
        return BindingBuilder.bind(agentActivityQueue)
                .to(collabExchange)
                .with(CollabConstants.MqRoutingKey.AGENT_ACTIVITY);
    }

    /**
     * 绑定：任务状态通知
     */
    @Bean
    public Binding wsTaskStatusBinding(Queue wsNotificationQueue, DirectExchange directExchange) {
        return BindingBuilder.bind(wsNotificationQueue)
                .to(directExchange)
                .with(MqConstants.Ws.ROUTING_TASK_STATUS);
    }

    /**
     * 绑定：实体变更通知（用于 project 服务发送）
     */
    @Bean
    public Binding wsEntityChangedBinding(Queue wsNotificationQueue, DirectExchange directExchange) {
        return BindingBuilder.bind(wsNotificationQueue)
                .to(directExchange)
                .with(MqConstants.Ws.ROUTING_ENTITY_CHANGED);
    }

    /**
     * 绑定：钱包余额变动通知
     */
    @Bean
    public Binding wsWalletBalanceBinding(Queue wsNotificationQueue, DirectExchange directExchange) {
        return BindingBuilder.bind(wsNotificationQueue)
                .to(directExchange)
                .with(MqConstants.Ws.ROUTING_WALLET_BALANCE);
    }

}
