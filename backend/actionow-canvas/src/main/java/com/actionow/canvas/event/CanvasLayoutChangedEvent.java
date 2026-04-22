package com.actionow.canvas.event;

import com.actionow.canvas.entity.Canvas;
import com.actionow.common.core.id.UuidGenerator;
import lombok.Getter;

/**
 * 画布布局变更事件
 *
 * @author Actionow
 */
@Getter
public class CanvasLayoutChangedEvent extends CanvasDomainEvent {

    private final String canvasId;
    private final String layoutStrategy;
    private final int affectedNodes;

    public CanvasLayoutChangedEvent(String canvasId, String layoutStrategy, int affectedNodes,
                                     String triggeredBy, String workspaceId) {
        super(UuidGenerator.generateUuidV7(), triggeredBy, workspaceId);
        this.canvasId = canvasId;
        this.layoutStrategy = layoutStrategy;
        this.affectedNodes = affectedNodes;
    }

    @Override
    public String getEventType() {
        return "CANVAS_LAYOUT_CHANGED";
    }
}
