package com.actionow.ai.dto.standard;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 媒体类型枚举
 * 对应 Asset.assetType
 *
 * @author Actionow
 */
public enum MediaType {

    /**
     * 图片
     */
    IMAGE("IMAGE", "图片"),

    /**
     * 视频
     */
    VIDEO("VIDEO", "视频"),

    /**
     * 音频
     */
    AUDIO("AUDIO", "音频"),

    /**
     * 文档
     */
    DOCUMENT("DOCUMENT", "文档");

    private final String code;
    private final String description;

    MediaType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    @JsonCreator
    public static MediaType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (MediaType type : values()) {
            if (type.code.equalsIgnoreCase(code) || type.name().equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 根据 MIME 类型推断媒体类型
     */
    public static MediaType fromMimeType(String mimeType) {
        if (mimeType == null) {
            return null;
        }
        String lower = mimeType.toLowerCase();
        if (lower.startsWith("image/")) {
            return IMAGE;
        } else if (lower.startsWith("video/")) {
            return VIDEO;
        } else if (lower.startsWith("audio/")) {
            return AUDIO;
        } else if (lower.startsWith("application/pdf") || lower.startsWith("text/")) {
            return DOCUMENT;
        }
        return null;
    }
}
