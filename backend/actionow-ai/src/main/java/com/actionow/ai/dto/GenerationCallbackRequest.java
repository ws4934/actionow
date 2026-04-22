package com.actionow.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * AI 生成回调请求
 * AI 服务调用 Asset 服务的回调接口
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerationCallbackRequest {

    /**
     * AI 任务 ID
     */
    @NotBlank(message = "任务ID不能为空")
    private String taskId;

    /**
     * 生成状态: COMPLETED/FAILED
     */
    @NotBlank(message = "状态不能为空")
    private String status;

    /**
     * 生成的文件 URL（成功时必填）
     */
    private String fileUrl;

    /**
     * 文件存储路径（OSS key）
     */
    private String fileKey;

    /**
     * 缩略图 URL（可选）
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
     * 元数据信息（尺寸、时长等）
     */
    private Map<String, Object> metaInfo;

    /**
     * 错误信息（失败时填写）
     */
    private String errorMessage;
}
