package com.actionow.project.entity.version;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 分镜版本实体
 * 存储分镜的历史版本快照
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_storyboard_version", autoResultMap = true)
public class StoryboardVersion extends EntityVersion {

    /**
     * 分镜ID (主实体引用)
     */
    @TableField("storyboard_id")
    private String storyboardId;

    // ========== 以下为分镜业务字段快照 ==========

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
     */
    @TableField(value = "visual_desc", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> visualDesc;

    /**
     * 音频描述 (JSON)
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
}
