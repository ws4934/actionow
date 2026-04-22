package com.actionow.ai.dto.standard;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * AI 响应输出类型枚举
 * 定义了所有支持的输出类型
 *
 * @author Actionow
 */
public enum OutputType {

    /**
     * 单个媒体文件输出
     */
    MEDIA_SINGLE("MEDIA_SINGLE", "单个媒体"),

    /**
     * 批量媒体文件输出
     */
    MEDIA_BATCH("MEDIA_BATCH", "批量媒体"),

    /**
     * 剧本实体输出
     */
    ENTITY_SCRIPT("ENTITY_SCRIPT", "剧本"),

    /**
     * 剧集实体输出
     */
    ENTITY_EPISODE("ENTITY_EPISODE", "剧集"),

    /**
     * 角色实体输出（可批量）
     */
    ENTITY_CHARACTER("ENTITY_CHARACTER", "角色"),

    /**
     * 场景实体输出（可批量）
     */
    ENTITY_SCENE("ENTITY_SCENE", "场景"),

    /**
     * 道具实体输出（可批量）
     */
    ENTITY_PROP("ENTITY_PROP", "道具"),

    /**
     * 风格实体输出（可批量）
     */
    ENTITY_STYLE("ENTITY_STYLE", "风格"),

    /**
     * 分镜实体输出（可批量）
     */
    ENTITY_STORYBOARD("ENTITY_STORYBOARD", "分镜"),

    /**
     * 混合实体输出（解析出多种类型）
     */
    ENTITY_MIXED("ENTITY_MIXED", "混合实体"),

    /**
     * 纯文本内容输出
     */
    TEXT_CONTENT("TEXT_CONTENT", "文本内容");

    private final String code;
    private final String description;

    OutputType(String code, String description) {
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
    public static OutputType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (OutputType type : values()) {
            if (type.code.equalsIgnoreCase(code) || type.name().equalsIgnoreCase(code)) {
                return type;
            }
        }
        // 常用别名
        if ("TEXT".equalsIgnoreCase(code)) {
            return TEXT_CONTENT;
        }
        return null;
    }

    /**
     * 是否为媒体类型
     */
    public boolean isMediaType() {
        return this == MEDIA_SINGLE || this == MEDIA_BATCH;
    }

    /**
     * 是否为实体类型
     */
    public boolean isEntityType() {
        return this.name().startsWith("ENTITY_");
    }
}
