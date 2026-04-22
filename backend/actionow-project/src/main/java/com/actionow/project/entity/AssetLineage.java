package com.actionow.project.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 素材溯源实体（AI生成关系）
 * 记录AI生成素材的输入来源
 *
 * @author Actionow
 */
@Data
@TableName(value = "t_asset_lineage", autoResultMap = true)
public class AssetLineage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 工作空间ID
     */
    @TableField("workspace_id")
    private String workspaceId;

    /**
     * 生成的目标素材ID
     */
    @TableField("output_asset_id")
    private String outputAssetId;

    /**
     * 输入来源类型: ASSET, CHARACTER, SCENE, PROP, STYLE, PROMPT_TEMPLATE, STORYBOARD
     */
    @TableField("input_type")
    private String inputType;

    /**
     * 输入来源ID
     */
    @TableField("input_id")
    private String inputId;

    /**
     * 输入在生成中的角色: SOURCE_IMAGE, REFERENCE, STYLE_REF, SUBJECT, BACKGROUND, PROMPT
     */
    @TableField("input_role")
    private String inputRole;

    /**
     * 排序（多输入时的顺序）
     */
    private Integer sequence;

    /**
     * 关联的AI任务ID
     */
    @TableField("task_id")
    private String taskId;

    /**
     * 扩展信息（如权重、参数等）
     */
    @TableField(value = "extra_info", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraInfo;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 创建人ID
     */
    @TableField(fill = FieldFill.INSERT)
    private String createdBy;
}
