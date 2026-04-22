package com.actionow.common.file.service;

import com.actionow.common.file.event.FileUploadedEvent;

/**
 * 文件事件发布者接口
 * 用于发布文件相关事件（上传完成、缩略图生成等）
 *
 * @author Actionow
 */
public interface FileEventPublisher {

    /**
     * 发布文件上传完成事件
     *
     * @param event 事件
     */
    void publishFileUploaded(FileUploadedEvent event);

    /**
     * 发布异步缩略图生成请求
     *
     * @param fileKey      文件 Key
     * @param mimeType     MIME 类型
     * @param workspaceId  工作空间 ID
     * @param businessId   业务 ID
     * @param businessType 业务类型
     */
    void requestThumbnailGeneration(String fileKey, String mimeType, String workspaceId,
                                    String businessId, String businessType);
}
