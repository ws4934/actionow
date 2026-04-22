package com.actionow.common.file.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 缩略图生成完成事件
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThumbnailGeneratedEvent {

    /**
     * 事件 ID
     */
    private String eventId;

    /**
     * 原文件 Key
     */
    private String originalFileKey;

    /**
     * 缩略图 Key
     */
    private String thumbnailKey;

    /**
     * 缩略图 URL
     */
    private String thumbnailUrl;

    /**
     * 工作空间 ID
     */
    private String workspaceId;

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 失败原因（如果失败）
     */
    private String errorMessage;

    /**
     * 事件时间
     */
    private LocalDateTime timestamp;

    /**
     * 关联的业务 ID
     */
    private String businessId;

    /**
     * 业务类型
     */
    private String businessType;
}
