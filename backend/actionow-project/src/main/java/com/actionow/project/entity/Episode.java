package com.actionow.project.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.actionow.common.data.entity.TenantBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 剧集实体
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_episode", autoResultMap = true)
public class Episode extends TenantBaseEntity {

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
