package com.actionow.ai.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.actionow.common.data.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

/**
 * 模型提供商 I/O Schema 实体
 * 对应 t_model_provider_schema 表
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_model_provider_schema", autoResultMap = true)
public class ModelProviderSchema extends BaseEntity {

    /**
     * 关联的模型提供商ID
     */
    private String providerId;

    /**
     * 输入参数定义列表
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> inputSchema;

    /**
     * 输入参数分组列表
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> inputGroups;

    /**
     * 互斥参数组列表
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> exclusiveGroups;

    /**
     * 输出参数Schema
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> outputSchema;
}
