package com.actionow.common.mq.publisher;

import com.actionow.common.mq.constant.MqConstants;
import com.actionow.common.mq.message.MessageWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Canvas 消息发布工具
 * 用于业务服务向 Canvas 服务发送实体变更消息
 *
 * 统一主画布模型：1 Script = 1 Canvas
 * 所有实体都归属于 Script 的 Canvas，通过 scriptId 标识
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CanvasMessagePublisher {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发布单个实体变更消息（新版接口）
     *
     * @param entityType       实体类型 (SCRIPT, EPISODE, STORYBOARD, CHARACTER, SCENE, PROP, ASSET)
     * @param entityId         实体ID
     * @param scriptId         剧本ID（统一主画布标识）
     * @param parentEntityType 父实体类型（可选，用于建立层级关系）
     * @param parentEntityId   父实体ID（可选）
     * @param workspaceId      工作空间ID
     * @param changeType       变更类型 (CREATED, UPDATED, DELETED)
     * @param entityData       实体数据（可选，用于缓存）
     */
    public void publishEntityChange(String entityType, String entityId,
                                    String scriptId, String parentEntityType, String parentEntityId,
                                    String workspaceId, String changeType,
                                    Map<String, Object> entityData) {
        publishEntityChange(entityType, entityId, scriptId, parentEntityType, parentEntityId,
                workspaceId, changeType, entityData, null, false);
    }

    /**
     * 发布单个实体变更消息（带跳过同步选项）
     *
     * @param entityType       实体类型 (SCRIPT, EPISODE, STORYBOARD, CHARACTER, SCENE, PROP, ASSET)
     * @param entityId         实体ID
     * @param scriptId         剧本ID（统一主画布标识）
     * @param parentEntityType 父实体类型（可选）
     * @param parentEntityId   父实体ID（可选）
     * @param workspaceId      工作空间ID
     * @param changeType       变更类型 (CREATED, UPDATED, DELETED)
     * @param entityData       实体数据（可选，用于缓存）
     * @param skipCanvasSync   是否跳过 Canvas 同步（用于避免循环调用）
     */
    public void publishEntityChange(String entityType, String entityId,
                                    String scriptId, String parentEntityType, String parentEntityId,
                                    String workspaceId, String changeType,
                                    Map<String, Object> entityData,
                                    boolean skipCanvasSync) {
        publishEntityChange(entityType, entityId, scriptId, parentEntityType, parentEntityId,
                workspaceId, changeType, entityData, null, skipCanvasSync);
    }

    /**
     * 发布单个实体变更消息（完整参数版）
     *
     * @param entityType       实体类型 (SCRIPT, EPISODE, STORYBOARD, CHARACTER, SCENE, PROP, ASSET)
     * @param entityId         实体ID
     * @param scriptId         剧本ID（统一主画布标识）
     * @param parentEntityType 父实体类型（可选，用于建立层级关系）
     * @param parentEntityId   父实体ID（可选）
     * @param workspaceId      工作空间ID
     * @param changeType       变更类型 (CREATED, UPDATED, DELETED)
     * @param entityData       实体数据（可选，用于缓存）
     * @param relatedEntities  关联实体列表（可选，用于创建额外的边）
     * @param skipCanvasSync   是否跳过 Canvas 同步（用于避免循环调用）
     */
    public void publishEntityChange(String entityType, String entityId,
                                    String scriptId, String parentEntityType, String parentEntityId,
                                    String workspaceId, String changeType,
                                    Map<String, Object> entityData,
                                    List<RelatedEntity> relatedEntities,
                                    boolean skipCanvasSync) {
        if (skipCanvasSync) {
            log.debug("跳过Canvas同步: entityType={}, entityId={}, changeType={}",
                    entityType, entityId, changeType);
            return;
        }
        EntityChangePayload payload = EntityChangePayload.builder()
                .entityType(entityType)
                .entityId(entityId)
                .scriptId(scriptId)
                .parentEntityType(parentEntityType)
                .parentEntityId(parentEntityId)
                .workspaceId(workspaceId)
                .changeType(changeType)
                .entityData(entityData)
                .relatedEntities(relatedEntities)
                .build();

        MessageWrapper<EntityChangePayload> wrapper = MessageWrapper.wrap(
                MqConstants.Canvas.MSG_ENTITY_CHANGE, payload);

        String routingKey = "entity.change." + entityType.toLowerCase();
        rabbitTemplate.convertAndSend(MqConstants.EXCHANGE_TOPIC, routingKey, wrapper);

        log.debug("实体变更消息已发送: entityType={}, entityId={}, scriptId={}, changeType={}, relatedEntities={}",
                entityType, entityId, scriptId, changeType,
                relatedEntities != null ? relatedEntities.size() : 0);
    }

    /**
     * 发布批量实体变更消息
     *
     * @param scriptId    剧本ID（统一主画布标识）
     * @param workspaceId 工作空间ID
     * @param changeType  变更类型
     * @param entities    实体列表
     */
    public void publishBatchEntityChange(String scriptId,
                                         String workspaceId, String changeType,
                                         List<EntityItem> entities) {
        BatchEntityChangePayload payload = BatchEntityChangePayload.builder()
                .scriptId(scriptId)
                .workspaceId(workspaceId)
                .changeType(changeType)
                .entities(entities)
                .build();

        MessageWrapper<BatchEntityChangePayload> wrapper = MessageWrapper.wrap(
                MqConstants.Canvas.MSG_BATCH_ENTITY_CHANGE, payload);

        rabbitTemplate.convertAndSend(MqConstants.EXCHANGE_TOPIC, "entity.batch.change", wrapper);

        log.debug("批量实体变更消息已发送: scriptId={}, changeType={}, count={}",
                scriptId, changeType, entities.size());
    }

    /**
     * 单个实体变更消息载荷
     * 统一主画布模型：所有实体都归属于 Script 的 Canvas
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class EntityChangePayload implements Serializable {
        private static final long serialVersionUID = 2L;
        private String entityType;
        private String entityId;
        /**
         * 剧本ID（统一主画布标识）
         */
        private String scriptId;
        /**
         * 父实体类型（可选，用于建立层级关系）
         * 如 Episode 的父实体类型为 SCRIPT, Storyboard 的父实体类型为 EPISODE
         */
        private String parentEntityType;
        /**
         * 父实体ID（可选）
         */
        private String parentEntityId;
        private String workspaceId;
        private String changeType;
        private Map<String, Object> entityData;
        /**
         * 额外的关联实体列表
         * 用于创建实体与其他实体之间的边
         * 例如：分镜同时关联剧集和剧本，素材关联到角色/场景/道具等
         */
        private List<RelatedEntity> relatedEntities;
    }

    /**
     * 关联实体信息
     * 用于描述需要额外创建边的关联关系
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
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

    /**
     * 批量实体变更消息载荷
     * 统一主画布模型：所有实体都归属于 Script 的 Canvas
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class BatchEntityChangePayload implements Serializable {
        private static final long serialVersionUID = 2L;
        /**
         * 剧本ID（统一主画布标识）
         */
        private String scriptId;
        private String workspaceId;
        private String changeType;
        private List<EntityItem> entities;
    }

    /**
     * 实体项
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class EntityItem implements Serializable {
        private static final long serialVersionUID = 1L;
        private String entityType;
        private String entityId;
        private Map<String, Object> entityData;
    }
}
