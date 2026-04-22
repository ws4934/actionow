package com.actionow.canvas.event;

import com.actionow.canvas.entity.CanvasEdge;
import com.actionow.common.core.id.UuidGenerator;
import lombok.Getter;

/**
 * 边创建事件
 *
 * @author Actionow
 */
@Getter
public class EdgeCreatedEvent extends CanvasDomainEvent {

    private final CanvasEdge edge;

    public EdgeCreatedEvent(CanvasEdge edge, String triggeredBy, String workspaceId) {
        super(UuidGenerator.generateUuidV7(), triggeredBy, workspaceId);
        this.edge = edge;
    }

    @Override
    public String getEventType() {
        return "EDGE_CREATED";
    }
}
