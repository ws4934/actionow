package com.actionow.task.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 更新素材生成状态请求
 * Task 服务调用 Asset 服务
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateGenerationStatusRequest {

    /**
     * 生成状态: GENERATING/COMPLETED/FAILED
     */
    private String status;

    /**
     * AI 任务 ID
     */
    private String taskId;

    /**
     * 模型提供商 ID
     */
    private String providerId;

    /**
     * 生成的文件 URL（成功时）
     */
    private String fileUrl;

    /**
     * 文件存储路径（OSS key）
     */
    private String fileKey;

    /**
     * 缩略图 URL
     */
    private String thumbnailUrl;

    /**
     * MIME 类型
     */
    private String mimeType;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 元数据信息
     */
    private Map<String, Object> metaInfo;

    /**
     * 额外信息（prompt 等参数）
     */
    private Map<String, Object> extraInfo;

    /**
     * 错误信息（失败时）
     */
    private String errorMessage;
}
