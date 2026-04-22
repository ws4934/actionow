package com.actionow.project.dto.relation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 实体关联素材汇总响应
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityAssetSummaryResponse {

    /**
     * 实体类型
     */
    private String entityType;

    /**
     * 实体ID
     */
    private String entityId;

    /**
     * 总素材数量
     */
    private Integer totalCount;

    /**
     * 按素材类型统计
     */
    private Map<String, Integer> countByAssetType;

    /**
     * 按关联类型统计
     */
    private Map<String, Integer> countByRelationType;
}
