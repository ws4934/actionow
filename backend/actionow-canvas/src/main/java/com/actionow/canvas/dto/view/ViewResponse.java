package com.actionow.canvas.dto.view;

import com.actionow.canvas.entity.CanvasView;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 视图响应
 *
 * @author Actionow
 */
@Data
public class ViewResponse {

    /**
     * 视图ID
     */
    private String id;

    /**
     * 画布ID
     */
    private String canvasId;

    /**
     * 视图键
     */
    private String viewKey;

    /**
     * 视图名称
     */
    private String name;

    /**
     * 视图图标
     */
    private String icon;

    /**
     * 视图类型 PRESET/CUSTOM
     */
    private String viewType;

    /**
     * 根实体类型
     */
    private String rootEntityType;

    /**
     * 可见实体类型列表
     */
    private List<String> visibleEntityTypes;

    /**
     * 可见层级列表
     */
    private List<String> visibleLayers;

    /**
     * 筛选配置
     */
    private Map<String, Object> filterConfig;

    /**
     * 视口状态
     */
    private Map<String, Object> viewport;

    /**
     * 布局策略
     */
    private String layoutStrategy;

    /**
     * 排序序号
     */
    private Integer sequence;

    /**
     * 是否默认视图
     */
    private Boolean isDefault;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 从实体转换
     */
    public static ViewResponse fromEntity(CanvasView view) {
        ViewResponse response = new ViewResponse();
        response.setId(view.getId());
        response.setCanvasId(view.getCanvasId());
        response.setViewKey(view.getViewKey());
        response.setName(view.getName());
        response.setIcon(view.getIcon());
        response.setViewType(view.getViewType());
        response.setRootEntityType(view.getRootEntityType());

        // 转换数组字段（entity 现在是 List<String>）
        response.setVisibleEntityTypes(view.getVisibleEntityTypes());
        response.setVisibleLayers(view.getVisibleLayers());

        response.setFilterConfig(view.getFilterConfig());
        response.setViewport(view.getViewport());
        response.setLayoutStrategy(view.getLayoutStrategy());
        response.setSequence(view.getSequence());
        response.setIsDefault(view.getIsDefault());
        response.setCreatedAt(view.getCreatedAt());
        response.setUpdatedAt(view.getUpdatedAt());
        return response;
    }
}
