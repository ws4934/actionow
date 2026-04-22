package com.actionow.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 创建/更新系统配置请求
 *
 * @author Actionow
 */
@Data
public class SystemConfigRequest {

    /**
     * 配置键
     */
    @NotBlank(message = "配置键不能为空")
    private String configKey;

    /**
     * 配置值
     */
    @NotBlank(message = "配置值不能为空")
    private String configValue;

    /**
     * 配置类型
     */
    private String configType;

    /**
     * 作用域
     */
    private String scope;

    /**
     * 作用域ID
     */
    private String scopeId;

    /**
     * 配置描述
     */
    private String description;

    /**
     * 默认值
     */
    private String defaultValue;

    /**
     * 值类型
     */
    private String valueType;

    /**
     * 验证规则
     */
    private Map<String, Object> validation;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 是否为敏感配置
     */
    private Boolean sensitive;

    /**
     * 所属模块
     */
    private String module;

    /**
     * 分组名
     */
    private String groupName;

    /**
     * 前端展示名
     */
    private String displayName;

    /**
     * 排序序号
     */
    private Integer sortOrder;
}
