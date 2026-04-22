package com.actionow.project.dto.relation;

import com.actionow.project.dto.asset.AssetResponse;
import com.actionow.project.entity.EntityAssetRelation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 实体-素材关联响应
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityAssetRelationResponse {

    /**
     * 关联记录ID
     */
    private String id;

    /**
     * 实体类型
     */
    private String entityType;

    /**
     * 实体ID
     */
    private String entityId;

    /**
     * 素材ID
     */
    private String assetId;

    /**
     * 关联类型: REFERENCE, OFFICIAL, DRAFT
     */
    private String relationType;

    /**
     * 描述
     */
    private String description;

    /**
     * 排序
     */
    private Integer sequence;

    /**
     * 扩展信息
     */
    private Map<String, Object> extraInfo;

    /**
     * 关联的素材详情
     */
    private AssetResponse asset;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 创建人
     */
    private String createdBy;

    /**
     * 创建人昵称
     */
    private String createdByNickname;

    /**
     * 创建人用户名
     */
    private String createdByUsername;

    /**
     * 从实体转换
     */
    public static EntityAssetRelationResponse fromEntity(EntityAssetRelation relation, AssetResponse asset) {
        return EntityAssetRelationResponse.builder()
                .id(relation.getId())
                .entityType(relation.getEntityType())
                .entityId(relation.getEntityId())
                .assetId(relation.getAssetId())
                .relationType(relation.getRelationType())
                .description(relation.getDescription())
                .sequence(relation.getSequence())
                .extraInfo(relation.getExtraInfo())
                .asset(asset)
                .createdAt(relation.getCreatedAt())
                .createdBy(relation.getCreatedBy())
                .build();
    }
}
