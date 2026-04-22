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
 * 道具实体
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_prop", autoResultMap = true)
public class Prop extends TenantBaseEntity {

    /**
     * 作用域: SYSTEM-系统预置, WORKSPACE-工作空间级, SCRIPT-剧本级
     */
    private String scope;

    /**
     * 关联的剧本ID（scope为SCRIPT时必填）
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
     * 结构:
     * {
     *   // 物理属性
     *   "material": "metal",             // 材质: metal/wood/glass/plastic/fabric/stone
     *   "texture": "smooth",             // 质感: smooth/rough/glossy/matte
     *   "color": "silver",               // 主色
     *   "secondaryColor": null,          // 次要色
     *
     *   // 尺寸描述
     *   "size": "handheld",              // 尺寸: tiny/small/handheld/medium/large/huge
     *   "shape": "rectangular",          // 形状描述
     *
     *   // 状态/特征
     *   "condition": "new",              // 状态: new/used/worn/damaged/antique
     *   "distinguishingFeatures": ["engraved pattern", "glowing runes"],
     *
     *   // 功能性
     *   "functional": true,              // 是否可交互
     *   "specialEffects": null,          // 特效描述（如发光、冒烟）
     *
     *   // AI生成参考
     *   "artStyle": "anime prop",
     *   "referenceImageIds": []
     * }
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
