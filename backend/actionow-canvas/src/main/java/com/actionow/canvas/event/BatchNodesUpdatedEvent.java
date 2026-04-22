package com.actionow.canvas.event;

import com.actionow.canvas.entity.CanvasNode;
import com.actionow.common.core.id.UuidGenerator;
import lombok.Getter;

import java.util.List;

/**
 * 批量节点更新事件
 * 用于优化批量位置更新时的 WebSocket 广播，减少网络开销
 *
 * @author Actionow
 */
@Getter
public class BatchNodesUpdatedEvent extends CanvasDomainEvent {

    /**
     * 画布ID
     */
    private final String canvasId;

    /**
     * 更新的节点列表
     */
    private final List<CanvasNode> nodes;

    /**
     * 更新前的节点状态列表（用于审计日志等）
     */
    private final List<CanvasNode> previousStates;

    public BatchNodesUpdatedEvent(String canvasId, List<CanvasNode> nodes,
                                   List<CanvasNode> previousStates,
                                   String triggeredBy, String workspaceId) {
        super(UuidGenerator.generateUuidV7(), triggeredBy, workspaceId);
        this.canvasId = canvasId;
        this.nodes = nodes;
        this.previousStates = previousStates;
    }

    @Override
    public String getEventType() {
        return "BATCH_NODES_UPDATED";
    }

    /**
     * 获取更新的节点数量
     */
    public int getNodeCount() {
        return nodes != null ? nodes.size() : 0;
    }
}
