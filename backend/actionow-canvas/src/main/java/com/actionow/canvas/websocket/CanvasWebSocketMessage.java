package com.actionow.canvas.websocket;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 画布 WebSocket 消息
 *
 * @author Actionow
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CanvasWebSocketMessage {

    /**
     * 消息类型
     */
    private Type type;

    /**
     * 消息数据
     */
    private Map<String, Object> data;

    /**
     * 消息时间戳（毫秒），用于前端判断消息顺序
     */
    private Long timestamp;

    /**
     * 事件ID，用于前端幂等性检查
     */
    private String eventId;

    /**
     * 兼容旧构造函数
     */
    public CanvasWebSocketMessage(Type type, Map<String, Object> data) {
        this.type = type;
        this.data = data;
        this.timestamp = Instant.now().toEpochMilli();
        this.eventId = null; // 简单消息不需要 eventId
    }

    /**
     * 带事件ID的构造函数
     */
    public CanvasWebSocketMessage(Type type, Map<String, Object> data, String eventId) {
        this.type = type;
        this.data = data;
        this.timestamp = Instant.now().toEpochMilli();
        this.eventId = eventId;
    }

    /**
     * 消息类型枚举
     */
    public enum Type {
        // 连接状态
        CONNECTED,
        DISCONNECTED,

        // 心跳
        PING,
        PONG,

        // 协作事件
        CURSOR_MOVE,       // 光标移动
        SELECTION_CHANGE,  // 选择变更
        NODE_DRAG,         // 节点拖拽中

        // 数据变更（服务端广播）
        NODE_CREATED,
        NODE_UPDATED,
        NODE_DELETED,
        EDGE_CREATED,
        EDGE_UPDATED,
        EDGE_DELETED,
        CANVAS_UPDATED,
        LAYOUT_CHANGED,

        // 批量更新（优化网络传输）
        BATCH_NODES_UPDATED,

        // 用户状态
        USER_JOINED,
        USER_LEFT
    }

    /**
     * 创建节点创建消息
     */
    public static CanvasWebSocketMessage nodeCreated(Map<String, Object> nodeData) {
        return new CanvasWebSocketMessage(Type.NODE_CREATED, nodeData);
    }

    /**
     * 创建节点更新消息
     */
    public static CanvasWebSocketMessage nodeUpdated(Map<String, Object> nodeData) {
        return new CanvasWebSocketMessage(Type.NODE_UPDATED, nodeData);
    }

    /**
     * 创建节点更新消息（带事件ID，用于幂等性检查）
     */
    public static CanvasWebSocketMessage nodeUpdated(Map<String, Object> nodeData, String eventId) {
        return new CanvasWebSocketMessage(Type.NODE_UPDATED, nodeData, eventId);
    }

    /**
     * 创建节点删除消息
     */
    public static CanvasWebSocketMessage nodeDeleted(String nodeId) {
        return new CanvasWebSocketMessage(Type.NODE_DELETED, Map.of("nodeId", nodeId));
    }

    /**
     * 创建边创建消息
     */
    public static CanvasWebSocketMessage edgeCreated(Map<String, Object> edgeData) {
        return new CanvasWebSocketMessage(Type.EDGE_CREATED, edgeData);
    }

    /**
     * 创建边更新消息
     */
    public static CanvasWebSocketMessage edgeUpdated(Map<String, Object> edgeData) {
        return new CanvasWebSocketMessage(Type.EDGE_UPDATED, edgeData);
    }

    /**
     * 创建边删除消息
     */
    public static CanvasWebSocketMessage edgeDeleted(String edgeId) {
        return new CanvasWebSocketMessage(Type.EDGE_DELETED, Map.of("edgeId", edgeId));
    }

    /**
     * 创建画布更新消息
     */
    public static CanvasWebSocketMessage canvasUpdated(Map<String, Object> canvasData) {
        return new CanvasWebSocketMessage(Type.CANVAS_UPDATED, canvasData);
    }

    /**
     * 创建画布更新消息（带事件ID）
     */
    public static CanvasWebSocketMessage canvasUpdated(Map<String, Object> canvasData, String eventId) {
        return new CanvasWebSocketMessage(Type.CANVAS_UPDATED, canvasData, eventId);
    }

    /**
     * 创建布局变更消息
     */
    public static CanvasWebSocketMessage layoutChanged(String canvasId, String strategy) {
        return new CanvasWebSocketMessage(Type.LAYOUT_CHANGED,
                Map.of("canvasId", canvasId, "strategy", strategy));
    }

    /**
     * 创建批量节点更新消息（优化多节点同时更新的网络传输）
     */
    public static CanvasWebSocketMessage batchNodesUpdated(java.util.List<Map<String, Object>> nodesData,
                                                           String triggeredBy) {
        Map<String, Object> data = new HashMap<>();
        data.put("nodes", nodesData);
        data.put("triggeredBy", triggeredBy);
        data.put("count", nodesData.size());
        return new CanvasWebSocketMessage(Type.BATCH_NODES_UPDATED, data);
    }
}
