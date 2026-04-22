package com.actionow.project.feign;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件转存请求
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
    private String workspaceId;

    /**
     * 源文件 URL
     */
    private String sourceUrl;

    /**
     * MIME 类型
     */
    private String mimeType;

    /**
     * 文件类型分类
     */
    private String fileType;

    /**
     * 自定义存储路径前缀
     */
    private String customPath;

    /**
     * 是否生成缩略图
     */
    @Builder.Default
    private Boolean generateThumbnail = true;
}
