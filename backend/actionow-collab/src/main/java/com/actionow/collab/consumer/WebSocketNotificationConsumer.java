package com.actionow.collab.consumer;

import com.actionow.collab.constant.CollabConstants;
import com.actionow.collab.notification.entity.Notification;
import com.actionow.collab.notification.entity.NotificationPreference;
import com.actionow.collab.notification.mapper.NotificationPreferenceMapper;
import com.actionow.collab.notification.service.NotificationService;
import com.actionow.common.mq.constant.MqConstants;
import com.actionow.common.mq.message.WebSocketMessage;
import com.actionow.collab.websocket.CollaborationHub;
import com.actionow.collab.dto.message.OutboundMessage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

/**
 * WebSocket 通知消费者
 * 监听来自其他服务的通知事件，通过 WebSocket 推送给前端
 * 同时持久化通知到数据库（任务状态、实体变更等跨服务通知）
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketNotificationConsumer {

    private final CollaborationHub collaborationHub;
    private final NotificationService notificationService;
    private final NotificationPreferenceMapper preferenceMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String DEDUP_KEY_PREFIX = "collab:ws:dedup:";
    // 延长到 24h：确定性 eventId 下，同一逻辑事件即使跨长间隔重试也应被判重
    private static final Duration DEDUP_TTL = Duration.ofHours(24);

    /**
     * 监听 WebSocket 通知队列
     */
    @RabbitListener(queues = MqConstants.Ws.QUEUE_NOTIFICATION)
    public void handleNotification(Message message, Channel channel,
                                   @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            // 先解析为通用 Map 获取 payload
            Map<String, Object> wrapper = objectMapper.readValue(message.getBody(),
                    new TypeReference<Map<String, Object>>() {});

            Object payload = wrapper.get("payload");
            WebSocketMessage wsMessage;

            if (payload == null) {
                wsMessage = objectMapper.readValue(message.getBody(), WebSocketMessage.class);
            } else {
                wsMessage = objectMapper.convertValue(payload, WebSocketMessage.class);
            }

            // 幂等性校验：基于 eventId 去重
            String eventId = wsMessage.getEventId();
            if (eventId != null) {
                Boolean absent = redisTemplate.opsForValue()
                        .setIfAbsent(DEDUP_KEY_PREFIX + eventId, "1", DEDUP_TTL);
                if (!Boolean.TRUE.equals(absent)) {
                    log.debug("Duplicate WebSocket notification skipped: eventId={}", eventId);
                    channel.basicAck(deliveryTag, false);
                    return;
                }
            }

            String workspaceId = wsMessage.getWorkspaceId();
            if (workspaceId == null) {
                workspaceId = (String) wrapper.get("workspaceId");
            }

            if (workspaceId == null) {
                log.warn("WebSocket notification missing workspaceId, discarding: type={}", wsMessage.getType());
                channel.basicNack(deliveryTag, false, false);
                return;
            }

            // 读取 targetUserId：先查外层 wrapper，再兜底到 wsMessage.data
            // TaskNotificationService 将 targetUserId 放在 data 中（MessageWrapper 包装后在 payload 内）
            String targetUserId = (String) wrapper.get("targetUserId");
            if (targetUserId == null && wsMessage.getData() != null) {
                Object fromData = wsMessage.getData().get("targetUserId");
                if (fromData instanceof String) {
                    targetUserId = (String) fromData;
                }
            }

            if (targetUserId != null) {
                persistNotification(wsMessage, workspaceId, targetUserId, wrapper);
            }

            log.debug("Broadcasting WebSocket notification: type={}, domain={}, workspaceId={}",
                    wsMessage.getType(), wsMessage.getDomain(), workspaceId);

            // 根据 scriptId 决定广播范围
            if (wsMessage.getScriptId() != null) {
                collaborationHub.sendToScript(wsMessage.getScriptId(),
                        OutboundMessage.of(wsMessage.getType(), wsMessage));
            } else if (targetUserId != null) {
                // 定向推送给特定用户
                collaborationHub.sendToUser(workspaceId, targetUserId,
                        OutboundMessage.of(wsMessage.getType(), wsMessage));
            } else {
                collaborationHub.sendToWorkspace(workspaceId, wsMessage);
            }

            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("Failed to handle WebSocket notification", e);
            try {
                // 不重入队，防止毒丸消息无限循环；路由至 DLQ 等待人工处理
                channel.basicNack(deliveryTag, false, false);
            } catch (Exception nackEx) {
                log.error("Failed to nack message", nackEx);
            }
        }
    }

    /**
     * 持久化跨服务通知到数据库
     * 检查用户偏好（taskCompleted / entityChange / systemAlert）和免打扰时段
     */
    private void persistNotification(WebSocketMessage wsMessage, String workspaceId,
                                      String targetUserId, Map<String, Object> wrapper) {
        try {
            String type = wsMessage.getType();

            // 检查用户偏好
            NotificationPreference pref = preferenceMapper.selectByUserAndWorkspace(targetUserId, workspaceId);
            if (pref != null) {
                if (isTaskType(type) && !Boolean.TRUE.equals(pref.getTaskCompleted())) return;
                if (isEntityChangeType(type) && !Boolean.TRUE.equals(pref.getEntityChange())) return;
                if (isSystemType(type) && !Boolean.TRUE.equals(pref.getSystemAlert())) return;
                if (isInQuietHours(pref)) return;
            }

            Notification notification = Notification.builder()
                    .workspaceId(workspaceId)
                    .userId(targetUserId)
                    .type(type)
                    .title((String) wrapper.getOrDefault("title", type))
                    .content((String) wrapper.get("content"))
                    .entityType(wsMessage.getDomain())
                    .entityId((String) wrapper.get("entityId"))
                    .senderId((String) wrapper.get("senderId"))
                    .senderName((String) wrapper.get("senderName"))
                    .isRead(false)
                    .priority(2)
                    .eventId(wsMessage.getEventId())
                    .createdAt(LocalDateTime.now())
                    .build();

            try {
                notificationService.persist(notification);
            } catch (DuplicateKeyException dup) {
                // DB 层唯一索引兜底：同一 eventId 已被其它消费者/实例抢先写入，直接跳过后续计数与推送
                log.debug("Duplicate notification suppressed by unique index: eventId={}, user={}",
                        wsMessage.getEventId(), targetUserId);
                return;
            }

            // Update Redis unread count
            String unreadKey = CollabConstants.RedisKey.NOTIFY_UNREAD + targetUserId;
            long newTotal = redisTemplate.opsForHash().increment(unreadKey, "total", 1);
            redisTemplate.opsForHash().increment(unreadKey, type, 1);
            redisTemplate.expire(unreadKey, Duration.ofDays(30));

            // Push unread count update via WebSocket
            collaborationHub.sendToUser(workspaceId, targetUserId,
                    OutboundMessage.of(CollabConstants.MessageType.NOTIFICATION_COUNT, Map.of(
                            "total", newTotal, "delta", 1
                    )));
        } catch (Exception e) {
            log.error("Failed to persist notification for user {}", targetUserId, e);
        }
    }

    private boolean isTaskType(String type) {
        return type != null && (type.startsWith("TASK_") || type.startsWith("GENERATION_"));
    }

    private boolean isEntityChangeType(String type) {
        return type != null && type.startsWith("ENTITY_");
    }

    private boolean isSystemType(String type) {
        return type != null && type.startsWith("SYSTEM_");
    }

    private boolean isInQuietHours(NotificationPreference pref) {
        if (pref.getQuietStart() == null || pref.getQuietEnd() == null) {
            return false;
        }
        LocalTime now = LocalTime.now();
        LocalTime start = pref.getQuietStart();
        LocalTime end = pref.getQuietEnd();
        if (!start.isAfter(end)) {
            return !now.isBefore(start) && !now.isAfter(end);
        } else {
            return !now.isBefore(start) || !now.isAfter(end);
        }
    }
}
