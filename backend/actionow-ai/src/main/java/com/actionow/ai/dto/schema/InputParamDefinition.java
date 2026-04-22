package com.actionow.ai.dto.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 输入参数定义
 * 用于描述模型提供商的输入参数结构
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InputParamDefinition {

    /**
     * 参数名称（API字段名）
     */
    private String name;

    /**
     * 参数类型
     * 基础: TEXT, TEXTAREA, NUMBER, BOOLEAN, SELECT
     * 文件: IMAGE, VIDEO, AUDIO, DOCUMENT
     * 文件列表: IMAGE_LIST, VIDEO_LIST, AUDIO_LIST, DOCUMENT_LIST
     * 实体引用: CHARACTER, SCENE, PROP, STYLE, STORYBOARD
     * 实体列表: CHARACTER_LIST, SCENE_LIST, PROP_LIST, STYLE_LIST, STORYBOARD_LIST
     */
    private String type;

    /**
     * 显示标签（中文）
     */
    private String label;

    /**
     * 显示标签（英文）
     */
    private String labelEn;

    /**
     * 参数描述
     */
    private String description;

    /**
     * 是否必填
     */
    private Boolean required;

    /**
     * 默认值
     */
    private Object defaultValue;

    /**
     * 占位提示
     */
    private String placeholder;

    /**
     * 所属分组名称
     */
    private String group;

    /**
     * 排序权重（越小越靠前）
     */
    private Integer order;

    /**
     * 是否可见
     */
    private Boolean visible;

    /**
     * 是否禁用
     */
    private Boolean disabled;

    /**
     * 前端组件名称
     */
    private String component;

    /**
     * 组件属性（透传给前端组件）
     */
    private Object componentProps;

    /**
     * SELECT 类型的选项列表
     */
    private List<SelectOption> options;

    /**
     * 文件类型配置
     */
    private InputFileConfig fileConfig;

    /**
     * 校验规则
     */
    private InputParamValidation validation;

    /**
     * 依赖条件
     */
    private DependsOnCondition dependsOn;

    /**
     * 条件作用类型
     * visibility（默认）, hidden, disabled, required
     */
    private String effectType;

    /**
     * 帮助提示
     */
    private String helpTip;

    /**
     * 帮助链接
     */
    private String helpUrl;

    /**
     * 获取参数类型枚举
     */
    public InputParamType getTypeEnum() {
        return InputParamType.fromCode(this.type);
    }

    /**
     * 获取作用类型枚举
     */
    public EffectType getEffectTypeEnum() {
        return EffectType.fromCode(this.effectType);
    }
}
