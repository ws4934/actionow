package com.actionow.canvas.event;

import com.actionow.common.core.id.UuidGenerator;
import lombok.Getter;

/**
 * 边删除事件
 *
 * @author Actionow
 */
@Getter
public class EdgeDeletedEvent extends CanvasDomainEvent {

    private final String edgeId;
    private final String canvasId;
    private final String sourceType;
    private final String sourceId;
    private final String targetType;
    private final String targetId;

    public EdgeDeletedEvent(String edgeId, String canvasId, String sourceType, String sourceId,
                            String targetType, String targetId, String triggeredBy, String workspaceId) {
        super(UuidGenerator.generateUuidV7(), triggeredBy, workspaceId);
        this.edgeId = edgeId;
        this.canvasId = canvasId;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.targetType = targetType;
        this.targetId = targetId;
    }

    @Override
    public String getEventType() {
        return "EDGE_DELETED";
    }
}
