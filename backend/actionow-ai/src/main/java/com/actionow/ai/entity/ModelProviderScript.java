package com.actionow.ai.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.actionow.common.data.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 模型提供商脚本实体（Groovy 脚本 & 定价规则）
 * 对应 t_model_provider_script 表
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_model_provider_script", autoResultMap = true)
public class ModelProviderScript extends BaseEntity {

    /**
     * 关联的模型提供商ID
     */
    private String providerId;

    /**
     * 请求构建Groovy脚本（内联）
     */
    private String requestBuilderScript;

    /**
     * 响应映射Groovy脚本（内联）
     */
    private String responseMapperScript;

    /**
     * 自定义逻辑Groovy脚本（内联）
     */
    private String customLogicScript;

    /**
     * 请求构建模板ID引用
     */
    private String requestBuilderTemplateId;

    /**
     * 响应映射模板ID引用
     */
    private String responseMapperTemplateId;

    /**
     * 自定义逻辑模板ID引用
     */
    private String customLogicTemplateId;

    /**
     * 动态积分计算规则（JSON）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> pricingRules;

    /**
     * 动态积分计算Groovy脚本
     */
    private String pricingScript;
}
