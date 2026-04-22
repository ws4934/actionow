package com.actionow.project.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.actionow.common.data.entity.TenantBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 剧本实体
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_script", autoResultMap = true)
public class Script extends TenantBaseEntity {

    /**
     * 剧本标题
     */
    private String title;

    /**
     * 状态: DRAFT, IN_PROGRESS, COMPLETED, ARCHIVED
     */
    private String status;

    /**
     * 剧本简介/梗概
     */
    private String synopsis;

    /**
     * 剧本正文内容
     */
    private String content;

    /**
     * 封面素材ID
     */
    @TableField("cover_asset_id")
    private String coverAssetId;

    /**
     * 关联的文档素材ID
     */
    @TableField("doc_asset_id")
    private String docAssetId;

    /**
     * 扩展信息 (JSON)
     */
    @TableField(value = "extra_info", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraInfo;

    /**
     * 当前版本记录ID
     * 指向 t_script_version 表中最新版本的ID
     */
    @TableField("current_version_id")
    private String currentVersionId;

    /**
     * 业务版本号 (从1开始递增)
     * 每次更新时自增，与 version 字段不同，version 是乐观锁版本号
     */
    @TableField("version_number")
    private Integer versionNumber;
}
