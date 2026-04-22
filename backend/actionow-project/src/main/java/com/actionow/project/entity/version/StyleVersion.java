package com.actionow.project.entity.version;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 风格版本实体
 * 存储风格的历史版本快照
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_style_version", autoResultMap = true)
public class StyleVersion extends EntityVersion {

    /**
     * 风格ID (主实体引用)
     */
    @TableField("style_id")
    private String styleId;

    // ========== 以下为风格业务字段快照 ==========

    /**
     * 作用域: WORKSPACE-工作空间级, SCRIPT-剧本级
     */
    private String scope;

    /**
     * 关联的剧本ID
     */
    @TableField("script_id")
    private String scriptId;

    /**
     * 风格名称
     */
    private String name;

    /**
     * 风格描述
     */
    private String description;

    /**
     * 固定描述词（AI生成时使用）
     */
    @TableField("fixed_desc")
    private String fixedDesc;

    /**
     * 风格参数 (JSON)
     */
    @TableField(value = "style_params", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> styleParams;

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
