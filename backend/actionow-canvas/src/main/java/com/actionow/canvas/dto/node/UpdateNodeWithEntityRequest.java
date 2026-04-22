package com.actionow.canvas.dto.node;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 更新节点并同步实体请求
 * 当需要同时更新画布节点和对应业务实体时使用
 *
 * @author Actionow
 */
@Data
public class UpdateNodeWithEntityRequest {

    /**
     * 节点ID（批量更新时必填，单个更新时从路径获取）
     */
    private String nodeId;

    // ==================== 节点布局字段 ====================

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

    // ==================== 实体更新字段 ====================

    /**
     * 实体名称
     */
    private String entityName;

    /**
     * 实体描述
     */
    private String entityDescription;

    /**
     * 实体缩略图URL
     */
    private String entityThumbnailUrl;

    /**
     * 实体扩展数据（特定实体类型的额外字段）
     */
    private Map<String, Object> entityExtraData;

    /**
     * 是否需要更新实体
     */
    public boolean hasEntityUpdateInfo() {
        return entityName != null || entityDescription != null
                || entityThumbnailUrl != null || entityExtraData != null;
    }

    /**
     * 转换为基础的 UpdateNodeRequest
     */
    public UpdateNodeRequest toUpdateNodeRequest() {
        UpdateNodeRequest request = new UpdateNodeRequest();
        request.setNodeId(this.nodeId);
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
