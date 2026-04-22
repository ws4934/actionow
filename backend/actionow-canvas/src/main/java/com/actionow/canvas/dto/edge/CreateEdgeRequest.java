package com.actionow.canvas.dto.edge;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 创建画布边请求
 *
 * @author Actionow
 */
@Data
public class CreateEdgeRequest {

    /**
     * 画布ID
     */
    @NotBlank(message = "画布ID不能为空")
    private String canvasId;

    /**
     * 源实体类型
     */
    @NotBlank(message = "源实体类型不能为空")
    private String sourceType;

    /**
     * 源实体ID
     */
    @NotBlank(message = "源实体ID不能为空")
    private String sourceId;

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
    @NotBlank(message = "目标实体类型不能为空")
    private String targetType;

    /**
     * 目标实体ID
     */
    @NotBlank(message = "目标实体ID不能为空")
    private String targetId;

    /**
     * 目标实体版本ID
     */
    private String targetVersionId;

    /**
     * 目标节点连接点
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
}
