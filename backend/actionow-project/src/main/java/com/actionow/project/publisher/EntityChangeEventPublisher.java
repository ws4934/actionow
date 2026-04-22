package com.actionow.project.publisher;

import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.mq.constant.MqConstants;
import com.actionow.common.mq.message.CollabEntityChangeEvent;
import com.actionow.common.mq.producer.MessageProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * 实体变更事件发布器
 * 向协作服务发送实体创建/更新/删除事件
 * <p>
 * 同步捕获 UserContext，事务提交后发送 MQ 消息，
 * 避免 @Async 导致上下文丢失和事务未提交就发事件的问题。
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EntityChangeEventPublisher {

    private final MessageProducer messageProducer;

    /**
     * 发布实体创建事件
     */
    public void publishEntityCreated(String entityType, String entityId, String scriptId, Object data) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String operatorId = UserContextHolder.getUserId();

        CollabEntityChangeEvent event = CollabEntityChangeEvent.builder()
                .eventType(CollabEntityChangeEvent.EventType.CREATED)
                .entityType(entityType)
                .entityId(entityId)
                .workspaceId(workspaceId)
                .scriptId(scriptId)
                .operatorId(operatorId)
                .data(data)
                .build();

        sendAfterCommit(MqConstants.Collab.ROUTING_ENTITY_CREATED, MqConstants.Collab.MSG_ENTITY_CREATED, event);
        log.debug("Published entity created event: type={}, id={}", entityType, entityId);
    }

    /**
     * 发布实体更新事件
     */
    public void publishEntityUpdated(String entityType, String entityId, String scriptId,
                                      List<String> changedFields, Object data) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String operatorId = UserContextHolder.getUserId();

        CollabEntityChangeEvent event = CollabEntityChangeEvent.builder()
                .eventType(CollabEntityChangeEvent.EventType.UPDATED)
                .entityType(entityType)
                .entityId(entityId)
                .workspaceId(workspaceId)
                .scriptId(scriptId)
                .operatorId(operatorId)
                .changedFields(changedFields)
                .data(data)
                .build();

        sendAfterCommit(MqConstants.Collab.ROUTING_ENTITY_UPDATED, MqConstants.Collab.MSG_ENTITY_UPDATED, event);
        log.debug("Published entity updated event: type={}, id={}, fields={}", entityType, entityId, changedFields);
    }

    /**
     * 发布实体删除事件
     */
    public void publishEntityDeleted(String entityType, String entityId, String scriptId) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String operatorId = UserContextHolder.getUserId();

        CollabEntityChangeEvent event = CollabEntityChangeEvent.builder()
                .eventType(CollabEntityChangeEvent.EventType.DELETED)
                .entityType(entityType)
                .entityId(entityId)
                .workspaceId(workspaceId)
                .scriptId(scriptId)
                .operatorId(operatorId)
                .build();

        sendAfterCommit(MqConstants.Collab.ROUTING_ENTITY_DELETED, MqConstants.Collab.MSG_ENTITY_DELETED, event);
        log.debug("Published entity deleted event: type={}, id={}", entityType, entityId);
    }

    /**
     * 如果当前存在活跃事务，延迟到事务提交后发送；否则立即发送。
     */
    private void sendAfterCommit(String routingKey, String messageType, CollabEntityChangeEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendEvent(routingKey, messageType, event);
                }
            });
        } else {
            sendEvent(routingKey, messageType, event);
        }
    }

    private void sendEvent(String routingKey, String messageType, CollabEntityChangeEvent event) {
        try {
            messageProducer.send(MqConstants.EXCHANGE_COLLAB, routingKey, messageType, event);
        } catch (Exception e) {
            log.error("Failed to publish entity change event: type={}, id={}, error={}",
                    event.getEntityType(), event.getEntityId(), e.getMessage(), e);
        }
    }
}
