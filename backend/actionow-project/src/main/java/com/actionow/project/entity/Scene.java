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
 * 场景实体
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_scene", autoResultMap = true)
public class Scene extends TenantBaseEntity {

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
     * 结构:
     * {
     *   // 环境设定
     *   "timeOfDay": "DAY",              // 时间: DAWN/DAY/DUSK/NIGHT
     *   "weather": "sunny",              // 天气: sunny/cloudy/rainy/snowy/foggy/stormy
     *   "season": "summer",              // 季节: spring/summer/autumn/winter
     *   "location": "urban street",      // 地点类型描述
     *
     *   // 氛围设定
     *   "lighting": "natural",           // 光线: natural/artificial/dim/bright/dramatic
     *   "colorTone": "warm",             // 色调: warm/cool/neutral/vibrant/muted
     *   "mood": "peaceful",              // 氛围: peaceful/tense/romantic/mysterious/chaotic
     *
     *   // 空间特征
     *   "perspective": "eye-level",      // 透视: eye-level/bird-eye/worm-eye
     *   "depth": "medium",               // 纵深: shallow/medium/deep
     *   "keyElements": ["coffee tables", "large windows"],
     *
     *   // AI生成参考
     *   "artStyle": "anime background",
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
