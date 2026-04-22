package com.actionow.canvas.event;

import java.time.Instant;

/**
 * 画布领域事件基类
 * 所有画布相关事件的抽象父类
 *
 * @author Actionow
 */
public abstract class CanvasDomainEvent {

    /**
     * 事件ID
     */
    private final String eventId;

    /**
     * 事件发生时间
     */
    private final Instant occurredAt;

    /**
     * 触发用户ID
     */
    private final String triggeredBy;

    /**
     * 工作空间ID
     */
    private final String workspaceId;

    protected CanvasDomainEvent(String eventId, String triggeredBy, String workspaceId) {
        this.eventId = eventId;
        this.occurredAt = Instant.now();
        this.triggeredBy = triggeredBy;
        this.workspaceId = workspaceId;
    }

    public String getEventId() {
        return eventId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getTriggeredBy() {
        return triggeredBy;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    /**
     * 获取事件类型名称
     */
    public abstract String getEventType();
}
