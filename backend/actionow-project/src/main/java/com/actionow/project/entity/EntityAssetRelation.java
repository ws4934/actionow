package com.actionow.project.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.actionow.common.data.entity.TenantBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 实体-素材关联实体
 * 记录实体与素材之间的关联关系
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_entity_asset_relation", autoResultMap = true)
public class EntityAssetRelation extends TenantBaseEntity {

    /**
     * 实体类型: SCRIPT, EPISODE, STORYBOARD, CHARACTER, SCENE, PROP, STYLE
     */
    @TableField("entity_type")
    private String entityType;

    /**
     * 实体ID
     */
    @TableField("entity_id")
    private String entityId;

    /**
     * 素材ID
     */
    @TableField("asset_id")
    private String assetId;

    /**
     * 关联类型: REFERENCE(参考素材), OFFICIAL(正式素材), DRAFT(草稿素材)
     */
    @TableField("relation_type")
    private String relationType;

    /**
     * 描述
     */
    private String description;

    /**
     * 排序（同一实体下的素材顺序）
     */
    private Integer sequence;

    /**
     * 扩展信息
     */
    @TableField(value = "extra_info", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraInfo;
}
