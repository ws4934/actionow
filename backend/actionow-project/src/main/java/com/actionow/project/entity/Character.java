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
 * 角色实体
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_character", autoResultMap = true)
public class Character extends TenantBaseEntity {

    /**
     * 作用域: SYSTEM-系统公共库, WORKSPACE-工作空间级, SCRIPT-剧本级
     */
    private String scope;

    /**
     * 关联的剧本ID（scope为SCRIPT时必填）
     */
    @TableField("script_id")
    private String scriptId;

    /**
     * 角色名称
     */
    private String name;

    /**
     * 角色描述
     */
    private String description;

    /**
     * 固定描述词（AI生成时使用）
     */
    @TableField("fixed_desc")
    private String fixedDesc;

    /**
     * 年龄
     */
    private Integer age;

    /**
     * 性别
     */
    private String gender;

    /**
     * 角色类型: PROTAGONIST, ANTAGONIST, SUPPORTING, BACKGROUND
     */
    @TableField("character_type")
    private String characterType;

    /**
     * 语音种子ID（用于TTS）
     */
    @TableField("voice_seed_id")
    private String voiceSeedId;

    /**
     * 外貌数据 (JSON)
     * 结构:
     * {
     *   // 基础外貌
     *   "bodyType": "slim",              // 体型: slim/average/athletic/muscular/chubby
     *   "height": "tall",                // 身高: short/average/tall
     *   "skinTone": "fair",              // 肤色: fair/light/medium/tan/dark
     *
     *   // 面部特征
     *   "faceShape": "oval",             // 脸型: oval/round/square/heart/long
     *   "eyeColor": "brown",             // 眼睛颜色
     *   "eyeShape": "almond",            // 眼型: almond/round/monolid/hooded
     *   "hairColor": "black",            // 发色
     *   "hairStyle": "short straight",   // 发型描述
     *   "hairLength": "short",           // 发长: short/medium/long
     *
     *   // 特殊标识
     *   "distinguishingFeatures": ["scar on left cheek", "glasses"],
     *
     *   // 默认服装
     *   "defaultOutfit": {
     *     "style": "casual",             // 风格: casual/formal/sporty/traditional
     *     "topwear": "white shirt",
     *     "bottomwear": "blue jeans",
     *     "footwear": "sneakers",
     *     "accessories": ["watch"]
     *   },
     *
     *   // AI生成参考
     *   "artStyle": "anime",             // 绘画风格: anime/realistic/cartoon/chibi
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

    /**
     * 发布时间（发布到公共库时记录，仅 scope=SYSTEM 时有值）
     */
    @TableField("published_at")
    private LocalDateTime publishedAt;

    /**
     * 发布操作人 userId
     */
    @TableField("published_by")
    private String publishedBy;

    /**
     * 发布说明
     */
    @TableField("publish_note")
    private String publishNote;
}
