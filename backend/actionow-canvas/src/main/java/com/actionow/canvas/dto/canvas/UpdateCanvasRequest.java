package com.actionow.canvas.dto.canvas;

import lombok.Data;

import java.util.Map;

/**
 * 更新画布请求
 *
 * @author Actionow
 */
@Data
public class UpdateCanvasRequest {

    /**
     * 画布名称
     */
    private String name;

    /**
     * 画布描述
     */
    private String description;

    /**
     * 布局策略: GRID, TREE, FORCE
     */
    private String layoutStrategy;

    /**
     * 是否锁定
     */
    private Boolean locked;

    /**
     * 视口状态
     */
    private Map<String, Object> viewport;

    /**
     * 画布设置
     */
    private Map<String, Object> settings;
}
