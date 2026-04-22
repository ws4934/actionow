package com.actionow.canvas.dto.view;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 创建自定义视图请求
 *
 * @author Actionow
 */
@Data
public class CreateViewRequest {

    /**
     * 画布ID
     */
    @NotBlank(message = "画布ID不能为空")
    private String canvasId;

    /**
     * 视图键（唯一标识）
     */
    @NotBlank(message = "视图键不能为空")
    private String viewKey;

    /**
     * 视图名称
     */
    @NotBlank(message = "视图名称不能为空")
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
     * 布局策略
     */
    private String layoutStrategy;

    /**
     * 排序序号
     */
    private Integer sequence;
}
