package com.actionow.canvas.dto.view;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 更新视图请求
 *
 * @author Actionow
 */
@Data
public class UpdateViewRequest {

    /**
     * 视图名称
     */
    private String name;

    /**
     * 视图图标
     */
    private String icon;

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
     * 是否设为默认视图
     */
    private Boolean isDefault;
}
