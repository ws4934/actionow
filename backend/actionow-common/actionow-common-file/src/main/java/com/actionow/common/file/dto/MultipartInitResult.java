package com.actionow.common.file.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分片上传初始化结果
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultipartInitResult {

    /**
     * OSS 分片上传 ID
     */
    private String uploadId;

    /**
     * 文件存储 Key
     */
    private String fileKey;

    /**
     * 各分片的预签名上传 URL
     */
    private List<PartUploadUrl> partUrls;

    /**
     * 过期时间（ISO 8601 格式）
     */
    private String expiresAt;

    /**
     * 分片上传 URL 信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PartUploadUrl {
        /**
         * 分片编号（从 1 开始）
         */
        private Integer partNumber;

        /**
         * 预签名上传 URL
         */
        private String uploadUrl;
    }
}
