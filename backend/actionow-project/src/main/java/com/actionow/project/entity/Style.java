package com.actionow.project.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.actionow.common.data.entity.TenantBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;
import java.time.LocalDateTime;

/**
 * 风格实体
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_style", autoResultMap = true)
public class Style extends TenantBaseEntity {

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
     * 包含：画风、色彩、纹理等AI绘图参数
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

    /**
     * 当前版本记录ID
     */
    @TableField("current_version_id")
    private String currentVersionId;

    /**
     * 业务版本号 (从1开始递增)
     */
    @TableField("version_number")
    private Integer versionNumber;

    @TableField("published_at")
    private LocalDateTime publishedAt;

    @TableField("published_by")
    private String publishedBy;

    @TableField("publish_note")
    private String publishNote;
}
