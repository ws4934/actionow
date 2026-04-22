package com.actionow.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 素材信息
 * 用于 AI 模块处理素材输入
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AssetInfo {

    /**
     * 素材ID
     */
    private String id;

    /**
     * 素材名称
     */
    private String name;

    /**
     * 素材类型
     * IMAGE, VIDEO, AUDIO, DOCUMENT, MODEL, OTHER
     */
    private String assetType;

    /**
     * 文件存储键
     */
    private String fileKey;

    /**
     * 文件访问URL
     */
    private String fileUrl;

    /**
     * 缩略图URL
     */
    private String thumbnailUrl;

    /**
     * MIME类型
     */
    private String mimeType;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 元数据信息
     * 包含：width, height, duration, format 等
     */
    private Map<String, Object> metaInfo;

    /**
     * 从 Map 构建 AssetInfo
     * 支持两种格式：
     * 1. 扁平格式：所有字段在顶层
     * 2. 嵌套格式：详细字段在 detail 对象中
     *
     * @param map 素材数据Map
     * @return AssetInfo
     */
    @SuppressWarnings("unchecked")
    public static AssetInfo fromMap(Map<String, Object> map) {
        if (map == null) {
            return null;
        }

        // 检查是否有嵌套的 detail 对象
        Map<String, Object> detail = null;
        if (map.get("detail") instanceof Map) {
            detail = (Map<String, Object>) map.get("detail");
        }

        // 优先从 detail 中获取字段，否则从顶层获取
        String fileUrl = detail != null ? (String) detail.get("fileUrl") : (String) map.get("fileUrl");
        String fileKey = detail != null ? (String) detail.get("fileKey") : (String) map.get("fileKey");
        String thumbnailUrl = detail != null ? (String) detail.get("thumbnailUrl") : (String) map.get("thumbnailUrl");
        String mimeType = detail != null ? (String) detail.get("mimeType") : (String) map.get("mimeType");
        String assetType = detail != null ? (String) detail.get("assetType") : (String) map.get("assetType");
        Object fileSizeObj = detail != null ? detail.get("fileSize") : map.get("fileSize");
        Map<String, Object> metaInfo = detail != null
                ? (Map<String, Object>) detail.get("metaInfo")
                : (Map<String, Object>) map.get("metaInfo");

        // 确保 fileUrl 有 https:// 前缀
        if (fileUrl != null && !fileUrl.isBlank() && !fileUrl.startsWith("http://") && !fileUrl.startsWith("https://")) {
            fileUrl = "https://" + fileUrl;
        }
        if (thumbnailUrl != null && !thumbnailUrl.isBlank() && !thumbnailUrl.startsWith("http://") && !thumbnailUrl.startsWith("https://")) {
            thumbnailUrl = "https://" + thumbnailUrl;
        }

        return AssetInfo.builder()
                .id((String) map.get("id"))
                .name((String) map.get("name"))
                .assetType(assetType)
                .fileKey(fileKey)
                .fileUrl(fileUrl)
                .thumbnailUrl(thumbnailUrl)
                .mimeType(mimeType)
                .fileSize(fileSizeObj instanceof Number ? ((Number) fileSizeObj).longValue() : null)
                .metaInfo(metaInfo)
                .build();
    }

    /**
     * 是否为图片类型
     */
    public boolean isImage() {
        return "IMAGE".equalsIgnoreCase(assetType);
    }

    /**
     * 是否为视频类型
     */
    public boolean isVideo() {
        return "VIDEO".equalsIgnoreCase(assetType);
    }

    /**
     * 是否为音频类型
     */
    public boolean isAudio() {
        return "AUDIO".equalsIgnoreCase(assetType);
    }

    /**
     * 是否为文档类型
     */
    public boolean isDocument() {
        return "DOCUMENT".equalsIgnoreCase(assetType);
    }

    /**
     * 获取图片宽度
     */
    public Integer getWidth() {
        if (metaInfo != null && metaInfo.get("width") instanceof Number) {
            return ((Number) metaInfo.get("width")).intValue();
        }
        return null;
    }

    /**
     * 获取图片高度
     */
    public Integer getHeight() {
        if (metaInfo != null && metaInfo.get("height") instanceof Number) {
            return ((Number) metaInfo.get("height")).intValue();
        }
        return null;
    }

    /**
     * 获取视频/音频时长（秒）
     */
    public Integer getDuration() {
        if (metaInfo != null && metaInfo.get("duration") instanceof Number) {
            return ((Number) metaInfo.get("duration")).intValue();
        }
        return null;
    }
}
