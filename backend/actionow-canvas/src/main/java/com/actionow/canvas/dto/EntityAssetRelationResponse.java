package com.actionow.canvas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 实体-素材关联响应（Canvas 模块 Feign 调用使用）
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityAssetRelationResponse {

    /**
     * 关联ID
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
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
