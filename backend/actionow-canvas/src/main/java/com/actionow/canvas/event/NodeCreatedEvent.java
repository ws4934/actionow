package com.actionow.canvas.event;

import com.actionow.canvas.entity.CanvasNode;
import com.actionow.common.core.id.UuidGenerator;
import lombok.Getter;

/**
 * 节点创建事件
 *
 * @author Actionow
 */
@Getter
public class NodeCreatedEvent extends CanvasDomainEvent {

    private final CanvasNode node;
    private final boolean createEntity;

    public NodeCreatedEvent(CanvasNode node, boolean createEntity, String triggeredBy, String workspaceId) {
        super(UuidGenerator.generateUuidV7(), triggeredBy, workspaceId);
        this.node = node;
        this.createEntity = createEntity;
    }

    @Override
    public String getEventType() {
        return "NODE_CREATED";
    }
}
