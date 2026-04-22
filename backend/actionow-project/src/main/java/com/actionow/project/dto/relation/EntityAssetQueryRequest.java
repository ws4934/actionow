package com.actionow.project.dto.relation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 实体关联素材查询请求
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityAssetQueryRequest {

    /**
     * 实体类型: SCRIPT, EPISODE, STORYBOARD, CHARACTER, SCENE, PROP, STYLE
     */
    private String entityType;

    /**
     * 实体ID
     */
    private String entityId;

    /**
     * 素材类型过滤（可多选）: IMAGE, VIDEO, AUDIO, TEXT, DOCUMENT
     */
    private List<String> assetTypes;

    /**
     * 关联类型过滤（可多选）: REFERENCE, OFFICIAL, DRAFT
     */
    private List<String> relationTypes;

    /**
     * 关键词搜索（素材名称/描述）
     */
    private String keyword;

    /**
     * 页码（从1开始）
     */
    private Integer page;

    /**
     * 每页数量
     */
    private Integer size;
}
