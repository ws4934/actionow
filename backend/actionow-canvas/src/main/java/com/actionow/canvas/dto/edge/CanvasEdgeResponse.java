package com.actionow.canvas.dto.edge;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 画布边响应
 *
 * @author Actionow
 */
@Data
public class CanvasEdgeResponse {

    /**
     * 边ID
     */
    private String id;

    /**
     * 画布ID
     */
    private String canvasId;

    /**
     * 源实体类型
     */
    private String sourceType;

    /**
     * 源实体ID
     */
    private String sourceId;

    /**
     * 源节点ID（用于画布渲染）
     */
    private String sourceNodeId;

    /**
     * 源实体版本ID
     */
    private String sourceVersionId;

    /**
     * 源节点连接点
     */
    private String sourceHandle;

    /**
     * 目标实体类型
     */
    private String targetType;

    /**
     * 目标实体ID
     */
    private String targetId;

    /**
     * 目标节点ID（用于画布渲染）
     */
    private String targetNodeId;

    /**
     * 目标实体版本ID
     */
    private String targetVersionId;

    /**
     * 目标节点连接点
     */
    private String targetHandle;

    /**
     * 关系类型
     */
    private String relationType;

    /**
     * 关系标签
     */
    private String relationLabel;

    /**
     * 描述
     */
    private String description;

    /**
     * 线条样式
     */
    private Map<String, Object> lineStyle;

    /**
     * 路径类型
     */
    private String pathType;

    /**
     * 排序序号
     */
    private Integer sequence;

    /**
     * 扩展信息
     */
    private Map<String, Object> extraInfo;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
