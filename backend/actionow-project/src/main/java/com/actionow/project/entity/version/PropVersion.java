package com.actionow.project.entity.version;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 道具版本实体
 * 存储道具的历史版本快照
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_prop_version", autoResultMap = true)
public class PropVersion extends EntityVersion {

    /**
     * 道具ID (主实体引用)
     */
    @TableField("prop_id")
    private String propId;

    // ========== 以下为道具业务字段快照 ==========

    /**
     * 作用域: SYSTEM-系统预置, WORKSPACE-工作空间级, SCRIPT-剧本级
     */
    private String scope;

    /**
     * 关联的剧本ID
     */
    @TableField("script_id")
    private String scriptId;

    /**
     * 道具名称
     */
    private String name;

    /**
     * 道具描述
     */
    private String description;

    /**
     * 固定描述词（AI生成时使用）
     */
    @TableField("fixed_desc")
    private String fixedDesc;

    /**
     * 道具类型
     */
    @TableField("prop_type")
    private String propType;

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
