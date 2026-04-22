package com.actionow.common.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分片上传完成请求
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultipartCompleteRequest {

    /**
     * OSS 分片上传 ID
     */
    @NotBlank(message = "uploadId不能为空")
    private String uploadId;

    /**
     * 文件存储 Key
     */
    @NotBlank(message = "fileKey不能为空")
    private String fileKey;

    /**
     * 各分片信息
     */
    @NotEmpty(message = "分片信息不能为空")
    private List<PartInfo> parts;

    /**
     * 分片信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PartInfo {
        /**
         * 分片编号
         */
        private Integer partNumber;

        /**
         * ETag（上传响应中返回）
         */
        private String etag;
    }
}
