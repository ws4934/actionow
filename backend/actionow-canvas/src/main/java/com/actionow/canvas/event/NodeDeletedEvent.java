package com.actionow.canvas.event;

import com.actionow.common.core.id.UuidGenerator;
import lombok.Getter;

/**
 * 节点删除事件
 *
 * @author Actionow
 */
@Getter
public class NodeDeletedEvent extends CanvasDomainEvent {

    private final String nodeId;
    private final String canvasId;
    private final String entityType;
    private final String entityId;

    public NodeDeletedEvent(String nodeId, String canvasId, String entityType, String entityId,
                            String triggeredBy, String workspaceId) {
        super(UuidGenerator.generateUuidV7(), triggeredBy, workspaceId);
        this.nodeId = nodeId;
        this.canvasId = canvasId;
        this.entityType = entityType;
        this.entityId = entityId;
    }

    @Override
    public String getEventType() {
        return "NODE_DELETED";
    }
}
