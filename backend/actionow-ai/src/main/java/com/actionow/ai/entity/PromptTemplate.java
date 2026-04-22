package com.actionow.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 提示词模板实体
 *
 * @author Actionow
 */
@Data
@TableName(value = "prompt_template", autoResultMap = true)
public class PromptTemplate implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 模板ID
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 工作空间ID（系统模板为空）
     */
    private String workspaceId;

    /**
     * 模板名称
     */
    private String name;

    /**
     * 模板描述
     */
    private String description;

    /**
     * 生成类型（IMAGE/VIDEO/AUDIO/TEXT）
     */
    private String type;

    /**
     * 模板内容（支持 {{variable}} 变量）
     */
    private String content;

    /**
     * 负面提示词（图像生成用）
     */
    private String negativePrompt;

    /**
     * 变量定义列表
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> variables;

    /**
     * 默认参数
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> defaultParams;

    /**
     * 作用域（SYSTEM/WORKSPACE）
     */
    private String scope;

    /**
     * 状态（ACTIVE/INACTIVE）
     */
    private String status;

    /**
     * 使用次数
     */
    private Long useCount;

    /**
     * 创建者ID
     */
    private String creatorId;

    /**
     * 版本号
     */
    @Version
    private Integer version;

    /**
     * 软删除标记
     */
    @TableLogic
    private Integer deleted;

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
}
