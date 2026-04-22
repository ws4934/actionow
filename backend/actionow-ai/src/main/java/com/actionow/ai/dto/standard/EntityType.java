package com.actionow.ai.dto.standard;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 实体类型枚举
 * 对应 actionow-script 模块的实体类型
 *
 * @author Actionow
 */
public enum EntityType {

    /**
     * 剧本
     */
    SCRIPT("SCRIPT", "剧本"),

    /**
     * 剧集
     */
    EPISODE("EPISODE", "剧集"),

    /**
     * 角色
     */
    CHARACTER("CHARACTER", "角色"),

    /**
     * 场景
     */
    SCENE("SCENE", "场景"),

    /**
     * 道具
     */
    PROP("PROP", "道具"),

    /**
     * 风格
     */
    STYLE("STYLE", "风格"),

    /**
     * 分镜
     */
    STORYBOARD("STORYBOARD", "分镜");

    private final String code;
    private final String description;

    EntityType(String code, String description) {
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
    public static EntityType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (EntityType type : values()) {
            if (type.code.equalsIgnoreCase(code) || type.name().equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }
}
