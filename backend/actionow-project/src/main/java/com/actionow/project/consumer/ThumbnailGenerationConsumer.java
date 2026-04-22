package com.actionow.project.consumer;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.actionow.common.core.context.UserContext;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.file.event.FileUploadedEvent;
import com.actionow.common.file.service.FileStorageService;
import com.actionow.common.mq.constant.MqConstants;
import com.actionow.common.mq.consumer.ConsumerRetryHelper;
import com.actionow.common.mq.message.MessageWrapper;
import com.actionow.project.entity.Asset;
import com.actionow.project.mapper.AssetMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;

/**
 * 缩略图异步生成消费者
 * 从 RabbitMQ 队列消费文件上传事件，异步生成缩略图
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ThumbnailGenerationConsumer {

    private static final int MAX_RETRY_COUNT = 3;

    private final FileStorageService fileStorageService;
    private final AssetMapper assetMapper;
    private final ConsumerRetryHelper retryHelper;

    /**
     * 处理文件上传事件 - 异步生成缩略图
     */
    @RabbitListener(queues = MqConstants.File.QUEUE)
    public void handleFileEvent(MessageWrapper<FileUploadedEvent> message,
                                Channel channel,
                                @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        FileUploadedEvent event = message.getPayload();
        String fileKey = event.getFileKey();
        String mimeType = event.getMimeType();
        String businessId = event.getBusinessId();
        String businessType = event.getBusinessType();
        int retryCount = message.getRetryCount() != null ? message.getRetryCount() : 0;

        log.debug("收到文件事件: messageType={}, fileKey={}, businessType={}, businessId={}, retryCount={}",
                message.getMessageType(), fileKey, businessType, businessId, retryCount);

        // 设置租户上下文
        setTenantContext(message);

        try {
            // 检查是否需要生成缩略图
            if (!Boolean.TRUE.equals(event.getNeedThumbnail())) {
                log.debug("文件不需要生成缩略图: fileKey={}", fileKey);
                acknowledgeMessage(channel, deliveryTag);
                return;
            }

            // 生成缩略图
            String thumbnailUrl = fileStorageService.generateThumbnail(fileKey, mimeType);

            if (!StringUtils.hasText(thumbnailUrl)) {
                log.debug("不支持的缩略图类型或生成失败: fileKey={}, mimeType={}", fileKey, mimeType);
                acknowledgeMessage(channel, deliveryTag);
                return;
            }

            log.info("缩略图生成成功: fileKey={}, thumbnailUrl={}", fileKey, thumbnailUrl);

            // 根据业务类型更新对应实体
            if ("ASSET".equals(businessType) && StringUtils.hasText(businessId)) {
                updateAssetThumbnail(businessId, thumbnailUrl);
            }
            // 可扩展其他业务类型

            acknowledgeMessage(channel, deliveryTag);

        } catch (Exception e) {
            log.error("处理文件事件失败: fileKey={}, retryCount={}, error={}", fileKey, retryCount, e.getMessage(), e);
            rejectMessage(channel, deliveryTag, message);
        } finally {
            // 清除租户上下文
            UserContextHolder.clear();
        }
    }

    /**
     * 设置租户上下文
     */
    private void setTenantContext(MessageWrapper<?> message) {
        UserContext context = new UserContext();
        context.setTenantSchema(message.getTenantSchema());
        context.setWorkspaceId(message.getWorkspaceId());
        context.setUserId(message.getSenderId());
        UserContextHolder.setContext(context);
        log.debug("设置消费者租户上下文: tenantSchema={}, workspaceId={}",
                message.getTenantSchema(), message.getWorkspaceId());
    }

    /**
     * 更新素材缩略图URL
     */
    private void updateAssetThumbnail(String assetId, String thumbnailUrl) {
        try {
            LambdaUpdateWrapper<Asset> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(Asset::getId, assetId)
                    .set(Asset::getThumbnailUrl, thumbnailUrl);

            int updated = assetMapper.update(null, wrapper);
            if (updated > 0) {
                log.info("素材缩略图更新成功: assetId={}, thumbnailUrl={}", assetId, thumbnailUrl);
            } else {
                log.warn("素材缩略图更新失败（素材不存在）: assetId={}", assetId);
            }
        } catch (Exception e) {
            log.error("更新素材缩略图失败: assetId={}, error={}", assetId, e.getMessage(), e);
        }
    }

    /**
     * 确认消息
     */
    private void acknowledgeMessage(Channel channel, long deliveryTag) {
        try {
            channel.basicAck(deliveryTag, false);
        } catch (IOException e) {
            log.error("确认消息失败: deliveryTag={}", deliveryTag, e);
        }
    }

    /**
     * 拒绝消息：通过重新发布消息递增 retryCount，超限后转入 DLQ
     */
    private void rejectMessage(Channel channel, long deliveryTag, MessageWrapper<?> message) {
        try {
            retryHelper.retryOrDlq(message, channel, deliveryTag, MAX_RETRY_COUNT,
                    MqConstants.EXCHANGE_DIRECT, MqConstants.File.ROUTING_UPLOADED);
        } catch (IOException e) {
            log.error("拒绝消息失败: deliveryTag={}", deliveryTag, e);
        }
    }
}
