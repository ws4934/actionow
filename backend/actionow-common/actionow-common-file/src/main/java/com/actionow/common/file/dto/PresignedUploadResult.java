package com.actionow.common.file.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 预签名上传结果
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresignedUploadResult {

    /**
     * 预签名上传 URL
     */
    private String uploadUrl;

    /**
     * HTTP 方法（PUT）
     */
    private String method;

    /**
     * 请求头（Content-Type 等）
     */
    private Map<String, String> headers;

    /**
     * 文件存储 Key
     */
    private String fileKey;

    /**
     * 过期时间（ISO 8601 格式）
     */
    private String expiresAt;
}
