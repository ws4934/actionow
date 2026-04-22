package com.actionow.canvas.event;

import com.actionow.canvas.entity.CanvasNode;
import com.actionow.common.core.id.UuidGenerator;
import lombok.Getter;

/**
 * 节点更新事件
 *
 * @author Actionow
 */
@Getter
public class NodeUpdatedEvent extends CanvasDomainEvent {

    private final CanvasNode node;
    private final CanvasNode previousState;

    public NodeUpdatedEvent(CanvasNode node, CanvasNode previousState, String triggeredBy, String workspaceId) {
        super(UuidGenerator.generateUuidV7(), triggeredBy, workspaceId);
        this.node = node;
        this.previousState = previousState;
    }

    @Override
    public String getEventType() {
        return "NODE_UPDATED";
    }
}
