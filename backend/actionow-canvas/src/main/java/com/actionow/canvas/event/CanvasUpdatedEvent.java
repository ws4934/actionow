package com.actionow.canvas.event;

import com.actionow.canvas.entity.Canvas;
import com.actionow.common.core.id.UuidGenerator;
import lombok.Getter;

/**
 * 画布更新事件
 *
 * @author Actionow
 */
@Getter
public class CanvasUpdatedEvent extends CanvasDomainEvent {

    private final Canvas canvas;
    private final Canvas previousState;

    public CanvasUpdatedEvent(Canvas canvas, Canvas previousState, String triggeredBy, String workspaceId) {
        super(UuidGenerator.generateUuidV7(), triggeredBy, workspaceId);
        this.canvas = canvas;
        this.previousState = previousState;
    }

    @Override
    public String getEventType() {
        return "CANVAS_UPDATED";
    }
}
