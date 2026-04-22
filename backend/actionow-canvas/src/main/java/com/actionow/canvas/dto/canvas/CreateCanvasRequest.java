package com.actionow.canvas.dto.canvas;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 创建画布请求
 * 统一主画布模型：1 Script = 1 Canvas
 *
 * @author Actionow
 */
@Data
public class CreateCanvasRequest {

    /**
     * 关联的剧本ID（必填，1:1 关系）
     */
    @NotBlank(message = "剧本ID不能为空")
    private String scriptId;

    /**
     * 画布名称（可选，默认使用剧本名称）
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
     * 画布设置
     */
    private Map<String, Object> settings;
}
