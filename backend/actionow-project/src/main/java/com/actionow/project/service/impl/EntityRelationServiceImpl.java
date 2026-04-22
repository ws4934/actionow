package com.actionow.project.service.impl;

import com.actionow.common.core.constant.CommonConstants;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.id.UuidGenerator;
import com.actionow.common.core.result.ResultCode;
import com.actionow.project.constant.ProjectConstants;
import com.actionow.project.dto.relation.*;
import com.actionow.project.dto.asset.AssetResponse;
import com.actionow.project.entity.*;
import com.actionow.project.entity.Character;
import com.actionow.project.feign.UserBasicInfo;
import com.actionow.project.mapper.*;
import com.actionow.project.publisher.EntityChangeEventPublisher;
import com.actionow.project.service.AssetService;
import com.actionow.project.service.EntityRelationService;
import com.actionow.project.service.UserInfoHelper;
import com.actionow.project.validator.RelationExtraInfoValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.dao.DuplicateKeyException;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 实体关系服务实现
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityRelationServiceImpl implements EntityRelationService {

    private final EntityRelationMapper entityRelationMapper;
    private final CharacterMapper characterMapper;
    private final SceneMapper sceneMapper;
    private final PropMapper propMapper;
    private final StyleMapper styleMapper;
    private final StoryboardMapper storyboardMapper;
    private final EpisodeMapper episodeMapper;
    private final UserInfoHelper userInfoHelper;
    private final AssetService assetService;
    private final EntityChangeEventPublisher entityChangeEventPublisher;
    private final RelationExtraInfoValidator relationExtraInfoValidator;

    // ==================== 关系管理 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EntityRelationResponse createRelation(CreateEntityRelationRequest request, String workspaceId, String userId) {
        relationExtraInfoValidator.validate(request.getRelationType(), request.getExtraInfo());

        // 检查关系是否已存在
        if (existsRelation(request.getSourceType(), request.getSourceId(),
                request.getTargetType(), request.getTargetId(), request.getRelationType())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "该关系已存在");
        }

        // 创建关系记录
        EntityRelation relation = new EntityRelation();
        relation.setId(UuidGenerator.generateUuidV7());
        relation.setWorkspaceId(workspaceId);
        relation.setSourceType(request.getSourceType());
        relation.setSourceId(request.getSourceId());
        relation.setSourceVersionId(request.getSourceVersionId());
        relation.setTargetType(request.getTargetType());
        relation.setTargetId(request.getTargetId());
        relation.setTargetVersionId(request.getTargetVersionId());
        relation.setRelationType(request.getRelationType());
        relation.setRelationLabel(request.getRelationLabel());
        relation.setDescription(request.getDescription());
        relation.setSequence(request.getSequence() != null ? request.getSequence() : 0);
        relation.setExtraInfo(request.getExtraInfo() != null ? request.getExtraInfo() : new HashMap<>());
        relation.setCreatedBy(userId);

        try {
            entityRelationMapper.insert(relation);
        } catch (DuplicateKeyException e) {
            // 并发插入导致的唯一约束冲突
            log.warn("检测到并发插入导致的重复关系: sourceType={}, sourceId={}, targetType={}, targetId={}, relationType={}",
                    request.getSourceType(), request.getSourceId(),
                    request.getTargetType(), request.getTargetId(), request.getRelationType());
            throw new BusinessException(ResultCode.PARAM_INVALID, "该关系已存在（并发创建）");
        }

        log.info("创建实体关系成功: relationId={}, sourceType={}, sourceId={}, targetType={}, targetId={}, relationType={}",
                relation.getId(), request.getSourceType(), request.getSourceId(),
                request.getTargetType(), request.getTargetId(), request.getRelationType());

        publishRelationChangeForSource(request.getSourceType(), request.getSourceId());

        return enrichWithUserInfo(EntityRelationResponse.fromEntity(relation));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<EntityRelationResponse> batchCreateRelations(List<CreateEntityRelationRequest> requests, String workspaceId, String userId) {
        List<EntityRelationResponse> results = new ArrayList<>();

        for (CreateEntityRelationRequest request : requests) {
            try {
                relationExtraInfoValidator.validate(request.getRelationType(), request.getExtraInfo());

                // 跳过已存在的关系
                if (existsRelation(request.getSourceType(), request.getSourceId(),
                        request.getTargetType(), request.getTargetId(), request.getRelationType())) {
                    log.warn("跳过已存在的关系: sourceType={}, sourceId={}, targetType={}, targetId={}, relationType={}",
                            request.getSourceType(), request.getSourceId(),
                            request.getTargetType(), request.getTargetId(), request.getRelationType());
                    continue;
                }

                EntityRelation relation = new EntityRelation();
                relation.setId(UuidGenerator.generateUuidV7());
                relation.setWorkspaceId(workspaceId);
                relation.setSourceType(request.getSourceType());
                relation.setSourceId(request.getSourceId());
                relation.setSourceVersionId(request.getSourceVersionId());
                relation.setTargetType(request.getTargetType());
                relation.setTargetId(request.getTargetId());
                relation.setTargetVersionId(request.getTargetVersionId());
                relation.setRelationType(request.getRelationType());
                relation.setRelationLabel(request.getRelationLabel());
                relation.setDescription(request.getDescription());
                relation.setSequence(request.getSequence() != null ? request.getSequence() : 0);
                relation.setExtraInfo(request.getExtraInfo() != null ? request.getExtraInfo() : new HashMap<>());
                relation.setCreatedBy(userId);

                entityRelationMapper.insert(relation);
                results.add(EntityRelationResponse.fromEntity(relation));
            } catch (DuplicateKeyException e) {
                // 并发插入导致的重复，跳过
                log.warn("批量创建时检测到并发插入，跳过: sourceType={}, sourceId={}, targetType={}, targetId={}, relationType={}",
                        request.getSourceType(), request.getSourceId(),
                        request.getTargetType(), request.getTargetId(), request.getRelationType());
            } catch (Exception e) {
                log.warn("批量创建关系时跳过失败项: {}", e.getMessage());
            }
        }

        log.info("批量创建实体关系完成: count={}", results.size());

        // 对所有受影响的 source 实体发布变更通知
        requests.stream()
                .collect(Collectors.toMap(
                        r -> r.getSourceType() + ":" + r.getSourceId(),
                        r -> r,
                        (a, b) -> a))
                .values()
                .forEach(r -> publishRelationChangeForSource(r.getSourceType(), r.getSourceId()));

        return results;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EntityRelationResponse updateRelation(String relationId, UpdateEntityRelationRequest request, String userId) {
        EntityRelation relation = entityRelationMapper.selectById(relationId);
        if (relation == null || relation.getDeleted() == CommonConstants.DELETED) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "关系不存在");
        }

        if (request.getRelationLabel() != null) {
            relation.setRelationLabel(request.getRelationLabel());
        }
        if (request.getDescription() != null) {
            relation.setDescription(request.getDescription());
        }
        if (request.getSequence() != null) {
            relation.setSequence(request.getSequence());
        }
        if (request.getExtraInfo() != null) {
            relationExtraInfoValidator.validate(relation.getRelationType(), request.getExtraInfo());
            if (Boolean.TRUE.equals(request.getMergeExtraInfo())) {
                // 合并 extraInfo
                Map<String, Object> merged = new HashMap<>(
                        relation.getExtraInfo() != null ? relation.getExtraInfo() : new HashMap<>());
                merged.putAll(request.getExtraInfo());
                relation.setExtraInfo(merged);
            } else {
                // 替换 extraInfo
                relation.setExtraInfo(request.getExtraInfo());
            }
        }
        relation.setUpdatedBy(userId);

        entityRelationMapper.updateById(relation);

        log.info("更新实体关系成功: relationId={}", relationId);

        publishRelationChangeForSource(relation.getSourceType(), relation.getSourceId());

        return enrichWithUserInfo(EntityRelationResponse.fromEntity(relation));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRelation(String relationId, String userId) {
        EntityRelation relation = entityRelationMapper.selectById(relationId);
        if (relation == null || relation.getDeleted() == CommonConstants.DELETED) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "关系不存在");
        }

        // 使用硬删除，因为唯一约束不含 deleted 字段
        entityRelationMapper.hardDeleteById(relationId);

        log.info("删除实体关系成功: relationId={}", relationId);

        publishRelationChangeForSource(relation.getSourceType(), relation.getSourceId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRelation(String sourceType, String sourceId, String targetType, String targetId,
                               String relationType, String userId) {
        int affected = entityRelationMapper.deleteBetweenEntitiesWithType(
                sourceType, sourceId, targetType, targetId, relationType);
        if (affected == 0) {
            log.warn("删除关系时未找到匹配项: sourceType={}, sourceId={}, targetType={}, targetId={}, relationType={}",
                    sourceType, sourceId, targetType, targetId, relationType);
        } else {
            log.info("删除实体关系成功: sourceType={}, sourceId={}, targetType={}, targetId={}, relationType={}",
                    sourceType, sourceId, targetType, targetId, relationType);
            publishRelationChangeForSource(sourceType, sourceId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBySource(String sourceType, String sourceId, String userId) {
        int affected = entityRelationMapper.deleteBySource(sourceType, sourceId);
        log.info("删除源实体的所有关系: sourceType={}, sourceId={}, affected={}", sourceType, sourceId, affected);
        if (affected > 0) {
            publishRelationChangeForSource(sourceType, sourceId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBySourceAndRelationType(String sourceType, String sourceId, String relationType, String userId) {
        int affected = entityRelationMapper.deleteBySourceAndRelationType(sourceType, sourceId, relationType);
        log.info("删除源实体指定类型的关系: sourceType={}, sourceId={}, relationType={}, affected={}",
                sourceType, sourceId, relationType, affected);
        if (affected > 0) {
            publishRelationChangeForSource(sourceType, sourceId);
        }
    }

    // ==================== 查询接口 ====================

    @Override
    public EntityRelationResponse getById(String relationId) {
        EntityRelation relation = entityRelationMapper.selectById(relationId);
        if (relation == null || relation.getDeleted() == CommonConstants.DELETED) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "关系不存在");
        }
        return enrichWithUserInfo(EntityRelationResponse.fromEntity(relation));
    }

    @Override
    public List<EntityRelationResponse> listBySource(String sourceType, String sourceId) {
        List<EntityRelation> relations = entityRelationMapper.selectBySource(sourceType, sourceId);
        return enrichWithUserInfo(relations.stream()
                .map(EntityRelationResponse::fromEntity)
                .collect(Collectors.toList()));
    }

    @Override
    public List<EntityRelationResponse> listBySourceAndRelationType(String sourceType, String sourceId, String relationType) {
        List<EntityRelation> relations = entityRelationMapper.selectBySourceAndRelationType(
                sourceType, sourceId, relationType);
        return enrichWithUserInfo(relations.stream()
                .map(EntityRelationResponse::fromEntity)
                .collect(Collectors.toList()));
    }

    @Override
    public List<EntityRelationResponse> listBySourceAndTargetType(String sourceType, String sourceId, String targetType) {
        List<EntityRelation> relations = entityRelationMapper.selectBySourceAndTargetType(
                sourceType, sourceId, targetType);
        return enrichWithUserInfo(relations.stream()
                .map(EntityRelationResponse::fromEntity)
                .collect(Collectors.toList()));
    }

    @Override
    public List<EntityRelationResponse> listByTarget(String targetType, String targetId) {
        List<EntityRelation> relations = entityRelationMapper.selectByTarget(targetType, targetId);
        return enrichWithUserInfo(relations.stream()
                .map(EntityRelationResponse::fromEntity)
                .collect(Collectors.toList()));
    }

    @Override
    public List<EntityRelationResponse> listBetweenEntities(String sourceType, String sourceId,
                                                             String targetType, String targetId) {
        List<EntityRelation> relations = entityRelationMapper.selectBetweenEntities(
                sourceType, sourceId, targetType, targetId);
        return enrichWithUserInfo(relations.stream()
                .map(EntityRelationResponse::fromEntity)
                .collect(Collectors.toList()));
    }

    @Override
    public Map<String, List<EntityRelationResponse>> batchListBySource(String sourceType, List<String> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<EntityRelation> relations = entityRelationMapper.selectBySourceIds(sourceType, sourceIds);
        return relations.stream()
                .map(EntityRelationResponse::fromEntity)
                .collect(Collectors.groupingBy(EntityRelationResponse::getSourceId));
    }

    // ==================== 分镜关系便捷方法 ====================

    @Override
    public StoryboardSceneRelation getStoryboardScene(String storyboardId) {
        List<EntityRelation> relations = entityRelationMapper.selectBySourceAndRelationType(
                ProjectConstants.EntityType.STORYBOARD, storyboardId, ProjectConstants.RelationType.TAKES_PLACE_IN);
        if (relations.isEmpty()) {
            return null;
        }
        EntityRelation relation = relations.get(0);
        StoryboardSceneRelation result = StoryboardSceneRelation.fromEntity(relation);

        // 填充场景信息
        Scene scene = sceneMapper.selectById(relation.getTargetId());
        if (scene != null) {
            result.setSceneName(scene.getName());
            result.setSceneDescription(scene.getDescription());
            // 填充封面URL
            if (scene.getCoverAssetId() != null) {
                result.setCoverUrl(getAssetUrl(scene.getCoverAssetId()));
            }
        }
        return result;
    }

    @Override
    public List<StoryboardCharacterRelation> listStoryboardCharacters(String storyboardId) {
        List<EntityRelation> relations = entityRelationMapper.selectBySourceAndRelationType(
                ProjectConstants.EntityType.STORYBOARD, storyboardId, ProjectConstants.RelationType.APPEARS_IN);

        if (relations.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量获取角色信息
        Set<String> characterIds = relations.stream()
                .map(EntityRelation::getTargetId)
                .collect(Collectors.toSet());
        Map<String, Character> characterMap = batchGetCharacters(characterIds);

        // 收集封面素材ID并批量获取
        Map<String, String> coverUrlMap = batchGetCoverUrls(
                characterMap.values().stream()
                        .map(Character::getCoverAssetId)
                        .filter(id -> id != null && !id.isEmpty())
                        .collect(Collectors.toSet()));

        return relations.stream()
                .map(relation -> {
                    StoryboardCharacterRelation result = StoryboardCharacterRelation.fromEntity(relation);
                    Character character = characterMap.get(relation.getTargetId());
                    if (character != null) {
                        result.setCharacterName(character.getName());
                        result.setCharacterType(character.getCharacterType());
                        if (character.getCoverAssetId() != null) {
                            result.setCoverUrl(coverUrlMap.get(character.getCoverAssetId()));
                        }
                    }
                    return result;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<StoryboardPropRelation> listStoryboardProps(String storyboardId) {
        List<EntityRelation> relations = entityRelationMapper.selectBySourceAndRelationType(
                ProjectConstants.EntityType.STORYBOARD, storyboardId, ProjectConstants.RelationType.USES);

        if (relations.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量获取道具信息
        Set<String> propIds = relations.stream()
                .map(EntityRelation::getTargetId)
                .collect(Collectors.toSet());
        Map<String, Prop> propMap = batchGetProps(propIds);

        // 收集封面素材ID并批量获取
        Map<String, String> coverUrlMap = batchGetCoverUrls(
                propMap.values().stream()
                        .map(Prop::getCoverAssetId)
                        .filter(id -> id != null && !id.isEmpty())
                        .collect(Collectors.toSet()));

        return relations.stream()
                .map(relation -> {
                    StoryboardPropRelation result = StoryboardPropRelation.fromEntity(relation);
                    Prop prop = propMap.get(relation.getTargetId());
                    if (prop != null) {
                        result.setPropName(prop.getName());
                        result.setPropType(prop.getPropType());
                        if (prop.getCoverAssetId() != null) {
                            result.setCoverUrl(coverUrlMap.get(prop.getCoverAssetId()));
                        }
                    }
                    return result;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<StoryboardDialogueRelation> listStoryboardDialogues(String storyboardId) {
        List<EntityRelation> relations = entityRelationMapper.selectBySourceAndRelationType(
                ProjectConstants.EntityType.STORYBOARD, storyboardId, ProjectConstants.RelationType.SPEAKS_IN);

        if (relations.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量获取角色信息
        Set<String> characterIds = relations.stream()
                .map(EntityRelation::getTargetId)
                .collect(Collectors.toSet());
        Map<String, Character> characterMap = batchGetCharacters(characterIds);

        return relations.stream()
                .map(relation -> {
                    StoryboardDialogueRelation result = StoryboardDialogueRelation.fromEntity(relation);
                    Character character = characterMap.get(relation.getTargetId());
                    if (character != null) {
                        result.setCharacterName(character.getName());
                    }
                    return result;
                })
                .sorted(Comparator.comparing(StoryboardDialogueRelation::getSequence,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    @Override
    public StoryboardRelationsResponse getStoryboardRelations(String storyboardId) {
        StoryboardRelationsResponse response = new StoryboardRelationsResponse();
        response.setStoryboardId(storyboardId);
        response.setScene(getStoryboardScene(storyboardId));
        response.setCharacters(listStoryboardCharacters(storyboardId));
        response.setProps(listStoryboardProps(storyboardId));
        response.setDialogues(listStoryboardDialogues(storyboardId));

        // 获取风格关系
        List<EntityRelation> styleRelations = entityRelationMapper.selectBySourceAndRelationType(
                ProjectConstants.EntityType.STORYBOARD, storyboardId, ProjectConstants.RelationType.STYLED_BY);
        if (!styleRelations.isEmpty()) {
            EntityRelation styleRelation = styleRelations.get(0);
            response.setStyleId(styleRelation.getTargetId());
            Style style = styleMapper.selectById(styleRelation.getTargetId());
            if (style != null) {
                response.setStyleName(style.getName());
            }
        }

        return response;
    }

    @Override
    public Map<String, StoryboardRelationsResponse> batchGetStoryboardRelations(List<String> storyboardIds) {
        if (storyboardIds == null || storyboardIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // 批量查询所有关系
        List<EntityRelation> allRelations = entityRelationMapper.selectBySourceIds(
                ProjectConstants.EntityType.STORYBOARD, storyboardIds);

        // 收集所有需要查询的实体ID
        Set<String> characterIds = new HashSet<>();
        Set<String> propIds = new HashSet<>();
        Set<String> sceneIds = new HashSet<>();
        Set<String> styleIds = new HashSet<>();

        for (EntityRelation relation : allRelations) {
            switch (relation.getTargetType()) {
                case ProjectConstants.EntityType.CHARACTER -> characterIds.add(relation.getTargetId());
                case ProjectConstants.EntityType.PROP -> propIds.add(relation.getTargetId());
                case ProjectConstants.EntityType.SCENE -> sceneIds.add(relation.getTargetId());
                case ProjectConstants.EntityType.STYLE -> styleIds.add(relation.getTargetId());
            }
        }

        // 批量获取实体信息
        Map<String, Character> characterMap = batchGetCharacters(characterIds);
        Map<String, Prop> propMap = batchGetProps(propIds);
        Map<String, Scene> sceneMap = batchGetScenes(sceneIds);
        Map<String, Style> styleMap = batchGetStyles(styleIds);

        // 收集所有封面素材ID并批量获取URL
        Set<String> coverAssetIds = new HashSet<>();
        characterMap.values().stream()
                .map(Character::getCoverAssetId)
                .filter(id -> id != null && !id.isEmpty())
                .forEach(coverAssetIds::add);
        propMap.values().stream()
                .map(Prop::getCoverAssetId)
                .filter(id -> id != null && !id.isEmpty())
                .forEach(coverAssetIds::add);
        sceneMap.values().stream()
                .map(Scene::getCoverAssetId)
                .filter(id -> id != null && !id.isEmpty())
                .forEach(coverAssetIds::add);
        Map<String, String> coverUrlMap = batchGetCoverUrls(coverAssetIds);

        // 按分镜ID分组处理
        Map<String, List<EntityRelation>> relationsByStoryboard = allRelations.stream()
                .collect(Collectors.groupingBy(EntityRelation::getSourceId));

        Map<String, StoryboardRelationsResponse> result = new HashMap<>();

        for (String storyboardId : storyboardIds) {
            StoryboardRelationsResponse response = new StoryboardRelationsResponse();
            response.setStoryboardId(storyboardId);

            List<EntityRelation> relations = relationsByStoryboard.getOrDefault(storyboardId, Collections.emptyList());

            List<StoryboardCharacterRelation> characters = new ArrayList<>();
            List<StoryboardPropRelation> props = new ArrayList<>();
            List<StoryboardDialogueRelation> dialogues = new ArrayList<>();

            for (EntityRelation relation : relations) {
                switch (relation.getRelationType()) {
                    case ProjectConstants.RelationType.TAKES_PLACE_IN -> {
                        StoryboardSceneRelation sceneRelation = StoryboardSceneRelation.fromEntity(relation);
                        Scene scene = sceneMap.get(relation.getTargetId());
                        if (scene != null) {
                            sceneRelation.setSceneName(scene.getName());
                            sceneRelation.setSceneDescription(scene.getDescription());
                            if (scene.getCoverAssetId() != null) {
                                sceneRelation.setCoverUrl(coverUrlMap.get(scene.getCoverAssetId()));
                            }
                        }
                        response.setScene(sceneRelation);
                    }
                    case ProjectConstants.RelationType.APPEARS_IN -> {
                        StoryboardCharacterRelation charRelation = StoryboardCharacterRelation.fromEntity(relation);
                        Character character = characterMap.get(relation.getTargetId());
                        if (character != null) {
                            charRelation.setCharacterName(character.getName());
                            charRelation.setCharacterType(character.getCharacterType());
                            if (character.getCoverAssetId() != null) {
                                charRelation.setCoverUrl(coverUrlMap.get(character.getCoverAssetId()));
                            }
                        }
                        characters.add(charRelation);
                    }
                    case ProjectConstants.RelationType.USES -> {
                        StoryboardPropRelation propRelation = StoryboardPropRelation.fromEntity(relation);
                        Prop prop = propMap.get(relation.getTargetId());
                        if (prop != null) {
                            propRelation.setPropName(prop.getName());
                            propRelation.setPropType(prop.getPropType());
                            if (prop.getCoverAssetId() != null) {
                                propRelation.setCoverUrl(coverUrlMap.get(prop.getCoverAssetId()));
                            }
                        }
                        props.add(propRelation);
                    }
                    case ProjectConstants.RelationType.SPEAKS_IN -> {
                        StoryboardDialogueRelation dialogueRelation = StoryboardDialogueRelation.fromEntity(relation);
                        Character character = characterMap.get(relation.getTargetId());
                        if (character != null) {
                            dialogueRelation.setCharacterName(character.getName());
                        }
                        dialogues.add(dialogueRelation);
                    }
                    case ProjectConstants.RelationType.STYLED_BY -> {
                        response.setStyleId(relation.getTargetId());
                        Style style = styleMap.get(relation.getTargetId());
                        if (style != null) {
                            response.setStyleName(style.getName());
                        }
                    }
                }
            }

            // 排序
            characters.sort(Comparator.comparing(StoryboardCharacterRelation::getSequence,
                    Comparator.nullsLast(Comparator.naturalOrder())));
            props.sort(Comparator.comparing(StoryboardPropRelation::getSequence,
                    Comparator.nullsLast(Comparator.naturalOrder())));
            dialogues.sort(Comparator.comparing(StoryboardDialogueRelation::getSequence,
                    Comparator.nullsLast(Comparator.naturalOrder())));

            response.setCharacters(characters);
            response.setProps(props);
            response.setDialogues(dialogues);

            result.put(storyboardId, response);
        }

        return result;
    }

    // ==================== 统计接口 ====================

    @Override
    public int countBySource(String sourceType, String sourceId) {
        return entityRelationMapper.countBySource(sourceType, sourceId);
    }

    @Override
    public int countBySourceAndRelationType(String sourceType, String sourceId, String relationType) {
        return entityRelationMapper.countBySourceAndRelationType(sourceType, sourceId, relationType);
    }

    // ==================== 便捷方法 ====================

    @Override
    public boolean existsRelation(String sourceType, String sourceId, String targetType, String targetId, String relationType) {
        int count = entityRelationMapper.existsRelation(sourceType, sourceId, targetType, targetId, relationType);
        return count > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EntityRelationResponse getOrCreate(CreateEntityRelationRequest request, String workspaceId, String userId) {
        // 检查是否已存在
        EntityRelation existing = entityRelationMapper.selectBetweenEntitiesWithType(
                request.getSourceType(), request.getSourceId(),
                request.getTargetType(), request.getTargetId(),
                request.getRelationType());

        if (existing != null) {
            return EntityRelationResponse.fromEntity(existing);
        }

        // 尝试创建，捕获并发插入导致的唯一约束冲突
        try {
            return createRelation(request, workspaceId, userId);
        } catch (DuplicateKeyException e) {
            // 并发插入时，另一个线程已经插入成功，查询并返回
            log.info("检测到并发插入，查询已存在的关系: sourceType={}, sourceId={}, targetType={}, targetId={}, relationType={}",
                    request.getSourceType(), request.getSourceId(),
                    request.getTargetType(), request.getTargetId(), request.getRelationType());
            existing = entityRelationMapper.selectBetweenEntitiesWithType(
                    request.getSourceType(), request.getSourceId(),
                    request.getTargetType(), request.getTargetId(),
                    request.getRelationType());
            if (existing != null) {
                return EntityRelationResponse.fromEntity(existing);
            }
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "创建关系失败，请重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<EntityRelationResponse> replaceRelations(String sourceType, String sourceId, String relationType,
                                                          List<CreateEntityRelationRequest> requests,
                                                          String workspaceId, String userId) {
        // 删除现有关系
        deleteBySourceAndRelationType(sourceType, sourceId, relationType, userId);

        // 创建新关系
        if (requests == null || requests.isEmpty()) {
            return Collections.emptyList();
        }

        return batchCreateRelations(requests, workspaceId, userId);
    }

    // ==================== 关系变更通知 ====================

    /**
     * 根据 sourceType 查询对应实体的 scriptId
     */
    private String resolveScriptIdForSource(String sourceType, String sourceId) {
        try {
            return switch (sourceType) {
                case ProjectConstants.EntityType.SCRIPT -> sourceId;
                case ProjectConstants.EntityType.EPISODE -> {
                    Episode episode = episodeMapper.selectById(sourceId);
                    yield episode != null ? episode.getScriptId() : null;
                }
                case ProjectConstants.EntityType.STORYBOARD -> {
                    Storyboard storyboard = storyboardMapper.selectById(sourceId);
                    yield storyboard != null ? storyboard.getScriptId() : null;
                }
                case ProjectConstants.EntityType.CHARACTER -> {
                    Character character = characterMapper.selectById(sourceId);
                    yield character != null ? character.getScriptId() : null;
                }
                case ProjectConstants.EntityType.SCENE -> {
                    Scene scene = sceneMapper.selectById(sourceId);
                    yield scene != null ? scene.getScriptId() : null;
                }
                case ProjectConstants.EntityType.PROP -> {
                    Prop prop = propMapper.selectById(sourceId);
                    yield prop != null ? prop.getScriptId() : null;
                }
                case ProjectConstants.EntityType.STYLE -> {
                    Style style = styleMapper.selectById(sourceId);
                    yield style != null ? style.getScriptId() : null;
                }
                default -> null;
            };
        } catch (Exception e) {
            log.warn("解析sourceEntity的scriptId失败: sourceType={}, sourceId={}", sourceType, sourceId, e);
            return null;
        }
    }

    /**
     * 对关系的 SOURCE 实体发布 UPDATED 事件（changedFields=["relations"]）
     */
    private void publishRelationChangeForSource(String sourceType, String sourceId) {
        try {
            String scriptId = resolveScriptIdForSource(sourceType, sourceId);
            entityChangeEventPublisher.publishEntityUpdated(
                    sourceType, sourceId, scriptId, List.of("relations"), null);
        } catch (Exception e) {
            log.warn("发布关系变更通知失败: sourceType={}, sourceId={}", sourceType, sourceId, e);
        }
    }

    // ==================== 内部辅助方法 ====================

    private Map<String, Character> batchGetCharacters(Set<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return characterMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Character::getId, Function.identity(), (a, b) -> a));
    }

    private Map<String, Prop> batchGetProps(Set<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return propMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Prop::getId, Function.identity(), (a, b) -> a));
    }

    private Map<String, Scene> batchGetScenes(Set<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return sceneMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Scene::getId, Function.identity(), (a, b) -> a));
    }

    private Map<String, Style> batchGetStyles(Set<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return styleMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Style::getId, Function.identity(), (a, b) -> a));
    }

    /**
     * 获取单个素材的URL
     */
    private String getAssetUrl(String assetId) {
        if (assetId == null || assetId.isEmpty()) {
            return null;
        }
        try {
            AssetResponse asset = assetService.getById(assetId);
            return asset.getThumbnailUrl() != null ? asset.getThumbnailUrl() : asset.getFileUrl();
        } catch (Exception e) {
            log.warn("获取素材URL失败: assetId={}", assetId);
            return null;
        }
    }

    /**
     * 批量获取素材URL
     */
    private Map<String, String> batchGetCoverUrls(Set<String> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            List<AssetResponse> assets = assetService.batchGet(new ArrayList<>(assetIds));
            return assets.stream()
                    .collect(Collectors.toMap(
                            AssetResponse::getId,
                            a -> a.getThumbnailUrl() != null ? a.getThumbnailUrl() : a.getFileUrl(),
                            (a, b) -> a
                    ));
        } catch (Exception e) {
            log.warn("批量获取素材URL失败", e);
            return Collections.emptyMap();
        }
    }

    private EntityRelationResponse enrichWithUserInfo(EntityRelationResponse response) {
        if (response == null || !StringUtils.hasText(response.getCreatedBy())) {
            return response;
        }
        UserBasicInfo userInfo = userInfoHelper.getUserInfo(response.getCreatedBy());
        if (userInfo != null) {
            response.setCreatedByUsername(userInfo.getUsername());
        }
        return response;
    }

    private List<EntityRelationResponse> enrichWithUserInfo(List<EntityRelationResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return responses;
        }
        Set<String> userIds = responses.stream()
                .map(EntityRelationResponse::getCreatedBy)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        Map<String, UserBasicInfo> userInfoMap = userInfoHelper.batchGetUserInfo(userIds);

        for (EntityRelationResponse response : responses) {
            if (StringUtils.hasText(response.getCreatedBy())) {
                UserBasicInfo userInfo = userInfoMap.get(response.getCreatedBy());
                if (userInfo != null) {
                    response.setCreatedByUsername(userInfo.getUsername());
                }
            }
        }
        return responses;
    }
}
