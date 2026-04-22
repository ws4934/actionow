package com.actionow.common.file.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文件上传完成事件
 * 用于异步缩略图生成等后续处理
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadedEvent {

    /**
     * 事件 ID
     */
    private String eventId;

    /**
     * 文件 Key
     */
    private String fileKey;

    /**
     * 文件 URL
     */
    private String fileUrl;

    /**
     * 工作空间 ID
     */
    private String workspaceId;

    /**
     * MIME 类型
     */
    private String mimeType;

    /**
     * 文件大小
     */
    private Long fileSize;

    /**
     * 是否需要生成缩略图
     */
    private Boolean needThumbnail;

    /**
     * 事件时间
     */
    private LocalDateTime timestamp;

    /**
     * 关联的业务 ID（可选）
     */
    private String businessId;

    /**
     * 业务类型（可选，如 ASSET, PROJECT 等）
     */
    private String businessType;
}
