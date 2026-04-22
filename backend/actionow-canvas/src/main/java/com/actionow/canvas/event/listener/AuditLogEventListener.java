package com.actionow.canvas.event.listener;

import com.actionow.canvas.event.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 审计日志事件监听器
 * 监听画布事件并记录审计日志
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogEventListener {

    @EventListener
    @Async("canvasEventExecutor")
    public void onNodeCreated(NodeCreatedEvent event) {
        logAudit("NODE_CREATED", event.getNode().getId(), event);
    }

    @EventListener
    @Async("canvasEventExecutor")
    public void onNodeUpdated(NodeUpdatedEvent event) {
        logAudit("NODE_UPDATED", event.getNode().getId(), event);
    }

    @EventListener
    @Async("canvasEventExecutor")
    public void onNodeDeleted(NodeDeletedEvent event) {
        logAudit("NODE_DELETED", event.getNodeId(), event);
    }

    @EventListener
    @Async("canvasEventExecutor")
    public void onEdgeCreated(EdgeCreatedEvent event) {
        logAudit("EDGE_CREATED", event.getEdge().getId(), event);
    }

    @EventListener
    @Async("canvasEventExecutor")
    public void onEdgeDeleted(EdgeDeletedEvent event) {
        logAudit("EDGE_DELETED", event.getEdgeId(), event);
    }

    @EventListener
    @Async("canvasEventExecutor")
    public void onCanvasLayoutChanged(CanvasLayoutChangedEvent event) {
        logAudit("CANVAS_LAYOUT_CHANGED", event.getCanvasId(), event);
    }

    private void logAudit(String action, String targetId, CanvasDomainEvent event) {
        // TODO: 调用 Collab 服务的审计日志 API
        log.debug("审计日志: action={}, targetId={}, userId={}, workspaceId={}, eventId={}",
                action,
                targetId,
                event.getTriggeredBy(),
                event.getWorkspaceId(),
                event.getEventId());
    }
}
