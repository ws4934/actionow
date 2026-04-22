package com.actionow.project.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.actionow.common.data.entity.TenantBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 分镜实体
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_storyboard", autoResultMap = true)
public class Storyboard extends TenantBaseEntity {

    /**
     * 所属剧本ID
     */
    @TableField("script_id")
    private String scriptId;

    /**
     * 所属剧集ID
     */
    @TableField("episode_id")
    private String episodeId;

    /**
     * 分镜标题
     */
    private String title;

    /**
     * 排序序号
     */
    private Integer sequence;

    /**
     * 状态: DRAFT, GENERATING, COMPLETED, FAILED
     */
    private String status;

    /**
     * 分镜描述/台词
     */
    private String synopsis;

    /**
     * 时长（秒）
     */
    private Integer duration;

    /**
     * 视觉描述 (JSON)
     * 结构:
     * {
     *   // 镜头设置
     *   "shotType": "MEDIUM",            // 景别: EXTREME_CLOSE_UP/CLOSE_UP/MEDIUM_CLOSE_UP/MEDIUM/MEDIUM_LONG/LONG/EXTREME_LONG
     *   "cameraAngle": "EYE_LEVEL",      // 机位: HIGH/LOW/EYE_LEVEL/DUTCH/BIRD_EYE/WORM_EYE
     *   "cameraMovement": "STATIC",      // 运动: STATIC/PAN/TILT/ZOOM/DOLLY/TRACKING/CRANE
     *
     *   // 场景引用
     *   "sceneId": null,                 // 引用的场景ID
     *   "sceneOverride": {               // 场景属性覆盖
     *     "timeOfDay": "NIGHT",
     *     "weather": "rainy"
     *   },
     *
     *   // 角色布局
     *   "characters": [{
     *     "characterId": "xxx",          // 角色ID
     *     "position": "center",          // 位置: left/center/right/background/foreground
     *     "positionDetail": "站在窗前",
     *     "action": "looking out",
     *     "expression": "thoughtful",
     *     "outfitOverride": null
     *   }],
     *
     *   // 道具布局
     *   "props": [{
     *     "propId": "xxx",
     *     "position": "foreground",
     *     "interaction": "held by character",
     *     "state": null
     *   }],
     *
     *   // 视觉效果
     *   "visualEffects": ["rain drops on window"],
     *   "lighting": "dramatic",
     *   "colorGrading": "desaturated",
     *
     *   // 转场
     *   "transition": {
     *     "type": "CUT",                 // 类型: CUT/FADE/DISSOLVE/WIPE/IRIS
     *     "duration": null
     *   },
     *
     *   // AI生成提示
     *   "additionalPrompt": null,
     *   "negativePrompt": null
     * }
     */
    @TableField(value = "visual_desc", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> visualDesc;

    /**
     * 音频描述 (JSON)
     * 结构:
     * {
     *   // 对白/配音
     *   "dialogues": [{
     *     "characterId": "xxx",          // 说话角色ID
     *     "text": "台词内容",
     *     "emotion": "excited",          // 情绪: neutral/happy/sad/angry/fearful/surprised/excited
     *     "voiceStyle": "loud",          // 语气: whisper/soft/normal/loud/shouting
     *     "timing": { "start": 0, "end": 2000 }
     *   }],
     *
     *   // 旁白
     *   "narration": {
     *     "text": null,
     *     "voiceType": "male",
     *     "timing": null
     *   },
     *
     *   // 音效
     *   "soundEffects": [{
     *     "type": "ambient",             // 类型: ambient/action/impact/ui
     *     "description": "rain on window",
     *     "volume": "medium",            // 音量: low/medium/high
     *     "timing": { "start": 0, "duration": null }
     *   }],
     *
     *   // 背景音乐
     *   "bgm": {
     *     "mood": "melancholy",          // 情绪: happy/sad/tense/peaceful/epic/mysterious/romantic
     *     "genre": "piano",              // 类型: orchestral/electronic/piano/guitar/ambient
     *     "tempo": "slow",               // 节奏: slow/medium/fast
     *     "volume": "low",
     *     "assetId": null,               // 已有音乐素材ID
     *     "fadeIn": false,
     *     "fadeOut": false
     *   },
     *
     *   // 静音/强调
     *   "silence": false,
     *   "emphasis": null                 // 音频重点: dialogue/sfx/bgm
     * }
     */
    @TableField(value = "audio_desc", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> audioDesc;

    /**
     * 扩展信息
     */
    @TableField(value = "extra_info", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraInfo;

    /**
     * 封面素材ID
     */
    @TableField("cover_asset_id")
    private String coverAssetId;

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
}
