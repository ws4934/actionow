package com.actionow.project.dto.asset;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 素材上传初始化响应
 * 包含：素材记录信息 + 预签名上传 URL
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetUploadInitResponse {

    /**
     * 素材 ID
     */
    private String assetId;

    /**
     * 素材名称
     */
    private String name;

    /**
     * 素材类型
     */
    private String assetType;

    /**
     * 上传状态
     */
    private String uploadStatus;

    // ========== 预签名上传信息 ==========

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

    /**
     * 上传状态常量
     */
    public static final class UploadStatus {
        public static final String PENDING = "PENDING";       // 待上传
        public static final String UPLOADING = "UPLOADING";   // 上传中
        public static final String COMPLETED = "COMPLETED";   // 已完成
        public static final String FAILED = "FAILED";         // 失败
    }
}
