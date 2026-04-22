package com.actionow.canvas.consumer;

import com.actionow.canvas.constant.CanvasConstants;
import com.actionow.common.core.context.UserContext;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.mq.constant.MqConstants;
import com.actionow.common.mq.consumer.ConsumerRetryHelper;
import com.actionow.common.mq.message.MessageWrapper;
import com.actionow.canvas.dto.EntityChangeMessage;
import com.actionow.canvas.service.CanvasSyncService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 实体变更消息消费者
 * 监听业务服务发送的实体变更消息，同步到Canvas
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EntityChangeConsumer {

    private static final int MAX_RETRIES = 3;

    private final CanvasSyncService canvasSyncService;
    private final ConsumerRetryHelper retryHelper;

    /**
     * 处理实体变更消息
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConstants.Canvas.QUEUE, durable = "true",
                    arguments = {
                            @Argument(name = "x-dead-letter-exchange", value = MqConstants.EXCHANGE_DEAD_LETTER),
                            @Argument(name = "x-dead-letter-routing-key", value = MqConstants.QUEUE_DEAD_LETTER)
                    }),
            exchange = @Exchange(value = MqConstants.EXCHANGE_TOPIC, type = "topic"),
            key = MqConstants.Canvas.ROUTING_ENTITY_CHANGE
    ))
    public void handleEntityChange(MessageWrapper<EntityChangeMessage> message, Channel channel,
                                   @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                                   @Header(value = AmqpHeaders.REDELIVERED, required = false) Boolean redelivered) {
        try {
            // 恢复上下文
            restoreContext(message);

            EntityChangeMessage payload = message.getPayload();

            // 增加诊断日志：检测重投递和重试
            if (Boolean.TRUE.equals(redelivered)) {
                log.warn("检测到消息重投递: messageId={}, entityType={}, entityId={}, scriptId={}, changeType={}, retryCount={}",
                        message.getMessageId(), payload.getEntityType(), payload.getEntityId(),
                        payload.getScriptId(), payload.getChangeType(), message.getRetryCount());
            } else {
                log.info("收到实体变更消息: messageId={}, entityType={}, entityId={}, scriptId={}, changeType={}",
                        message.getMessageId(), payload.getEntityType(), payload.getEntityId(),
                        payload.getScriptId(), payload.getChangeType());
            }

            // 提取实体信息
            String entityType = payload.getEntityType();
            String entityId = payload.getEntityId();
            String changeType = payload.getChangeType();
            Map<String, Object> entityData = payload.getEntityData();

            // 提取名称和缩略图
            String name = extractName(entityData);
            String thumbnailUrl = extractThumbnailUrl(entityData);

            // 根据变更类型处理
            switch (changeType) {
                case CanvasConstants.ChangeType.CREATED ->
                        canvasSyncService.handleEntityCreated(entityType, entityId,
                                payload.getScriptId(),
                                payload.getParentEntityType(), payload.getParentEntityId(),
                                message.getWorkspaceId(), name,
                                payload.getRelatedEntities());
                case CanvasConstants.ChangeType.UPDATED ->
                        canvasSyncService.handleEntityUpdated(entityType, entityId, name, thumbnailUrl);
                case CanvasConstants.ChangeType.DELETED ->
                        canvasSyncService.handleEntityDeleted(entityType, entityId);
                default ->
                        log.warn("未知的变更类型: changeType={}", changeType);
            }

            // 确认消息
            channel.basicAck(deliveryTag, false);
            log.debug("实体变更消息处理完成: messageId={}", message.getMessageId());

        } catch (Exception e) {
            log.error("处理实体变更消息失败: messageId={}, error={}",
                    message.getMessageId(), e.getMessage(), e);
            handleException(channel, deliveryTag, message);
        } finally {
            UserContextHolder.clear();
        }
    }

    /**
     * 恢复上下文
     */
    private void restoreContext(MessageWrapper<?> message) {
        UserContext context = new UserContext();
        context.setUserId(message.getSenderId());
        context.setWorkspaceId(message.getWorkspaceId());
        context.setTenantSchema(message.getTenantSchema());
        context.setRequestId(message.getTraceId());
        UserContextHolder.setContext(context);
    }

    /**
     * 从实体数据中提取名称
     */
    private String extractName(Map<String, Object> entityData) {
        if (entityData == null) {
            return null;
        }
        // 尝试多种字段名
        Object name = entityData.get("name");
        if (name != null) {
            return name.toString();
        }
        Object title = entityData.get("title");
        if (title != null) {
            return title.toString();
        }
        return null;
    }

    /**
     * 从实体数据中提取缩略图URL
     */
    private String extractThumbnailUrl(Map<String, Object> entityData) {
        if (entityData == null) {
            return null;
        }
        // 尝试多种字段名
        Object thumbnail = entityData.get("thumbnailUrl");
        if (thumbnail != null) {
            return thumbnail.toString();
        }
        Object cover = entityData.get("coverUrl");
        if (cover != null) {
            return cover.toString();
        }
        Object url = entityData.get("url");
        if (url != null) {
            return url.toString();
        }
        return null;
    }

    /**
     * 异常处理：通过重新发布消息递增 retryCount，超限后转入 DLQ
     */
    private void handleException(Channel channel, long deliveryTag, MessageWrapper<?> message) {
        try {
            retryHelper.retryOrDlq(message, channel, deliveryTag, MAX_RETRIES,
                    MqConstants.EXCHANGE_TOPIC, MqConstants.Canvas.ROUTING_ENTITY_CHANGE);
        } catch (Exception ex) {
            log.error("消息重试处理异常: {}", ex.getMessage(), ex);
        }
    }
}
