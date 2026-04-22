package com.actionow.collab.consumer;

import com.actionow.collab.constant.CollabConstants;
import com.actionow.collab.dto.message.EntityChangeEvent;
import com.actionow.collab.websocket.CollaborationHub;
import com.actionow.common.mq.message.WebSocketMessage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 实体变更消息消费者
 * 监听来自 actionow-project 的实体变更事件，广播给相关用户
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EntityChangeConsumer {

    private final CollaborationHub collaborationHub;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    private static final String DEDUP_KEY_PREFIX = "collab:entity:dedup:";
    private static final Duration DEDUP_TTL = Duration.ofMinutes(5);

    /**
     * 解析结果：包含事件和 messageId
     */
    private record ParsedMessage(EntityChangeEvent event, String messageId) {}

    /**
     * 从 MessageWrapper 中提取 EntityChangeEvent 和 messageId
     * 消息格式: { "messageId": "...", "payload": { ...实际数据... }, ... }
     */
    private ParsedMessage parseMessage(Message message) throws Exception {
        // 先解析为通用 Map 获取 payload 和 messageId
        Map<String, Object> wrapper = objectMapper.readValue(message.getBody(),
                new TypeReference<Map<String, Object>>() {});

        String messageId = (String) wrapper.get("messageId");
        Object payload = wrapper.get("payload");

        EntityChangeEvent event;
        if (payload == null) {
            // 如果没有 payload 字段，尝试直接解析为 EntityChangeEvent（兼容直接发送的情况）
            event = objectMapper.readValue(message.getBody(), EntityChangeEvent.class);
        } else {
            // 将 payload 转换为 EntityChangeEvent
            event = objectMapper.convertValue(payload, EntityChangeEvent.class);
        }

        return new ParsedMessage(event, messageId);
    }

    /**
     * 幂等性校验：基于 messageId 去重
     * @return true 表示重复消息，应跳过
     */
    private boolean isDuplicate(String messageId) {
        if (messageId == null) {
            return false;
        }
        Boolean absent = redisTemplate.opsForValue()
                .setIfAbsent(DEDUP_KEY_PREFIX + messageId, "1", DEDUP_TTL);
        return !Boolean.TRUE.equals(absent);
    }

    /**
     * 监听实体创建事件
     */
    @RabbitListener(queues = CollabConstants.MqQueue.ENTITY_CREATED)
    public void handleEntityCreated(Message message, Channel channel,
                                    @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            ParsedMessage parsed = parseMessage(message);
            if (isDuplicate(parsed.messageId())) {
                log.debug("Duplicate entity created event skipped: messageId={}", parsed.messageId());
                channel.basicAck(deliveryTag, false);
                return;
            }

            EntityChangeEvent event = parsed.event();
            log.info("Entity created: type={}, id={}, scriptId={}",
                    event.getEntityType(), event.getEntityId(), event.getScriptId());

            broadcastEntityChange(event, WebSocketMessage.Action.CREATED);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("Failed to handle entity created event", e);
            nackSilently(channel, deliveryTag);
        }
    }

    /**
     * 监听实体更新事件
     */
    @RabbitListener(queues = CollabConstants.MqQueue.ENTITY_UPDATED)
    public void handleEntityUpdated(Message message, Channel channel,
                                    @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            ParsedMessage parsed = parseMessage(message);
            if (isDuplicate(parsed.messageId())) {
                log.debug("Duplicate entity updated event skipped: messageId={}", parsed.messageId());
                channel.basicAck(deliveryTag, false);
                return;
            }

            EntityChangeEvent event = parsed.event();
            log.info("Entity updated: type={}, id={}, fields={}",
                    event.getEntityType(), event.getEntityId(), event.getChangedFields());

            broadcastEntityChange(event, WebSocketMessage.Action.UPDATED);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("Failed to handle entity updated event", e);
            nackSilently(channel, deliveryTag);
        }
    }

    /**
     * 监听实体删除事件
     */
    @RabbitListener(queues = CollabConstants.MqQueue.ENTITY_DELETED)
    public void handleEntityDeleted(Message message, Channel channel,
                                    @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            ParsedMessage parsed = parseMessage(message);
            if (isDuplicate(parsed.messageId())) {
                log.debug("Duplicate entity deleted event skipped: messageId={}", parsed.messageId());
                channel.basicAck(deliveryTag, false);
                return;
            }

            EntityChangeEvent event = parsed.event();
            log.info("Entity deleted: type={}, id={}", event.getEntityType(), event.getEntityId());

            broadcastEntityChange(event, WebSocketMessage.Action.DELETED);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("Failed to handle entity deleted event", e);
            nackSilently(channel, deliveryTag);
        }
    }

    private void nackSilently(Channel channel, long deliveryTag) {
        try {
            channel.basicNack(deliveryTag, false, false);
        } catch (Exception e) {
            log.error("Failed to nack message", e);
        }
    }

    /**
     * 广播实体变更事件（使用统一 WebSocketMessage 格式）
     */
    private void broadcastEntityChange(EntityChangeEvent event, String action) {
        if (event.getScriptId() == null && event.getWorkspaceId() == null) {
            log.warn("Entity change event missing both scriptId and workspaceId");
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("operatorId", event.getOperatorId() != null ? event.getOperatorId() : "");
        if (event.getChangedFields() != null) {
            data.put("changedFields", event.getChangedFields());
        }
        if (event.getData() != null) {
            data.put("entity", event.getData());
        }

        WebSocketMessage wsMessage = WebSocketMessage.entityChanged(
                WebSocketMessage.Domain.PROJECT,
                action,
                event.getEntityType(),
                event.getEntityId(),
                event.getWorkspaceId(),
                event.getScriptId(),
                data
        );

        if (event.getScriptId() != null) {
            // 广播给剧本内用户（使用统一格式）
            collaborationHub.broadcastToScriptRaw(event.getScriptId(), wsMessage);
        } else {
            // 广播给工作空间内所有用户
            collaborationHub.sendToWorkspace(event.getWorkspaceId(), wsMessage);
        }
    }
}
