package com.actionow.project.publisher;

import com.actionow.common.file.event.FileUploadedEvent;
import com.actionow.common.file.service.FileEventPublisher;
import com.actionow.common.mq.constant.MqConstants;
import com.actionow.common.mq.message.MessageWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ 文件事件发布者
 * 实现 FileEventPublisher 接口，通过 RabbitMQ 发布文件相关事件
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitFileEventPublisher implements FileEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publishFileUploaded(FileUploadedEvent event) {
        MessageWrapper<FileUploadedEvent> wrapper = MessageWrapper.wrap(
                MqConstants.File.MSG_UPLOADED, event);

        rabbitTemplate.convertAndSend(
                MqConstants.EXCHANGE_DIRECT,
                MqConstants.File.ROUTING_UPLOADED,
                wrapper
        );

        log.debug("文件上传事件已发布: fileKey={}, businessId={}, businessType={}",
                event.getFileKey(), event.getBusinessId(), event.getBusinessType());
    }

    @Override
    public void requestThumbnailGeneration(String fileKey, String mimeType, String workspaceId,
                                           String businessId, String businessType) {
        FileUploadedEvent event = FileUploadedEvent.builder()
                .fileKey(fileKey)
                .mimeType(mimeType)
                .workspaceId(workspaceId)
                .businessId(businessId)
                .businessType(businessType)
                .needThumbnail(true)
                .build();

        MessageWrapper<FileUploadedEvent> wrapper = MessageWrapper.wrap(
                MqConstants.File.MSG_THUMBNAIL_REQUEST, event);

        rabbitTemplate.convertAndSend(
                MqConstants.EXCHANGE_DIRECT,
                MqConstants.File.ROUTING_THUMBNAIL_REQUEST,
                wrapper
        );

        log.debug("缩略图生成请求已发布: fileKey={}, businessId={}, businessType={}",
                fileKey, businessId, businessType);
    }
}
