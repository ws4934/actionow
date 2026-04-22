package com.actionow.canvas.service;

import com.actionow.canvas.dto.EntityChangeMessage;
import com.actionow.common.mq.message.MessageWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * 消息聚合服务
 * 对短时间内的大量消息进行聚合处理，提高处理效率
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageAggregationService {

    private final CanvasSyncService canvasSyncService;

    /**
     * 待处理消息队列
     * key: entityId, value: 最新的消息
     */
    private final ConcurrentHashMap<String, MessageWrapper<EntityChangeMessage>> pendingMessages = new ConcurrentHashMap<>();

    /**
     * 待确认的消息队列
     */
    private final ConcurrentLinkedQueue<PendingAck> pendingAcks = new ConcurrentLinkedQueue<>();

    /**
     * 聚合窗口时间（毫秒）
     */
    private static final long AGGREGATION_WINDOW_MS = 200;

    /**
     * 最大批处理数量
     */
    private static final int MAX_BATCH_SIZE = 50;

    /**
     * 添加消息到聚合队列
     * 如果同一实体有多条消息，只保留最新的
     *
     * @param message 消息包装
     * @param deliveryTag 投递标签
     * @param channel 通道
     */
    public void addMessage(MessageWrapper<EntityChangeMessage> message,
                           long deliveryTag,
                           com.rabbitmq.client.Channel channel) {
        EntityChangeMessage payload = message.getPayload();
        String key = payload.getEntityType() + ":" + payload.getEntityId();

        // 合并消息：只保留最新的
        pendingMessages.put(key, message);

        // 记录待确认信息
        pendingAcks.offer(new PendingAck(deliveryTag, channel, System.currentTimeMillis()));

        log.debug("消息已加入聚合队列: key={}, queueSize={}", key, pendingMessages.size());
    }

    /**
     * 定时处理聚合消息
     * 每200ms执行一次
     */
    @Scheduled(fixedDelay = AGGREGATION_WINDOW_MS)
    public void processAggregatedMessages() {
        if (pendingMessages.isEmpty()) {
            return;
        }

        // 取出当前所有待处理消息
        List<MessageWrapper<EntityChangeMessage>> batch = new ArrayList<>();
        List<String> keysToProcess = new ArrayList<>();

        for (Map.Entry<String, MessageWrapper<EntityChangeMessage>> entry : pendingMessages.entrySet()) {
            if (batch.size() >= MAX_BATCH_SIZE) {
                break;
            }
            keysToProcess.add(entry.getKey());
            batch.add(entry.getValue());
        }

        // 移除已取出的消息
        keysToProcess.forEach(pendingMessages::remove);

        if (batch.isEmpty()) {
            return;
        }

        log.info("开始处理聚合消息: batchSize={}", batch.size());

        // 按变更类型分组处理
        Map<String, List<MessageWrapper<EntityChangeMessage>>> groupedByChangeType = batch.stream()
                .collect(Collectors.groupingBy(m -> m.getPayload().getChangeType()));

        // 处理更新消息（最常见的场景）
        List<MessageWrapper<EntityChangeMessage>> updateMessages = groupedByChangeType.get("UPDATED");
        if (updateMessages != null && !updateMessages.isEmpty()) {
            processUpdateBatch(updateMessages);
        }

        // 处理创建消息
        List<MessageWrapper<EntityChangeMessage>> createMessages = groupedByChangeType.get("CREATED");
        if (createMessages != null && !createMessages.isEmpty()) {
            for (MessageWrapper<EntityChangeMessage> msg : createMessages) {
                processCreateMessage(msg);
            }
        }

        // 处理删除消息
        List<MessageWrapper<EntityChangeMessage>> deleteMessages = groupedByChangeType.get("DELETED");
        if (deleteMessages != null && !deleteMessages.isEmpty()) {
            for (MessageWrapper<EntityChangeMessage> msg : deleteMessages) {
                processDeleteMessage(msg);
            }
        }

        // 批量确认消息
        ackPendingMessages();

        log.info("聚合消息处理完成: batchSize={}", batch.size());
    }

    /**
     * 批量处理更新消息
     */
    private void processUpdateBatch(List<MessageWrapper<EntityChangeMessage>> messages) {
        for (MessageWrapper<EntityChangeMessage> message : messages) {
            try {
                EntityChangeMessage payload = message.getPayload();
                String name = extractName(payload.getEntityData());
                String thumbnailUrl = extractThumbnailUrl(payload.getEntityData());

                canvasSyncService.handleEntityUpdated(
                        payload.getEntityType(),
                        payload.getEntityId(),
                        name,
                        thumbnailUrl
                );
            } catch (Exception e) {
                log.error("处理更新消息失败: entityId={}, error={}",
                        message.getPayload().getEntityId(), e.getMessage());
            }
        }
    }

    /**
     * 处理单条创建消息
     */
    private void processCreateMessage(MessageWrapper<EntityChangeMessage> message) {
        try {
            EntityChangeMessage payload = message.getPayload();
            String name = extractName(payload.getEntityData());

            canvasSyncService.handleEntityCreated(
                    payload.getEntityType(),
                    payload.getEntityId(),
                    payload.getScriptId(),
                    payload.getParentEntityType(),
                    payload.getParentEntityId(),
                    message.getWorkspaceId(),
                    name
            );
        } catch (Exception e) {
            log.error("处理创建消息失败: entityId={}, error={}",
                    message.getPayload().getEntityId(), e.getMessage());
        }
    }

    /**
     * 处理单条删除消息
     */
    private void processDeleteMessage(MessageWrapper<EntityChangeMessage> message) {
        try {
            EntityChangeMessage payload = message.getPayload();
            canvasSyncService.handleEntityDeleted(payload.getEntityType(), payload.getEntityId());
        } catch (Exception e) {
            log.error("处理删除消息失败: entityId={}, error={}",
                    message.getPayload().getEntityId(), e.getMessage());
        }
    }

    /**
     * 批量确认消息
     */
    private void ackPendingMessages() {
        long now = System.currentTimeMillis();
        PendingAck ack;
        while ((ack = pendingAcks.poll()) != null) {
            // 只确认超过聚合窗口的消息
            if (now - ack.timestamp >= AGGREGATION_WINDOW_MS) {
                try {
                    ack.channel.basicAck(ack.deliveryTag, false);
                } catch (Exception e) {
                    log.warn("确认消息失败: deliveryTag={}, error={}", ack.deliveryTag, e.getMessage());
                }
            } else {
                // 还没到时间的放回队列
                pendingAcks.offer(ack);
                break;
            }
        }
    }

    /**
     * 从实体数据中提取名称
     */
    private String extractName(Map<String, Object> entityData) {
        if (entityData == null) return null;
        Object name = entityData.get("name");
        if (name != null) return name.toString();
        Object title = entityData.get("title");
        if (title != null) return title.toString();
        return null;
    }

    /**
     * 从实体数据中提取缩略图URL
     */
    private String extractThumbnailUrl(Map<String, Object> entityData) {
        if (entityData == null) return null;
        Object thumbnail = entityData.get("thumbnailUrl");
        if (thumbnail != null) return thumbnail.toString();
        Object cover = entityData.get("coverUrl");
        if (cover != null) return cover.toString();
        return null;
    }

    /**
     * 待确认消息记录
     */
    private record PendingAck(long deliveryTag, com.rabbitmq.client.Channel channel, long timestamp) {
    }
}
