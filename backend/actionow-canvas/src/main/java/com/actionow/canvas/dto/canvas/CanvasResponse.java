package com.actionow.canvas.dto.canvas;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 画布响应
 * 统一主画布模型：1 Script = 1 Canvas
 *
 * @author Actionow
 */
@Data
public class CanvasResponse {

    /**
     * 画布ID
     */
    private String id;

    /**
     * 关联的剧本ID
     */
    private String scriptId;

    /**
     * 画布名称
     */
    private String name;

    /**
     * 画布描述
     */
    private String description;

    /**
     * 视口状态
     */
    private Map<String, Object> viewport;

    /**
     * 布局策略
     */
    private String layoutStrategy;

    /**
     * 是否锁定
     */
    private Boolean locked;

    /**
     * 画布设置
     */
    private Map<String, Object> settings;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 节点数量
     */
    private Integer nodeCount;

    /**
     * 边数量
     */
    private Integer edgeCount;

    /**
     * 视图列表
     */
    private List<CanvasViewResponse> views;

    /**
     * 画布视图响应
     */
    @Data
    public static class CanvasViewResponse {
        private String id;
        private String viewKey;
        private String name;
        private String icon;
        private String viewType;
        private String rootEntityType;
        private List<String> visibleEntityTypes;
        private Integer sequence;
        private Boolean isDefault;
    }
}
