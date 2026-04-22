package com.actionow.ai.dto.standard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 媒体项 DTO
 * 表示单个媒体文件的完整信息
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaItem {

    // ==================== 必填字段 ====================

    /**
     * 文件 URL（外部 URL 或 base64 数据）
     * 必填
     */
    private String fileUrl;

    /**
     * OSS 对象存储路径（用于后续访问和管理）
     * 如 ai-outputs/image/xxx.jpg
     */
    private String fileKey;

    /**
     * MIME 类型
     * 必填，如 image/png, video/mp4, audio/mp3
     */
    private String mimeType;

    // ==================== 推荐字段 ====================

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 缩略图 URL（视频/图片）
     */
    private String thumbnailUrl;

    // ==================== 可选元数据（对应 Asset.metaInfo）====================

    /**
     * 图片/视频宽度（像素）
     */
    private Integer width;

    /**
     * 图片/视频高度（像素）
     */
    private Integer height;

    /**
     * 视频/音频时长（秒）
     */
    private Integer duration;

    /**
     * 文件格式（如 PNG, MP4, MP3）
     */
    private String format;

    /**
     * 音视频比特率（bps）
     */
    private Integer bitrate;

    /**
     * 编解码器（如 H.264, AAC）
     */
    private String codec;

    /**
     * 视频帧率（fps）
     */
    private Integer frameRate;

    /**
     * 音频采样率（Hz）
     */
    private Integer sampleRate;

    /**
     * 音频声道数
     */
    private Integer channels;

    /**
     * 是否为 Base64 数据
     */
    @Builder.Default
    private Boolean base64 = false;

    // ==================== AI 生成信息（对应 Asset.extraInfo）====================

    /**
     * 使用的模型 ID
     */
    private String modelId;

    /**
     * 模型版本
     */
    private String modelVersion;

    /**
     * 随机种子
     */
    private String seed;

    /**
     * 生成参数快照
     */
    private Map<String, Object> params;

    // ==================== 工具方法 ====================

    /**
     * 构建 metaInfo Map（用于存储到 Asset.metaInfo）
     */
    public Map<String, Object> toMetaInfo() {
        Map<String, Object> meta = new java.util.HashMap<>();
        if (width != null) meta.put("width", width);
        if (height != null) meta.put("height", height);
        if (duration != null) meta.put("duration", duration);
        if (format != null) meta.put("format", format);
        if (bitrate != null) meta.put("bitrate", bitrate);
        if (codec != null) meta.put("codec", codec);
        if (frameRate != null) meta.put("frameRate", frameRate);
        if (sampleRate != null) meta.put("sampleRate", sampleRate);
        if (channels != null) meta.put("channels", channels);
        return meta;
    }

    /**
     * 构建 extraInfo Map（用于存储到 Asset.extraInfo）
     */
    public Map<String, Object> toExtraInfo() {
        Map<String, Object> extra = new java.util.HashMap<>();
        if (modelId != null) extra.put("modelId", modelId);
        if (modelVersion != null) extra.put("modelVersion", modelVersion);
        if (seed != null) extra.put("seed", seed);
        if (params != null) extra.put("params", params);
        return extra;
    }
}
