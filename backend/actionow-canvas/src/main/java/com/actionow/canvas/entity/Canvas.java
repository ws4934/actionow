package com.actionow.canvas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.actionow.common.data.entity.TenantBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 画布实体
 * 统一主画布模型：1 Script = 1 Canvas
 * 通过视图（CanvasView）实现不同维度的过滤展示
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_canvas", autoResultMap = true)
public class Canvas extends TenantBaseEntity {

    /**
     * 关联的剧本ID（1:1 关系）
     */
    @TableField("script_id")
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
     * 视口状态 (JSON)
     * 格式: { "x": 0, "y": 0, "zoom": 1 }
     */
    @TableField(value = "viewport", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> viewport;

    /**
     * 布局策略: GRID, TREE, FORCE
     */
    @TableField("layout_strategy")
    private String layoutStrategy;

    /**
     * 是否锁定
     */
    private Boolean locked;

    /**
     * 画布设置 (JSON)
     */
    @TableField(value = "settings", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> settings;
}
