package com.actionow.common.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 预签名上传请求
 * 用于客户端直传 OSS
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresignedUploadRequest {

    /**
     * 工作空间 ID
     */
    @NotBlank(message = "工作空间ID不能为空")
    private String workspaceId;

    /**
     * 原始文件名
     */
    @NotBlank(message = "文件名不能为空")
    private String fileName;

    /**
     * MIME 类型
     */
    @NotBlank(message = "MIME类型不能为空")
    private String mimeType;

    /**
     * 文件大小（字节）
     */
    @NotNull(message = "文件大小不能为空")
    @Positive(message = "文件大小必须为正数")
    private Long fileSize;

    /**
     * 文件类型分类（IMAGE, VIDEO, AUDIO, DOCUMENT, MODEL）
     * 用于决定存储路径，可选
     */
    private String fileType;

    /**
     * 自定义存储路径前缀（可选）
     * 如果提供则使用自定义路径，否则根据文件类型自动生成
     */
    private String customPath;
}
