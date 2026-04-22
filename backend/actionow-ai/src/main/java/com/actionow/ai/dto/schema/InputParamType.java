package com.actionow.ai.dto.schema;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 输入参数类型枚举
 *
 * @author Actionow
 */
@Getter
@AllArgsConstructor
public enum InputParamType {

    // 基础类型
    TEXT("TEXT", "Input", "单行文本"),
    TEXTAREA("TEXTAREA", "Textarea", "多行文本"),
    NUMBER("NUMBER", "InputNumber", "数字"),
    BOOLEAN("BOOLEAN", "Switch", "布尔值"),
    SELECT("SELECT", "Select", "下拉选择"),

    // 单个文件类型
    IMAGE("IMAGE", "ImageUpload", "单个图片"),
    VIDEO("VIDEO", "VideoUpload", "单个视频"),
    AUDIO("AUDIO", "AudioUpload", "单个音频"),
    DOCUMENT("DOCUMENT", "DocumentUpload", "单个文档"),

    // 文件列表类型
    TEXT_LIST("TEXT_LIST", "TagInput", "文本列表"),
    NUMBER_LIST("NUMBER_LIST", "NumberListInput", "数字列表"),
    IMAGE_LIST("IMAGE_LIST", "ImageListUpload", "图片列表"),
    VIDEO_LIST("VIDEO_LIST", "VideoListUpload", "视频列表"),
    AUDIO_LIST("AUDIO_LIST", "AudioListUpload", "音频列表"),
    DOCUMENT_LIST("DOCUMENT_LIST", "DocumentListUpload", "文档列表"),

    // 实体引用类型（单个）
    CHARACTER("CHARACTER", "EntitySelect", "角色引用"),
    SCENE("SCENE", "EntitySelect", "场景引用"),
    PROP("PROP", "EntitySelect", "道具引用"),
    STYLE("STYLE", "EntitySelect", "风格引用"),
    STORYBOARD("STORYBOARD", "EntitySelect", "分镜引用"),

    // 实体引用类型（列表）
    CHARACTER_LIST("CHARACTER_LIST", "EntityMultiSelect", "角色列表引用"),
    SCENE_LIST("SCENE_LIST", "EntityMultiSelect", "场景列表引用"),
    PROP_LIST("PROP_LIST", "EntityMultiSelect", "道具列表引用"),
    STYLE_LIST("STYLE_LIST", "EntityMultiSelect", "风格列表引用"),
    STORYBOARD_LIST("STORYBOARD_LIST", "EntityMultiSelect", "分镜列表引用");

    /**
     * 类型代码
     */
    private final String code;

    /**
     * 默认前端组件名称
     */
    private final String defaultComponent;

    /**
     * 类型描述
     */
    private final String description;

    /**
     * 是否为文件类型
     */
    public boolean isFileType() {
        return this == IMAGE || this == VIDEO || this == AUDIO || this == DOCUMENT
                || this == IMAGE_LIST || this == VIDEO_LIST || this == AUDIO_LIST || this == DOCUMENT_LIST;
    }

    /**
     * 是否为实体引用类型
     */
    public boolean isEntityType() {
        return this == CHARACTER || this == SCENE || this == PROP || this == STYLE || this == STORYBOARD
                || this == CHARACTER_LIST || this == SCENE_LIST || this == PROP_LIST
                || this == STYLE_LIST || this == STORYBOARD_LIST;
    }

    /**
     * 是否为列表类型
     */
    public boolean isListType() {
        return this == TEXT_LIST || this == NUMBER_LIST
                || this == IMAGE_LIST || this == VIDEO_LIST || this == AUDIO_LIST || this == DOCUMENT_LIST
                || this == CHARACTER_LIST || this == SCENE_LIST || this == PROP_LIST
                || this == STYLE_LIST || this == STORYBOARD_LIST;
    }

    /**
     * 获取实体批量查询请求键名（用于 /entities/batch-query）
     */
    public String getEntityBatchKey() {
        return switch (this) {
            case CHARACTER, CHARACTER_LIST -> "characterIds";
            case SCENE, SCENE_LIST -> "sceneIds";
            case PROP, PROP_LIST -> "propIds";
            case STYLE, STYLE_LIST -> "styleIds";
            case STORYBOARD, STORYBOARD_LIST -> "storyboardIds";
            default -> null;
        };
    }

    /**
     * 获取实体批量查询响应键名
     */
    public String getEntityResponseKey() {
        return switch (this) {
            case CHARACTER, CHARACTER_LIST -> "characters";
            case SCENE, SCENE_LIST -> "scenes";
            case PROP, PROP_LIST -> "props";
            case STYLE, STYLE_LIST -> "styles";
            case STORYBOARD, STORYBOARD_LIST -> "storyboards";
            default -> null;
        };
    }

    /**
     * 根据代码获取枚举
     */
    public static InputParamType fromCode(String code) {
        for (InputParamType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return TEXT;
    }
}
