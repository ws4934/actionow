package com.actionow.canvas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.actionow.common.data.entity.TenantBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 画布节点实体
 * 存储画布中实体节点的位置和状态信息
 * 实体数据通过 entityType + entityId 引用 actionow-project 中的实际数据
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_canvas_node", autoResultMap = true)
public class CanvasNode extends TenantBaseEntity {

    /**
     * 所属画布ID
     */
    @TableField("canvas_id")
    private String canvasId;

    /**
     * 实体类型: SCRIPT, EPISODE, STORYBOARD, CHARACTER, SCENE, PROP, STYLE, ASSET
     */
    @TableField("entity_type")
    private String entityType;

    /**
     * 实体ID
     */
    @TableField("entity_id")
    private String entityId;

    /**
     * 实体版本ID（可选，指定特定版本）
     */
    @TableField("entity_version_id")
    private String entityVersionId;

    /**
     * 节点层级: SCRIPT, EPISODE, STORYBOARD, CHARACTER, SCENE, PROP, ASSET
     * 用于视图过滤和层级展示
     */
    private String layer;

    /**
     * 父节点ID，用于层级关系
     */
    @TableField("parent_node_id")
    private String parentNodeId;

    /**
     * X 坐标位置
     */
    @TableField("position_x")
    private BigDecimal positionX;

    /**
     * Y 坐标位置
     */
    @TableField("position_y")
    private BigDecimal positionY;

    /**
     * 节点宽度
     */
    private BigDecimal width;

    /**
     * 节点高度
     */
    private BigDecimal height;

    /**
     * 是否折叠
     */
    private Boolean collapsed;

    /**
     * 是否锁定
     */
    private Boolean locked;

    /**
     * 是否隐藏
     */
    private Boolean hidden;

    /**
     * 层级顺序（z-index）
     */
    @TableField("z_index")
    private Integer zIndex;

    /**
     * 节点样式 (JSON)
     * 格式: { "backgroundColor": "#fff", "borderColor": "#ddd", "borderRadius": 8 }
     */
    @TableField(value = "style", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> style;

    /**
     * 缓存的实体名称（冗余存储，用于减少查询）
     */
    @TableField("cached_name")
    private String cachedName;

    /**
     * 缓存的缩略图URL（冗余存储，用于快速渲染）
     */
    @TableField("cached_thumbnail_url")
    private String cachedThumbnailUrl;

    /**
     * 缓存的实体状态
     */
    @TableField("cached_status")
    private String cachedStatus;
}
