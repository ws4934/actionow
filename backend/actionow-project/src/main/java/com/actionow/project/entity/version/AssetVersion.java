package com.actionow.project.entity.version;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 素材版本实体
 * 存储素材的历史版本快照
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_asset_version", autoResultMap = true)
public class AssetVersion extends EntityVersion {

    /**
     * 素材ID (主实体引用)
     */
    @TableField("asset_id")
    private String assetId;

    // ========== 以下为素材业务字段快照 ==========

    /**
     * 作用域
     */
    private String scope;

    /**
     * 剧本ID
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
     * 素材类型
     */
    @TableField("asset_type")
    private String assetType;

    /**
     * 来源
     */
    private String source;

    /**
     * 文件存储路径
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
     * 元数据信息
     */
    @TableField(value = "meta_info", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metaInfo;

    /**
     * 扩展信息
     */
    @TableField(value = "extra_info", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraInfo;

    /**
     * 生成状态
     */
    @TableField("generation_status")
    private String generationStatus;

    /**
     * AI工作流ID
     */
    @TableField("workflow_id")
    private String workflowId;

    /**
     * AI任务ID
     */
    @TableField("task_id")
    private String taskId;
}
