package com.actionow.canvas.event;

import com.actionow.canvas.entity.CanvasEdge;
import com.actionow.common.core.id.UuidGenerator;
import lombok.Getter;

/**
 * 边更新事件
 *
 * @author Actionow
 */
@Getter
public class EdgeUpdatedEvent extends CanvasDomainEvent {

    private final CanvasEdge edge;
    private final CanvasEdge previousState;

    public EdgeUpdatedEvent(CanvasEdge edge, CanvasEdge previousState, String triggeredBy, String workspaceId) {
        super(UuidGenerator.generateUuidV7(), triggeredBy, workspaceId);
        this.edge = edge;
        this.previousState = previousState;
    }

    @Override
    public String getEventType() {
        return "EDGE_UPDATED";
    }
}
