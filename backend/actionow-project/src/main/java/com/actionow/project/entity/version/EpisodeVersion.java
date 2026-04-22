package com.actionow.project.entity.version;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 剧集版本实体
 * 存储剧集的历史版本快照
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_episode_version", autoResultMap = true)
public class EpisodeVersion extends EntityVersion {

    /**
     * 剧集ID (主实体引用)
     */
    @TableField("episode_id")
    private String episodeId;

    // ========== 以下为剧集业务字段快照 ==========

    /**
     * 所属剧本ID
     */
    @TableField("script_id")
    private String scriptId;

    /**
     * 剧集标题
     */
    private String title;

    /**
     * 排序序号
     */
    private Integer sequence;

    /**
     * 状态: DRAFT, IN_PROGRESS, COMPLETED
     */
    private String status;

    /**
     * 剧集简介
     */
    private String synopsis;

    /**
     * 剧集内容
     */
    private String content;

    /**
     * 封面素材ID
     */
    @TableField("cover_asset_id")
    private String coverAssetId;

    /**
     * 文档素材ID
     */
    @TableField("doc_asset_id")
    private String docAssetId;

    /**
     * 扩展信息
     */
    @TableField(value = "extra_info", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraInfo;
}
