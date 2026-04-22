package com.actionow.project.entity.version;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 剧本版本实体
 * 存储剧本的历史版本快照
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_script_version", autoResultMap = true)
public class ScriptVersion extends EntityVersion {

    /**
     * 剧本ID (主实体引用)
     */
    @TableField("script_id")
    private String scriptId;

    // ========== 以下为剧本业务字段快照 ==========

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
}
