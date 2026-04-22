package com.actionow.canvas.dto.node;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 引用已有实体创建节点请求
 * 当实体已存在时使用，只需要创建画布节点引用
 *
 * @author Actionow
 */
@Data
public class CreateNodeReferenceRequest {

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

    /**
     * 实体ID（必填）
     */
    @NotBlank(message = "实体ID不能为空")
    private String entityId;

    /**
     * 实体版本ID（可选，指定特定版本）
     */
    private String entityVersionId;

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

    /**
     * 转换为统一的 CreateNodeRequest
     */
    public CreateNodeRequest toCreateNodeRequest() {
        CreateNodeRequest request = new CreateNodeRequest();
        request.setCanvasId(this.canvasId);
        request.setEntityType(this.entityType);
        request.setEntityId(this.entityId);
        request.setEntityVersionId(this.entityVersionId);
        request.setPositionX(this.positionX);
        request.setPositionY(this.positionY);
        request.setWidth(this.width);
        request.setHeight(this.height);
        request.setCollapsed(this.collapsed);
        request.setLocked(this.locked);
        request.setZIndex(this.zIndex);
        request.setStyle(this.style);
        return request;
    }
}
