package com.actionow.canvas.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Spring 事件发布器实现
 * 使用 Spring ApplicationEventPublisher 发布本地事件
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpringEventPublisher implements CanvasEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publish(CanvasDomainEvent event) {
        log.debug("发布同步事件: eventType={}, eventId={}", event.getEventType(), event.getEventId());
        applicationEventPublisher.publishEvent(event);
    }

    /**
     * 发布事件（支持事务同步）
     *
     * 注意：移除了 @Async，改为同步发布。
     * 配合 @TransactionalEventListener 使用，确保：
     * 1. 事件在事务上下文中发布
     * 2. 监听器在事务提交后才执行
     * 3. 事务回滚时不会发送 WebSocket 消息
     */
    @Override
    public void publishAsync(CanvasDomainEvent event) {
        log.debug("发布事务同步事件: eventType={}, eventId={}", event.getEventType(), event.getEventId());
        applicationEventPublisher.publishEvent(event);
    }
}
