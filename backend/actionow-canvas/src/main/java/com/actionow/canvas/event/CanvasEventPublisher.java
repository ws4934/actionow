package com.actionow.canvas.event;

/**
 * 画布事件发布器接口
 * 定义事件发布的标准接口，支持不同实现（本地、MQ等）
 *
 * @author Actionow
 */
public interface CanvasEventPublisher {

    /**
     * 发布事件
     *
     * @param event 领域事件
     */
    void publish(CanvasDomainEvent event);

    /**
     * 异步发布事件
     *
     * @param event 领域事件
     */
    void publishAsync(CanvasDomainEvent event);
}
