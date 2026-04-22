package com.actionow.project.entity.version;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 场景版本实体
 * 存储场景的历史版本快照
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_scene_version", autoResultMap = true)
public class SceneVersion extends EntityVersion {

    /**
     * 场景ID (主实体引用)
     */
    @TableField("scene_id")
    private String sceneId;

    // ========== 以下为场景业务字段快照 ==========

    /**
     * 作用域: SYSTEM-系统预置, WORKSPACE-工作空间级, SCRIPT-剧本级
     */
    private String scope;

    /**
     * 场景类型: INTERIOR-室内, EXTERIOR-室外, MIXED-混合
     */
    @TableField("scene_type")
    private String sceneType;

    /**
     * 关联的剧本ID（scope为SCRIPT时必填）
     */
    @TableField("script_id")
    private String scriptId;

    /**
     * 场景名称
     */
    private String name;

    /**
     * 场景描述
     */
    private String description;

    /**
     * 固定描述词（AI生成时使用）
     */
    @TableField("fixed_desc")
    private String fixedDesc;

    /**
     * 外观数据 (JSON)
     */
    @TableField(value = "appearance_data", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> appearanceData;

    /**
     * 封面素材ID
     */
    @TableField("cover_asset_id")
    private String coverAssetId;

    /**
     * 扩展信息
     */
    @TableField(value = "extra_info", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraInfo;
}
