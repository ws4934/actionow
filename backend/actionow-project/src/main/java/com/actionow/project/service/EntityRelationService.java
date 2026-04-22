package com.actionow.project.service;

import com.actionow.project.dto.relation.*;
import com.actionow.project.entity.EntityRelation;

import java.util.List;
import java.util.Map;

/**
 * 实体关系服务接口
 * 管理实体之间的关联关系，如分镜与角色、场景、道具的关系
 *
 * @author Actionow
 */
public interface EntityRelationService {

    // ==================== 关系管理 ====================

    /**
     * 创建实体关系
     *
     * @param request     创建请求
     * @param workspaceId 工作空间ID
     * @param userId      用户ID
     * @return 关系响应
     */
    EntityRelationResponse createRelation(CreateEntityRelationRequest request, String workspaceId, String userId);

    /**
     * 批量创建实体关系
     *
     * @param requests    创建请求列表
     * @param workspaceId 工作空间ID
     * @param userId      用户ID
     * @return 关系响应列表
     */
    List<EntityRelationResponse> batchCreateRelations(List<CreateEntityRelationRequest> requests, String workspaceId, String userId);

    /**
     * 更新实体关系
     *
     * @param relationId 关系ID
     * @param request    更新请求
     * @param userId     用户ID
     * @return 更新后的关系响应
     */
    EntityRelationResponse updateRelation(String relationId, UpdateEntityRelationRequest request, String userId);

    /**
     * 删除关系（按ID）
     *
     * @param relationId 关系ID
     * @param userId     操作用户ID
     */
    void deleteRelation(String relationId, String userId);

    /**
     * 删除两个实体之间指定类型的关系
     *
     * @param sourceType   源实体类型
     * @param sourceId     源实体ID
     * @param targetType   目标实体类型
     * @param targetId     目标实体ID
     * @param relationType 关系类型
     * @param userId       操作用户ID
     */
    void deleteRelation(String sourceType, String sourceId, String targetType, String targetId,
                        String relationType, String userId);

    /**
     * 删除源实体的所有关系
     *
     * @param sourceType 源实体类型
     * @param sourceId   源实体ID
     * @param userId     操作用户ID
     */
    void deleteBySource(String sourceType, String sourceId, String userId);

    /**
     * 删除源实体指定类型的关系
     *
     * @param sourceType   源实体类型
     * @param sourceId     源实体ID
     * @param relationType 关系类型
     * @param userId       操作用户ID
     */
    void deleteBySourceAndRelationType(String sourceType, String sourceId, String relationType, String userId);

    // ==================== 查询接口 ====================

    /**
     * 根据ID获取关系
     *
     * @param relationId 关系ID
     * @return 关系响应
     */
    EntityRelationResponse getById(String relationId);

    /**
     * 查询源实体的所有关系（出向）
     *
     * @param sourceType 源实体类型
     * @param sourceId   源实体ID
     * @return 关系列表
     */
    List<EntityRelationResponse> listBySource(String sourceType, String sourceId);

    /**
     * 查询源实体指定类型的关系
     *
     * @param sourceType   源实体类型
     * @param sourceId     源实体ID
     * @param relationType 关系类型
     * @return 关系列表
     */
    List<EntityRelationResponse> listBySourceAndRelationType(String sourceType, String sourceId, String relationType);

    /**
     * 查询源实体指定目标类型的关系
     *
     * @param sourceType 源实体类型
     * @param sourceId   源实体ID
     * @param targetType 目标实体类型
     * @return 关系列表
     */
    List<EntityRelationResponse> listBySourceAndTargetType(String sourceType, String sourceId, String targetType);

    /**
     * 查询目标实体的所有关系（入向）
     *
     * @param targetType 目标实体类型
     * @param targetId   目标实体ID
     * @return 关系列表
     */
    List<EntityRelationResponse> listByTarget(String targetType, String targetId);

