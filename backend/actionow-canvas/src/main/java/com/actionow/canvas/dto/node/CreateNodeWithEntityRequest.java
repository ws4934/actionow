package com.actionow.canvas.dto.node;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 新建实体并创建节点请求
 * 当需要同时创建实体和画布节点时使用
 *
 * @author Actionow
 */
@Data
public class CreateNodeWithEntityRequest {

    /**
     * 画布ID
     */
    @NotBlank(message = "画布ID不能为空")
    private String canvasId;

    /**
     * 实体类型
     */
    @NotBlank(message = "实体类型不能为空")
    private String entityType;

    // ==================== 实体创建字段 ====================

    /**
     * 实体名称（必填）
     */
    @NotBlank(message = "实体名称不能为空")
    private String entityName;

    /**
     * 实体描述
     */
    private String entityDescription;

    /**
     * 实体作用域（用于 CHARACTER, SCENE, PROP, STYLE）
     * WORKSPACE / SCRIPT
     */
    private String entityScope;

    /**
     * 关联的剧本ID（SCRIPT作用域时必填；EPISODE/STORYBOARD创建时需要）
     */
    private String scriptId;

    /**
     * 关联的剧集ID（STORYBOARD创建时需要）
     */
    private String episodeId;

    /**
     * 实体扩展数据（特定实体类型的额外字段）
     */
    private Map<String, Object> entityExtraData;

    // ==================== 节点布局字段 ====================

    /**
     * X 坐标位置
     */
    @NotNull(message = "X坐标不能为空")
    private BigDecimal positionX;

    /**
     * Y 坐标位置
     */
    @NotNull(message = "Y坐标不能为空")
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
     * 层级顺序
     */
    private Integer zIndex;

    /**
     * 节点样式
     */
    private Map<String, Object> style;

    // ==================== 边关联字段（可选）====================
    // 如果提供以下字段，则在创建节点后自动创建从源节点到新节点的边

    /**
     * 源节点实体类型（用于创建边）
     */
    private String sourceNodeType;

    /**
     * 源节点实体ID（用于创建边）
     */
    private String sourceNodeId;

    /**
     * 源节点连接点位置
     */
    private String sourceHandle;

    /**
     * 目标节点（新节点）连接点位置
     */
    private String targetHandle;

    /**
     * 关系类型（可选，不传则自动推断）
     */
    private String relationType;

    /**
     * 关系标签
     */
    private String relationLabel;

    /**
     * 边的线条样式
     */
    private Map<String, Object> edgeLineStyle;

    /**
     * 转换为统一的 CreateNodeRequest
     */
    public CreateNodeRequest toCreateNodeRequest() {
        CreateNodeRequest request = new CreateNodeRequest();
        request.setCanvasId(this.canvasId);
        request.setEntityType(this.entityType);
        // entityId is null for new entity mode
        request.setEntityName(this.entityName);
        request.setEntityDescription(this.entityDescription);
        request.setEntityScope(this.entityScope);
        request.setScriptId(this.scriptId);
        request.setEpisodeId(this.episodeId);
        request.setEntityExtraData(this.entityExtraData);
        request.setPositionX(this.positionX);
        request.setPositionY(this.positionY);
        request.setWidth(this.width);
        request.setHeight(this.height);
        request.setCollapsed(this.collapsed);
        request.setLocked(this.locked);
        request.setZIndex(this.zIndex);
        request.setStyle(this.style);
        // 边相关字段
        request.setSourceNodeType(this.sourceNodeType);
        request.setSourceNodeId(this.sourceNodeId);
        request.setSourceHandle(this.sourceHandle);
        request.setTargetHandle(this.targetHandle);
        request.setRelationType(this.relationType);
        request.setRelationLabel(this.relationLabel);
        request.setEdgeLineStyle(this.edgeLineStyle);
        return request;
    }
}
