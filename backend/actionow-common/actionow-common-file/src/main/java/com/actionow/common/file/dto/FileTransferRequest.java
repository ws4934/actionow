package com.actionow.common.file.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件转存请求
 * 用于从外部 URL 下载文件并转存到本地 OSS
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileTransferRequest {

    /**
     * 工作空间 ID
     */
    @NotBlank(message = "工作空间ID不能为空")
    private String workspaceId;

    /**
     * 源文件 URL
     */
    @NotBlank(message = "源文件URL不能为空")
    private String sourceUrl;

    /**
     * MIME 类型（可选，如果不提供则从响应头获取）
     */
    private String mimeType;

    /**
     * 文件类型分类（IMAGE, VIDEO, AUDIO, DOCUMENT, MODEL）
     * 用于决定存储路径，可选
     */
    private String fileType;

    /**
     * 自定义存储路径前缀（可选）
     */
    private String customPath;

    /**
     * 是否生成缩略图（仅图片有效）
     */
    @Builder.Default
    private Boolean generateThumbnail = true;

    /**
     * 是否异步生成缩略图
     */
    @Builder.Default
    private Boolean asyncThumbnail = false;
}
