package com.actionow.canvas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 实体变更消息
 * 用于跨服务传递实体变更事件到 Canvas 服务
 * 统一主画布模型：所有实体都归属于 Script 的 Canvas
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EntityChangeMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 实体类型
     * 如: SCRIPT, EPISODE, STORYBOARD, CHARACTER, SCENE, PROP, ASSET
     */
    private String entityType;

    /**
     * 实体ID
     */
    private String entityId;

    /**
     * 剧本ID（统一主画布的标识）
     * 每个 Script 对应唯一的 Canvas
     */
    private String scriptId;

    /**
     * 父实体类型（可选）
     * 用于建立层级关系，如 Episode 属于 Script, Storyboard 属于 Episode
     */
    private String parentEntityType;

    /**
     * 父实体ID（可选）
     * 与 parentEntityType 配合使用
     */
    private String parentEntityId;

    /**
     * 工作空间ID
     */
    private String workspaceId;

    /**
     * 变更类型
     * 如: CREATED, UPDATED, DELETED
     */
    private String changeType;

    /**
     * 实体数据（用于缓存到 Canvas 节点）
     * 可选字段，如果不提供则 Canvas 服务会通过 Feign 获取
     */
    private Map<String, Object> entityData;

    /**
     * 额外的关联实体列表
     * 用于创建实体与其他实体之间的边
     * 例如：分镜同时关联剧集和剧本，素材关联到角色/场景/道具等
     */
    private List<RelatedEntity> relatedEntities;

    /**
     * 变更类型枚举
     */
    public enum ChangeType {
        CREATED,
        UPDATED,
        DELETED
    }

    /**
     * 关联实体信息
     * 用于描述需要额外创建边的关联关系
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RelatedEntity implements Serializable {
        private static final long serialVersionUID = 1L;
        /**
         * 关联实体类型 (SCRIPT, EPISODE, CHARACTER, SCENE, PROP, STYLE, STORYBOARD)
         */
        private String entityType;
        /**
         * 关联实体ID
         */
        private String entityId;
        /**
         * 关系类型（可选，不提供则自动推断）
         * 如: has_character, has_asset, appears_in 等
         */
        private String relationType;
    }
}
