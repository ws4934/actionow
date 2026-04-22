package com.actionow.canvas.event.listener;

import com.actionow.canvas.event.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 实体同步事件监听器
 * 监听画布事件并同步到 Project 服务
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EntitySyncEventListener {

    // TODO: 注入 ProjectFeignClient 用于同步实体

    @EventListener
    @Async("canvasEventExecutor")
    public void onNodeCreated(NodeCreatedEvent event) {
        log.info("处理节点创建事件: nodeId={}, entityType={}, entityId={}, createEntity={}",
                event.getNode().getId(),
                event.getNode().getEntityType(),
                event.getNode().getEntityId(),
                event.isCreateEntity());

        if (event.isCreateEntity()) {
            // 节点关联新实体，需要通知 Project 服务创建实体
            // 这里可以通过 MQ 发送消息或直接调用 Feign
            syncEntityCreation(event);
        }
    }

    @EventListener
    @Async("canvasEventExecutor")
    public void onNodeDeleted(NodeDeletedEvent event) {
        log.info("处理节点删除事件: nodeId={}, entityType={}, entityId={}",
                event.getNodeId(),
                event.getEntityType(),
                event.getEntityId());

        // 节点删除后，可能需要通知 Project 服务更新实体关联
        // 具体逻辑取决于业务需求（是否级联删除实体等）
    }

    @EventListener
    @Async("canvasEventExecutor")
    public void onEdgeCreated(EdgeCreatedEvent event) {
        log.info("处理边创建事件: edgeId={}, source={}:{}, target={}:{}",
                event.getEdge().getId(),
                event.getEdge().getSourceType(),
                event.getEdge().getSourceId(),
                event.getEdge().getTargetType(),
                event.getEdge().getTargetId());

        // 边创建可能需要同步实体间的关系到 Project 服务
        syncRelationCreation(event);
    }

    @EventListener
    @Async("canvasEventExecutor")
    public void onEdgeDeleted(EdgeDeletedEvent event) {
        log.info("处理边删除事件: edgeId={}, source={}:{}, target={}:{}",
                event.getEdgeId(),
                event.getSourceType(),
                event.getSourceId(),
                event.getTargetType(),
                event.getTargetId());

        // 边删除需要同步实体关系的解除
    }

    @EventListener
    @Async("canvasEventExecutor")
    public void onCanvasLayoutChanged(CanvasLayoutChangedEvent event) {
        log.info("处理画布布局变更事件: canvasId={}, strategy={}, affectedNodes={}",
                event.getCanvasId(),
                event.getLayoutStrategy(),
                event.getAffectedNodes());

        // 布局变更可用于触发 WebSocket 通知
    }

    private void syncEntityCreation(NodeCreatedEvent event) {
        // TODO: 实现实体创建同步逻辑
        // 可以通过 RabbitMQ 发送消息到 Project 服务
    }

    private void syncRelationCreation(EdgeCreatedEvent event) {
        // TODO: 实现关系创建同步逻辑
        // 可以通过 Feign 调用 Project 服务的 EntityRelation API
    }
}
