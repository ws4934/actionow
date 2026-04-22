package com.actionow.project.dto.relation;

import com.actionow.project.entity.EntityRelation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 实体关系响应
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityRelationResponse {

    /**
     * 关系ID
     */
    private String id;

    /**
     * 工作空间ID
     */
    private String workspaceId;

    /**
     * 源实体类型
     */
    private String sourceType;

    /**
     * 源实体ID
     */
    private String sourceId;

    /**
     * 源实体版本ID
     */
    private String sourceVersionId;

    /**
     * 目标实体类型
     */
    private String targetType;

    /**
     * 目标实体ID
     */
    private String targetId;

    /**
     * 目标实体版本ID
     */
    private String targetVersionId;

    /**
     * 关系类型
     */
    private String relationType;

    /**
     * 关系标签
     */
    private String relationLabel;

    /**
     * 描述
     */
    private String description;

    /**
     * 排序序号
     */
    private Integer sequence;

    /**
     * 扩展信息
     */
    private Map<String, Object> extraInfo;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 创建者ID
     */
    private String createdBy;

    /**
     * 创建者用户名
     */
    private String createdByUsername;

    /**
     * 从实体转换
     */
    public static EntityRelationResponse fromEntity(EntityRelation entity) {
        if (entity == null) {
            return null;
        }
        return EntityRelationResponse.builder()
                .id(entity.getId())
                .workspaceId(entity.getWorkspaceId())
                .sourceType(entity.getSourceType())
                .sourceId(entity.getSourceId())
                .sourceVersionId(entity.getSourceVersionId())
                .targetType(entity.getTargetType())
                .targetId(entity.getTargetId())
                .targetVersionId(entity.getTargetVersionId())
                .relationType(entity.getRelationType())
                .relationLabel(entity.getRelationLabel())
                .description(entity.getDescription())
                .sequence(entity.getSequence())
                .extraInfo(entity.getExtraInfo())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .createdBy(entity.getCreatedBy())
                .build();
    }
}