    /**
     * 查询两个实体之间的关系
     *
     * @param sourceType 源实体类型
     * @param sourceId   源实体ID
     * @param targetType 目标实体类型
     * @param targetId   目标实体ID
     * @return 关系列表
     */
    List<EntityRelationResponse> listBetweenEntities(String sourceType, String sourceId,
                                                      String targetType, String targetId);

    /**
     * 批量查询多个源实体的关系
     *
     * @param sourceType 源实体类型
     * @param sourceIds  源实体ID列表
     * @return 关系列表（按源实体ID分组）
     */
    Map<String, List<EntityRelationResponse>> batchListBySource(String sourceType, List<String> sourceIds);

    // ==================== 分镜关系便捷方法 ====================

    /**
     * 获取分镜的场景关系
     *
     * @param storyboardId 分镜ID
     * @return 场景关系（包含 sceneOverride）
     */
    StoryboardSceneRelation getStoryboardScene(String storyboardId);

    /**
     * 获取分镜的角色出现关系列表
     *
     * @param storyboardId 分镜ID
     * @return 角色关系列表（包含位置、动作等信息）
     */
    List<StoryboardCharacterRelation> listStoryboardCharacters(String storyboardId);

    /**
     * 获取分镜的道具使用关系列表
     *
     * @param storyboardId 分镜ID
     * @return 道具关系列表（包含位置、交互等信息）
     */
    List<StoryboardPropRelation> listStoryboardProps(String storyboardId);

    /**
     * 获取分镜的对白关系列表
     *
     * @param storyboardId 分镜ID
     * @return 对白关系列表（包含台词、情绪等信息）
     */
    List<StoryboardDialogueRelation> listStoryboardDialogues(String storyboardId);

    /**
     * 获取分镜的所有关系（聚合）
     *
     * @param storyboardId 分镜ID
     * @return 分镜关系聚合对象
     */
    StoryboardRelationsResponse getStoryboardRelations(String storyboardId);

    /**
     * 批量获取多个分镜的关系
     *
     * @param storyboardIds 分镜ID列表
     * @return 分镜关系Map（key为分镜ID）
     */
    Map<String, StoryboardRelationsResponse> batchGetStoryboardRelations(List<String> storyboardIds);

    // ==================== 统计接口 ====================

    /**
     * 统计源实体的关系数量
     *
     * @param sourceType 源实体类型
     * @param sourceId   源实体ID
     * @return 关系数量
     */
    int countBySource(String sourceType, String sourceId);

    /**
     * 统计源实体指定类型的关系数量
     *
     * @param sourceType   源实体类型
     * @param sourceId     源实体ID
     * @param relationType 关系类型
     * @return 关系数量
     */
    int countBySourceAndRelationType(String sourceType, String sourceId, String relationType);

    // ==================== 便捷方法 ====================

    /**
     * 检查关系是否存在
     *
     * @param sourceType   源实体类型
     * @param sourceId     源实体ID
     * @param targetType   目标实体类型
     * @param targetId     目标实体ID
     * @param relationType 关系类型
     * @return 是否存在
     */
    boolean existsRelation(String sourceType, String sourceId, String targetType, String targetId, String relationType);

    /**
     * 获取或创建关系
     * 如果关系已存在则返回现有关系，否则创建新关系
     *
     * @param request     创建请求
     * @param workspaceId 工作空间ID
     * @param userId      用户ID
     * @return 关系响应
     */
    EntityRelationResponse getOrCreate(CreateEntityRelationRequest request, String workspaceId, String userId);

    /**
     * 替换关系
     * 删除源实体指定类型的所有现有关系，然后创建新关系
     *
     * @param sourceType   源实体类型
     * @param sourceId     源实体ID
     * @param relationType 关系类型
     * @param requests     新关系请求列表
     * @param workspaceId  工作空间ID
     * @param userId       用户ID
     * @return 新创建的关系列表
     */
    List<EntityRelationResponse> replaceRelations(String sourceType, String sourceId, String relationType,
                                                   List<CreateEntityRelationRequest> requests,
                                                   String workspaceId, String userId);
}
