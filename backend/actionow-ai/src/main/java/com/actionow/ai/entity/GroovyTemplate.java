package com.actionow.ai.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.actionow.common.data.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * Groovy脚本模板实体
 * 存储可复用的Groovy脚本模板
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_groovy_template", autoResultMap = true)
public class GroovyTemplate extends BaseEntity {

    /**
     * 模板名称
     */
    private String name;

    /**
     * 模板描述
     */
    private String description;

    /**
     * 模板类型
     * REQUEST_BUILDER - 请求构建器
     * RESPONSE_MAPPER - 响应映射器
     * CUSTOM_LOGIC - 自定义逻辑
     */
    private String templateType;

    /**
     * 适用的生成类型
     * IMAGE, VIDEO, AUDIO, TEXT, ALL
     */
    private String generationType;

    /**
     * 脚本内容
     */
    private String scriptContent;

    /**
     * 脚本版本
     */
    private String scriptVersion;

    /**
     * 是否系统模板（只读）
     */
    private Boolean isSystem;

    /**
     * 示例输入参数
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> exampleInput;

    /**
     * 示例输出结果
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> exampleOutput;

    /**
     * 文档说明
     */
    private String documentation;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 模板类型枚举
     */
    public enum TemplateType {
        REQUEST_BUILDER,
        RESPONSE_MAPPER,
        CUSTOM_LOGIC
    }

    /**
     * 生成类型枚举
     */
    public enum GenerationType {
        IMAGE,
        VIDEO,
        AUDIO,
        TEXT,
        ALL
    }
}
