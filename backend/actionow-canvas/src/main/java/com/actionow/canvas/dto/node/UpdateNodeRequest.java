package com.actionow.canvas.dto.node;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 更新画布节点请求
 *
 * @author Actionow
 */
@Data
public class UpdateNodeRequest {

    /**
     * 节点ID（批量更新时必填）
     */
    private String nodeId;

    /**
     * 实体版本ID
     */
    private String entityVersionId;

    /**
     * X 坐标位置
     */
    private BigDecimal positionX;

    /**
     * Y 坐标位置
     */
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
}
