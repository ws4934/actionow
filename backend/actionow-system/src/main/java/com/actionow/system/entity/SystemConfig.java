package com.actionow.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 系统配置实体
 *
 * @author Actionow
 */
@Data
@TableName(value = "t_system_config", autoResultMap = true)
public class SystemConfig {

    /**
     * 配置ID
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 配置键
     */
    private String configKey;

    /**
     * 配置值
     */
    private String configValue;

    /**
     * 配置类型（SYSTEM/FEATURE/LIMIT/...）
     */
    private String configType;

    /**
     * 作用域（GLOBAL/WORKSPACE/USER）
     */
    private String scope;

    /**
     * 作用域ID（工作空间ID或用户ID）
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
     * 值类型（STRING/INTEGER/BOOLEAN/JSON）
     */
    private String valueType;

    /**
     * 验证规则（JSON Schema）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> validation;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 是否为敏感配置（API Key等，响应时掩码）
     */
    private Boolean sensitive;

    /**
     * 所属模块（user/agent/task/ai/gateway/project/billing/canvas/mq/system）
     */
    private String module;

    /**
     * 分组名（同一模块内的子分类）
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

    /**
     * 创建者ID
     */
    private String createdBy;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 是否删除 (0=未删除, 1=已删除)
     */
    @TableLogic
    private Integer deleted;

    /**
     * 乐观锁版本号
     */
    @Version
    private Integer version;
}
