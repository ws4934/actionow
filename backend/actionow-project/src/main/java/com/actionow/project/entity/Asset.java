package com.actionow.project.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.actionow.common.data.entity.TenantBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 素材实体
 * 管理所有类型的媒体资源
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_asset", autoResultMap = true)
public class Asset extends TenantBaseEntity {

    /**
     * 作用域: SYSTEM-系统公共库, WORKSPACE-工作空间级, SCRIPT-剧本级
     */
    private String scope;

    /**
     * 剧本ID（scope为SCRIPT时必填）
     */
    @TableField("script_id")
    private String scriptId;

    /**
     * 素材名称
     */
    private String name;

    /**
     * 描述
     */
    private String description;

    /**
     * 素材类型: IMAGE, VIDEO, AUDIO, DOCUMENT, MODEL, OTHER
     */
    @TableField("asset_type")
    private String assetType;

    /**
     * 来源: UPLOAD, SYSTEM, AI_GENERATED
     */
    private String source;

    /**
     * 文件存储路径（OSS key）
     */
    @TableField("file_key")
    private String fileKey;

    /**
     * 文件URL
     */
    @TableField("file_url")
    private String fileUrl;

    /**
     * 缩略图URL
     */
    @TableField("thumbnail_url")
    private String thumbnailUrl;

    /**
     * 文件大小（字节）
     */
    @TableField("file_size")
    private Long fileSize;

    /**
     * MIME类型
     */
    @TableField("mime_type")
    private String mimeType;

    /**
     * 元数据信息（宽度、高度、时长等结构化数据）
     */
    @TableField(value = "meta_info", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metaInfo;

    /**
     * 扩展信息（AI生成参数等）
     */
    @TableField(value = "extra_info", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraInfo;

    /**
     * 生成状态: DRAFT-草稿, GENERATING-生成中, COMPLETED-已完成, FAILED-失败
     */
    @TableField("generation_status")
    private String generationStatus;

    /**
     * AI工作流ID
     */
    @TableField("workflow_id")
    private String workflowId;

    /**
     * AI任务ID（生成中时记录）
     */
    @TableField("task_id")
    private String taskId;

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
     * 删除时间（软删除时记录）
     */
    @TableField("deleted_at")
    private java.time.LocalDateTime deletedAt;

    /**
     * 回收站文件路径（软删除时记录文件在回收站中的路径）
     */
    @TableField("trash_path")
    private String trashPath;

    /**
     * 发布时间（scope=SYSTEM时记录）
     */
    @TableField("published_at")
    private LocalDateTime publishedAt;

    /**
     * 发布操作人ID
     */
    @TableField("published_by")
    private String publishedBy;

    /**
     * 发布说明
     */
    @TableField("publish_note")
    private String publishNote;
}
