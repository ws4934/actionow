package com.actionow.task.config;

import com.actionow.common.mq.constant.MqConstants;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Task 模块 RabbitMQ 消费者配置
 * <p>
 * 全局 factory（rabbitListenerContainerFactory）适用于高吞吐量轻量队列（批量回调、通知等）：
 * concurrentConsumers=1，prefetch=10。
 * <p>
 * 此处定义的 taskExecutorContainerFactory 专用于 AI 生成任务队列：
 * 每条任务可能阻塞 1~60 秒等待 AI 服务返回，因此需要多个并发 consumer，
 * 同时 prefetch=1 保证每个 consumer 每次只持有一条消息，避免慢任务堆积。
 * <p>
 * 初始并发数来源（优先级从高到低）：
 * 1. Redis system:config:GLOBAL:runtime.mq.task_concurrency（系统模块动态配置）
 * 2. 代码注册的默认值（5/10）
 * <p>
 * 运行时修改：通过系统模块写入 Redis 并触发 Pub/Sub，
 * TaskRuntimeConfigService.onConfigChanged() 会自动调整运行中的 consumer 数量，无需重启。
 *
 * @author Actionow
 */
@Configuration
public class TaskMqConfig {

    /**
     * 专用于 AI 任务队列的 listener container factory
     * <p>
     * 初始并发数从 TaskRuntimeConfigService 读取（已在 @PostConstruct 中从 Redis 加载）。
     * TaskRuntimeConfigService 作为 @Bean 参数注入，Spring 保证其 @PostConstruct 先于本方法执行。
     */
    // ==================== Queue ====================

    @Bean
    public Queue taskQueue() {
        return QueueBuilder.durable(MqConstants.Task.QUEUE)
                .withArgument("x-dead-letter-exchange", MqConstants.EXCHANGE_DEAD_LETTER)
                .withArgument("x-dead-letter-routing-key", MqConstants.QUEUE_DEAD_LETTER)
                .build();
    }

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(MqConstants.Task.QUEUE_NOTIFICATION)
                .withArgument("x-dead-letter-exchange", MqConstants.EXCHANGE_DEAD_LETTER)
                .withArgument("x-dead-letter-routing-key", MqConstants.QUEUE_DEAD_LETTER)
                .build();
    }

    @Bean
    public Queue batchJobQueue() {
        return QueueBuilder.durable(MqConstants.BatchJob.QUEUE)
                .withArgument("x-dead-letter-exchange", MqConstants.EXCHANGE_DEAD_LETTER)
                .withArgument("x-dead-letter-routing-key", MqConstants.QUEUE_DEAD_LETTER)
                .build();
    }

    @Bean
    public Queue batchJobTaskCallbackQueue() {
        return QueueBuilder.durable(MqConstants.BatchJob.QUEUE_TASK_CALLBACK)
                .withArgument("x-dead-letter-exchange", MqConstants.EXCHANGE_DEAD_LETTER)
                .withArgument("x-dead-letter-routing-key", MqConstants.QUEUE_DEAD_LETTER)
                .build();
    }

    // ==================== Binding ====================

    @Bean
    public Binding taskCreatedBinding(Queue taskQueue, DirectExchange directExchange) {
        return BindingBuilder.bind(taskQueue).to(directExchange).with(MqConstants.Task.ROUTING_CREATED);
    }

    @Bean
    public Binding taskCompletedBinding(Queue notificationQueue, DirectExchange directExchange) {
        return BindingBuilder.bind(notificationQueue).to(directExchange).with(MqConstants.Task.ROUTING_COMPLETED);
    }

    @Bean
    public Binding batchJobStartBinding(Queue batchJobQueue, DirectExchange directExchange) {
        return BindingBuilder.bind(batchJobQueue).to(directExchange).with(MqConstants.BatchJob.ROUTING_START);
    }

    @Bean
    public Binding batchJobTaskCallbackBinding(Queue batchJobTaskCallbackQueue, DirectExchange directExchange) {
        return BindingBuilder.bind(batchJobTaskCallbackQueue).to(directExchange).with(MqConstants.BatchJob.ROUTING_TASK_CALLBACK);
    }

    // ==================== Container Factory ====================

    @Bean("taskExecutorContainerFactory")
    public SimpleRabbitListenerContainerFactory taskExecutorContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter,
            TaskRuntimeConfigService runtimeConfig) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        // prefetch=1：每个 consumer 一次只取 1 条，保证 N 个 AI 任务均衡分配到 N 个 consumer
        factory.setPrefetchCount(1);
        factory.setConcurrentConsumers(runtimeConfig.getMqTaskConcurrency());
        factory.setMaxConcurrentConsumers(runtimeConfig.getMqTaskMaxConcurrency());
        return factory;
    }
}
