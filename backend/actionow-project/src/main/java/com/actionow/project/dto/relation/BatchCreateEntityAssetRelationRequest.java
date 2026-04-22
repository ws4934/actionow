package com.actionow.project.dto.relation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量创建实体-素材关联请求
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchCreateEntityAssetRelationRequest {

    /**
     * 实体类型
     */
    @NotBlank(message = "实体类型不能为空")
    private String entityType;

    /**
     * 实体ID
     */
    @NotBlank(message = "实体ID不能为空")
    private String entityId;

    /**
     * 素材关联列表
     */
    @NotEmpty(message = "素材关联列表不能为空")
    @Valid
    private List<AssetRelationItem> assets;

    /**
     * 素材关联项
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssetRelationItem {
        /**
         * 素材ID
         */
        @NotBlank(message = "素材ID不能为空")
        private String assetId;

        /**
         * 关联类型: REFERENCE, OFFICIAL, DRAFT（默认为 DRAFT）
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
    }
}
