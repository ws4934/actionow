package com.actionow.common.mq.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Outbox 自动配置
 * <p>
 * 当 classpath 上存在 JdbcTemplate（即服务有数据库连接）时自动激活。
 * 可通过 {@code actionow.outbox.enabled=false} 关闭。
 *
 * @author Actionow
 */
@Configuration
@EnableScheduling
@AutoConfigureAfter(JdbcTemplateAutoConfiguration.class)
@ConditionalOnClass(JdbcTemplate.class)
@ConditionalOnBean(JdbcTemplate.class)
@ConditionalOnProperty(name = "actionow.outbox.enabled", havingValue = "true", matchIfMissing = false)
public class OutboxAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OutboxAutoConfiguration.class);

    @Bean
    public OutboxMessageRepository outboxMessageRepository(JdbcTemplate jdbcTemplate) {
        log.info("初始化 Outbox 消息仓库 (Transactional Outbox Pattern)");
        return new OutboxMessageRepository(jdbcTemplate);
    }

    @Bean
    public TransactionalMessageProducer transactionalMessageProducer(
            OutboxMessageRepository outboxRepository) {
        // 创建与 RabbitMqConfig.messageConverter() 相同配置的 ObjectMapper，
        // 确保 outbox 存储的 JSON 与 RabbitMQ 消费端期望的格式完全一致。
        // 不使用 DefaultTyping — 消费端用 INFERRED 模式从方法参数推断类型。
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        log.info("初始化事务性消息生产者 (TransactionalMessageProducer)");
        return new TransactionalMessageProducer(outboxRepository, mapper);
    }

    @Bean
    public OutboxMessagePublisher outboxMessagePublisher(
            OutboxMessageRepository outboxRepository,
            RabbitTemplate rabbitTemplate) {
        log.info("初始化 Outbox 消息轮询发布器 (OutboxMessagePublisher)");
        return new OutboxMessagePublisher(outboxRepository, rabbitTemplate);
    }
}
