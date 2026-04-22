package com.actionow.canvas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.actionow.common.data.entity.TenantBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 画布边/连线实体
 * 存储画布中节点之间的连线关系及样式
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_canvas_edge", autoResultMap = true)
public class CanvasEdge extends TenantBaseEntity {

    /**
     * 所属画布ID
     */
    @TableField("canvas_id")
    private String canvasId;

    /**
     * 源实体类型: SCRIPT, EPISODE, STORYBOARD, CHARACTER, SCENE, PROP, STYLE, ASSET
     */
    @TableField("source_type")
    private String sourceType;

    /**
     * 源实体ID
     */
    @TableField("source_id")
    private String sourceId;

    /**
     * 源实体版本ID（可选，指定特定版本）
     */
    @TableField("source_version_id")
    private String sourceVersionId;

    /**
     * 源节点连接点: top, bottom, left, right
     */
    @TableField("source_handle")
    private String sourceHandle;

    /**
     * 目标实体类型: SCRIPT, EPISODE, STORYBOARD, CHARACTER, SCENE, PROP, STYLE, ASSET
     */
    @TableField("target_type")
    private String targetType;

    /**
     * 目标实体ID
     */
    @TableField("target_id")
    private String targetId;

    /**
     * 目标实体版本ID（可选，指定特定版本）
     */
    @TableField("target_version_id")
    private String targetVersionId;

    /**
     * 目标节点连接点: top, bottom, left, right
     */
    @TableField("target_handle")
    private String targetHandle;

    /**
     * 关系类型: has_episode, has_character, appears_in, takes_place_in, uses, styled_by, has_asset, relates_to 等
     */
    @TableField("relation_type")
    private String relationType;

    /**
     * 关系显示标签（用于画布渲染）
     */
    @TableField("relation_label")
    private String relationLabel;

    /**
     * 使用说明/描述
     */
    private String description;

    /**
     * 线条样式 (JSON)
     * 格式: { "strokeColor": "#666", "strokeWidth": 2, "strokeStyle": "solid", "animated": false, "arrowType": "arrow" }
     */
    @TableField(value = "line_style", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> lineStyle;

    /**
     * 路径类型: straight, bezier, step, smoothstep
     */
    @TableField("path_type")
    private String pathType;

    /**
     * 排序序号
     */
    private Integer sequence;

    /**
     * 扩展信息
     */
    @TableField(value = "extra_info", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraInfo;
}
