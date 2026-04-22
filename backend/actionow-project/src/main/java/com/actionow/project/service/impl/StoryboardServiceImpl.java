package com.actionow.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.common.core.constant.CommonConstants;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.id.UuidGenerator;
import com.actionow.common.core.result.ResultCode;
import com.actionow.project.constant.ProjectConstants;
import com.actionow.project.constant.EntityDefaults;
import com.actionow.project.dto.CreateStoryboardRequest;
import com.actionow.project.dto.UpdateStoryboardRequest;
import com.actionow.project.dto.StoryboardQueryRequest;
import com.actionow.project.dto.StoryboardDetailResponse;
import com.actionow.project.dto.StoryboardListResponse;
import com.actionow.project.dto.relation.*;
import com.actionow.project.entity.Episode;
import com.actionow.project.entity.Storyboard;
import com.actionow.project.mapper.StoryboardMapper;
import com.actionow.project.service.EpisodeService;
import com.actionow.project.service.EntityRelationService;
import com.actionow.project.service.StoryboardService;
import com.actionow.project.service.UserInfoHelper;
import com.actionow.project.service.version.VersionService;
import com.actionow.project.dto.version.StoryboardVersionDetailResponse;
import com.actionow.project.feign.UserBasicInfo;
import com.actionow.project.service.AssetService;
import com.actionow.project.dto.asset.AssetResponse;
import com.actionow.common.mq.publisher.CanvasMessagePublisher;
import com.actionow.common.mq.publisher.CanvasMessagePublisher.EntityItem;
import com.actionow.common.mq.publisher.CanvasMessagePublisher.RelatedEntity;
import com.actionow.common.mq.message.CollabEntityChangeEvent;
import com.actionow.project.publisher.EntityChangeEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 分镜服务实现
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoryboardServiceImpl implements StoryboardService {

    private final StoryboardMapper storyboardMapper;
    private final EpisodeService episodeService;
    private final UserInfoHelper userInfoHelper;
    private final AssetService assetService;
    private final CanvasMessagePublisher canvasMessagePublisher;
    private final EntityChangeEventPublisher entityChangeEventPublisher;
    private final VersionService<Storyboard, StoryboardVersionDetailResponse> storyboardVersionService;
    private final EntityRelationService entityRelationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StoryboardDetailResponse create(String episodeId, CreateStoryboardRequest request, String userId) {
        return create(episodeId, request, userId, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StoryboardDetailResponse create(String episodeId, CreateStoryboardRequest request, String userId, boolean skipCanvasSync) {
        // 验证剧集存在
        Episode episode = episodeService.findById(episodeId)
                .orElseThrow(() -> new BusinessException(ResultCode.EPISODE_NOT_FOUND));

        // 确定序号
        int sequence = request.getSequence() != null
                ? request.getSequence()
                : storyboardMapper.getMaxSequence(episodeId) + 1;

        Storyboard storyboard = new Storyboard();
        storyboard.setId(UuidGenerator.generateUuidV7());
        storyboard.setWorkspaceId(episode.getWorkspaceId());
        storyboard.setScriptId(episode.getScriptId());
        storyboard.setEpisodeId(episodeId);
        storyboard.setTitle(request.getTitle());
        storyboard.setSynopsis(request.getSynopsis());
        storyboard.setSequence(sequence);
        storyboard.setDuration(request.getDuration());
        // 合并 visualDesc 和 audioDesc（仅镜头属性，不含实体引用）
        storyboard.setVisualDesc(EntityDefaults.mergeStoryboardVisualDesc(request.getVisualDesc()));
        storyboard.setAudioDesc(EntityDefaults.mergeStoryboardAudioDesc(request.getAudioDesc()));
        storyboard.setStatus(ProjectConstants.StoryboardStatus.DRAFT);
        storyboard.setVersion(1);
        storyboard.setCreatedBy(userId);

        storyboardMapper.insert(storyboard);

        // 创建实体关系
        createStoryboardRelations(storyboard.getId(), request, episode.getWorkspaceId(), userId);

        // 创建初始版本快照 (V1)
        storyboardVersionService.createVersionSnapshot(storyboard, "创建分镜", userId);

        log.info("分镜创建成功: storyboardId={}, episodeId={}, skipCanvasSync={}", storyboard.getId(), episodeId, skipCanvasSync);

        // 发布Canvas同步消息（未跳过同步时）
        if (!skipCanvasSync) {
            List<RelatedEntity> relatedEntities = new ArrayList<>();
            canvasMessagePublisher.publishEntityChange(
                    ProjectConstants.EntityType.STORYBOARD,
                    storyboard.getId(),
                    storyboard.getScriptId(),
                    ProjectConstants.EntityType.EPISODE,
                    episodeId,
                    episode.getWorkspaceId(),
                    "CREATED",
                    toEntityDataMap(storyboard),
                    relatedEntities,
                    false
            );
        }

        // 发布协作事件
        entityChangeEventPublisher.publishEntityCreated(
                CollabEntityChangeEvent.EntityType.STORYBOARD,
                storyboard.getId(),
                storyboard.getScriptId(),
                toEntityDataMap(storyboard)
        );

        return getById(storyboard.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<StoryboardDetailResponse> batchCreate(String episodeId, List<CreateStoryboardRequest> requests, String userId) {
        // 验证剧集存在
        Episode episode = episodeService.findById(episodeId)
                .orElseThrow(() -> new BusinessException(ResultCode.EPISODE_NOT_FOUND));

        int currentMaxSequence = storyboardMapper.getMaxSequence(episodeId);
        List<StoryboardDetailResponse> responses = new ArrayList<>();
        List<EntityItem> entityItems = new ArrayList<>();

        for (int i = 0; i < requests.size(); i++) {
            CreateStoryboardRequest request = requests.get(i);
            int sequence = request.getSequence() != null
                    ? request.getSequence()
                    : currentMaxSequence + i + 1;

            Storyboard storyboard = new Storyboard();
            storyboard.setId(UuidGenerator.generateUuidV7());
            storyboard.setWorkspaceId(episode.getWorkspaceId());
            storyboard.setScriptId(episode.getScriptId());
            storyboard.setEpisodeId(episodeId);
            storyboard.setTitle(request.getTitle());
            storyboard.setSynopsis(request.getSynopsis());
            storyboard.setSequence(sequence);
            storyboard.setDuration(request.getDuration());
            storyboard.setVisualDesc(EntityDefaults.mergeStoryboardVisualDesc(request.getVisualDesc()));
            storyboard.setAudioDesc(EntityDefaults.mergeStoryboardAudioDesc(request.getAudioDesc()));
            storyboard.setStatus(ProjectConstants.StoryboardStatus.DRAFT);
            storyboard.setVersion(1);
            storyboard.setCreatedBy(userId);

            storyboardMapper.insert(storyboard);

            // 创建实体关系
            createStoryboardRelations(storyboard.getId(), request, episode.getWorkspaceId(), userId);

            // 创建初始版本快照 (V1)
            storyboardVersionService.createVersionSnapshot(storyboard, "批量创建分镜", userId);

            responses.add(getById(storyboard.getId()));

            // 收集实体信息用于批量消息
            entityItems.add(new EntityItem(
                    ProjectConstants.EntityType.STORYBOARD,
                    storyboard.getId(),
                    toEntityDataMap(storyboard)
            ));
        }

        // 发布批量Canvas同步消息
        if (!entityItems.isEmpty()) {
            canvasMessagePublisher.publishBatchEntityChange(
                    episode.getScriptId(),
                    episode.getWorkspaceId(),
                    "CREATED",
                    entityItems
            );
        }

        log.info("批量创建分镜成功: episodeId={}, count={}", episodeId, requests.size());

        return responses;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StoryboardDetailResponse update(String storyboardId, UpdateStoryboardRequest request, String userId) {
        Storyboard storyboard = getStoryboardOrThrow(storyboardId);

        // 获取保存模式，默认为 NEW_VERSION
        String saveMode = request.getSaveMode();
        if (saveMode == null) {
            saveMode = ProjectConstants.SaveMode.NEW_VERSION;
        }

        // NEW_ENTITY 模式：另存为新实体
        if (ProjectConstants.SaveMode.NEW_ENTITY.equals(saveMode)) {
            CreateStoryboardRequest newRequest = new CreateStoryboardRequest();
            newRequest.setEpisodeId(storyboard.getEpisodeId());
            newRequest.setTitle(request.getTitle() != null ? request.getTitle() : storyboard.getTitle());
            newRequest.setSynopsis(request.getSynopsis() != null ? request.getSynopsis() : storyboard.getSynopsis());
            newRequest.setDuration(request.getDuration() != null ? request.getDuration() : storyboard.getDuration());
            newRequest.setVisualDesc(request.getVisualDesc() != null ? request.getVisualDesc() : storyboard.getVisualDesc());
            newRequest.setAudioDesc(request.getAudioDesc() != null ? request.getAudioDesc() : storyboard.getAudioDesc());
            newRequest.setExtraInfo(request.getExtraInfo() != null ? request.getExtraInfo() : storyboard.getExtraInfo());
            // 复制关系
            newRequest.setSceneId(request.getSceneId());
            newRequest.setSceneOverride(request.getSceneOverride());
            newRequest.setStyleId(request.getStyleId());
            newRequest.setCharacters(request.getCharacters());
            newRequest.setProps(request.getProps());
            newRequest.setDialogues(request.getDialogues());
            return create(storyboard.getEpisodeId(), newRequest, userId);
        }

        // 检测实际变更，生成变更摘要
        StringBuilder changes = new StringBuilder();
        if (request.getTitle() != null && !request.getTitle().equals(storyboard.getTitle())) {
            changes.append("标题");
        }
        if (request.getSynopsis() != null && !request.getSynopsis().equals(storyboard.getSynopsis())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("简介");
        }
        if (request.getVisualDesc() != null && !request.getVisualDesc().equals(storyboard.getVisualDesc())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("画面描述");
        }
        if (request.getAudioDesc() != null && !request.getAudioDesc().equals(storyboard.getAudioDesc())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("音频描述");
        }
        if (request.getDuration() != null && !request.getDuration().equals(storyboard.getDuration())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("时长");
        }
        if (request.getExtraInfo() != null && !request.getExtraInfo().equals(storyboard.getExtraInfo())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("扩展信息");
        }
        if (request.getStatus() != null && !request.getStatus().equals(storyboard.getStatus())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("状态");
        }
        if (request.getSequence() != null && !request.getSequence().equals(storyboard.getSequence())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("序号");
        }

        // 1. 更新基本数据
        if (request.getTitle() != null) {
            storyboard.setTitle(request.getTitle());
        }
        if (request.getSynopsis() != null) {
            storyboard.setSynopsis(request.getSynopsis());
        }
        if (request.getDuration() != null) {
            storyboard.setDuration(request.getDuration());
        }
        if (request.getVisualDesc() != null) {
            storyboard.setVisualDesc(request.getVisualDesc());
        }
        // visualDescPatch: merge 语义
        if (request.getVisualDescPatch() != null && !request.getVisualDescPatch().isEmpty()) {
            Map<String, Object> existing = storyboard.getVisualDesc();
            if (existing == null) existing = new HashMap<>();
            existing.putAll(request.getVisualDescPatch());
            storyboard.setVisualDesc(existing);
        }
        if (request.getAudioDesc() != null) {
            storyboard.setAudioDesc(request.getAudioDesc());
        }
        // audioDescPatch: merge 语义
        if (request.getAudioDescPatch() != null && !request.getAudioDescPatch().isEmpty()) {
            Map<String, Object> existing = storyboard.getAudioDesc();
            if (existing == null) existing = new HashMap<>();
            existing.putAll(request.getAudioDescPatch());
            storyboard.setAudioDesc(existing);
        }
        if (request.getExtraInfo() != null) {
            storyboard.setExtraInfo(request.getExtraInfo());
        }
        // extraInfoPatch: merge 语义
        if (request.getExtraInfoPatch() != null && !request.getExtraInfoPatch().isEmpty()) {
            Map<String, Object> existing = storyboard.getExtraInfo();
            if (existing == null) existing = new HashMap<>();
            existing.putAll(request.getExtraInfoPatch());
            storyboard.setExtraInfo(existing);
        }
        if (request.getStatus() != null) {
            storyboard.setStatus(request.getStatus());
        }
        if (request.getSequence() != null) {
            storyboard.setSequence(request.getSequence());
        }
        if (request.getCoverAssetId() != null) {
            storyboard.setCoverAssetId(request.getCoverAssetId());
        }
        storyboard.setUpdatedBy(userId);

        int rows = storyboardMapper.updateById(storyboard);
        if (rows == 0) {
            log.warn("分镜更新失败（并发冲突）: storyboardId={}, version={}", storyboardId, storyboard.getVersion());
            throw new BusinessException(ResultCode.CONCURRENT_OPERATION);
        }

        // 2. 更新实体关系
        updateStoryboardRelations(storyboardId, request, storyboard.getWorkspaceId(), userId);

        // 3. NEW_VERSION 模式：仅在有实际变更时创建版本快照
        if (ProjectConstants.SaveMode.NEW_VERSION.equals(saveMode) && !changes.isEmpty()) {
            String changeSummary = "更新" + changes;
            storyboard = getStoryboardOrThrow(storyboardId);
            storyboardVersionService.createVersionSnapshot(storyboard, changeSummary, userId);
        }

        log.info("分镜更新成功: storyboardId={}, versionNumber={}, saveMode={}", storyboardId, storyboard.getVersionNumber(), saveMode);

        // 发布Canvas同步消息
        canvasMessagePublisher.publishEntityChange(
                ProjectConstants.EntityType.STORYBOARD,
                storyboard.getId(),
                storyboard.getScriptId(),
                ProjectConstants.EntityType.EPISODE,
                storyboard.getEpisodeId(),
                storyboard.getWorkspaceId(),
                "UPDATED",
                toEntityDataMap(storyboard)
        );

        // 发布协作事件
        entityChangeEventPublisher.publishEntityUpdated(
                CollabEntityChangeEvent.EntityType.STORYBOARD,
                storyboard.getId(),
                storyboard.getScriptId(),
                null,
                toEntityDataMap(storyboard)
        );

        return getById(storyboardId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String storyboardId, String userId) {
        Storyboard storyboard = getStoryboardOrThrow(storyboardId);

        // 删除所有关联关系
        entityRelationService.deleteBySource(ProjectConstants.EntityType.STORYBOARD, storyboardId, userId);

        // 使用 MyBatis-Plus 的 deleteById，会自动转换为逻辑删除
        storyboardMapper.deleteById(storyboardId);

        log.info("分镜删除成功: storyboardId={}", storyboardId);

        // 发布Canvas同步消息
        canvasMessagePublisher.publishEntityChange(
                ProjectConstants.EntityType.STORYBOARD,
                storyboard.getId(),
                storyboard.getScriptId(),
                ProjectConstants.EntityType.EPISODE,
                storyboard.getEpisodeId(),
                storyboard.getWorkspaceId(),
                "DELETED",
                null
        );

        // 发布协作事件
        entityChangeEventPublisher.publishEntityDeleted(
                CollabEntityChangeEvent.EntityType.STORYBOARD,
                storyboard.getId(),
                storyboard.getScriptId()
        );
    }

    @Override
    public StoryboardDetailResponse getById(String storyboardId) {
        Storyboard storyboard = getStoryboardOrThrow(storyboardId);
        StoryboardDetailResponse response = StoryboardDetailResponse.fromEntity(storyboard);

        // 填充封面URL
        if (storyboard.getCoverAssetId() != null && !storyboard.getCoverAssetId().isEmpty()) {
            try {
                AssetResponse asset = assetService.getById(storyboard.getCoverAssetId());
                if (asset != null) {
                    response.setCoverUrl(asset.getThumbnailUrl() != null ? asset.getThumbnailUrl() : asset.getFileUrl());
                }
            } catch (Exception e) {
                log.warn("获取分镜封面素材失败: storyboardId={}, coverAssetId={}", storyboardId, storyboard.getCoverAssetId(), e);
            }
        }

        // 填充创建者信息
        if (storyboard.getCreatedBy() != null) {
            UserBasicInfo userInfo = userInfoHelper.getUserInfo(storyboard.getCreatedBy());
            if (userInfo != null) {
                response.setCreatedByUsername(userInfo.getUsername());
                response.setCreatedByNickname(userInfo.getNickname());
            }
        }

        // 填充实体关系
        StoryboardRelationsResponse relations = entityRelationService.getStoryboardRelations(storyboardId);
        response.setScene(relations.getScene());
        response.setCharacters(relations.getCharacters());
        response.setProps(relations.getProps());
        response.setDialogues(relations.getDialogues());
        response.setStyleId(relations.getStyleId());
        response.setStyleName(relations.getStyleName());

        return response;
    }

    @Override
    public Optional<Storyboard> findById(String storyboardId) {
        Storyboard storyboard = storyboardMapper.selectById(storyboardId);
        if (storyboard == null || storyboard.getDeleted() == CommonConstants.DELETED) {
            return Optional.empty();
        }
        return Optional.of(storyboard);
    }

    @Override
    public List<StoryboardListResponse> listByEpisode(String episodeId) {
        List<Storyboard> storyboards = storyboardMapper.selectByEpisodeId(episodeId);
        return convertToListResponses(storyboards);
    }

    @Override
    public List<StoryboardListResponse> listByEpisode(String episodeId, String keyword, Integer limit) {
        LambdaQueryWrapper<Storyboard> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Storyboard::getEpisodeId, episodeId)
                .eq(Storyboard::getDeleted, CommonConstants.NOT_DELETED);

        // 数据库级模糊搜索
        if (keyword != null && !keyword.isBlank()) {
            String kw = "%" + keyword + "%";
            wrapper.and(w -> w.like(Storyboard::getTitle, keyword)
                    .or().like(Storyboard::getSynopsis, keyword)
                    .or().apply("visual_desc::text LIKE {0}", kw)
                    .or().apply("audio_desc::text LIKE {0}", kw)
                    .or().apply("extra_info::text LIKE {0}", kw));
        }

        wrapper.orderByAsc(Storyboard::getSequence);

        // 数量限制
        if (limit != null && limit > 0) {
            wrapper.last("LIMIT " + limit);
        }

        List<Storyboard> storyboards = storyboardMapper.selectList(wrapper);
        return convertToListResponses(storyboards);
    }

    @Override
    public List<StoryboardListResponse> listByScript(String scriptId) {
        List<Storyboard> storyboards = storyboardMapper.selectByScriptId(scriptId);
        return convertToListResponses(storyboards);
    }

    @Override
    public Page<StoryboardListResponse> queryStoryboards(StoryboardQueryRequest request, String workspaceId) {
        Page<Storyboard> page = new Page<>(request.getPageNum(), request.getPageSize());

        LambdaQueryWrapper<Storyboard> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Storyboard::getWorkspaceId, workspaceId)
                .eq(Storyboard::getDeleted, CommonConstants.NOT_DELETED);

        if (StringUtils.hasText(request.getScriptId())) {
            wrapper.eq(Storyboard::getScriptId, request.getScriptId());
        }
        if (StringUtils.hasText(request.getEpisodeId())) {
            wrapper.eq(Storyboard::getEpisodeId, request.getEpisodeId());
        }
        if (StringUtils.hasText(request.getStatus())) {
            wrapper.eq(Storyboard::getStatus, request.getStatus());
        }
        if (StringUtils.hasText(request.getKeyword())) {
            String kw = "%" + request.getKeyword() + "%";
            wrapper.and(w -> w.like(Storyboard::getTitle, request.getKeyword())
                    .or()
                    .like(Storyboard::getSynopsis, request.getKeyword())
                    .or()
                    .apply("visual_desc::text LIKE {0}", kw)
                    .or()
                    .apply("audio_desc::text LIKE {0}", kw)
                    .or()
                    .apply("extra_info::text LIKE {0}", kw));
        }

        String orderBy = request.getOrderBy();
        boolean isAsc = "asc".equalsIgnoreCase(request.getOrderDir());
        switch (orderBy) {
            case "title" -> wrapper.orderBy(true, isAsc, Storyboard::getTitle);
            case "created_at" -> wrapper.orderBy(true, isAsc, Storyboard::getCreatedAt);
            case "updated_at" -> wrapper.orderBy(true, isAsc, Storyboard::getUpdatedAt);
            default -> wrapper.orderBy(true, isAsc, Storyboard::getSequence);
        }

        Page<Storyboard> resultPage = storyboardMapper.selectPage(page, wrapper);

        Page<StoryboardListResponse> responsePage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        responsePage.setRecords(convertToListResponses(resultPage.getRecords()));

        return responsePage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reorder(String episodeId, List<String> storyboardIds, String userId) {
        for (int i = 0; i < storyboardIds.size(); i++) {
            Storyboard storyboard = storyboardMapper.selectById(storyboardIds.get(i));
            if (storyboard != null && storyboard.getEpisodeId().equals(episodeId)) {
                storyboard.setSequence(i + 1);
                storyboardMapper.updateById(storyboard);
            }
        }

        log.info("分镜顺序调整: episodeId={}", episodeId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(String storyboardId, String status, String userId) {
        Storyboard storyboard = getStoryboardOrThrow(storyboardId);

        if (!status.equals(storyboard.getStatus())) {
            String changeSummary = String.format("状态变更: %s → %s", storyboard.getStatus(), status);
            storyboardVersionService.createVersionSnapshot(storyboard, changeSummary, userId);
            storyboard = getStoryboardOrThrow(storyboardId);
        }

        storyboard.setStatus(status);
        storyboard.setUpdatedBy(userId);

        int rows = storyboardMapper.updateById(storyboard);
        if (rows == 0) {
            throw new BusinessException(ResultCode.CONCURRENT_OPERATION);
        }

        log.info("分镜状态更新: storyboardId={}, status={}, versionNumber={}", storyboardId, status, storyboard.getVersionNumber());
    }

    @Override
    public void setCover(String storyboardId, String assetId, String userId) {
        Storyboard storyboard = getStoryboardOrThrow(storyboardId);
        storyboard.setCoverAssetId(assetId);
        storyboardMapper.updateById(storyboard);

        log.info("分镜封面设置成功: storyboardId={}, assetId={}", storyboardId, assetId);
    }

    // ==================== 关系管理辅助方法 ====================

    /**
     * 创建分镜的所有实体关系
     */
    private void createStoryboardRelations(String storyboardId, CreateStoryboardRequest request, String workspaceId, String userId) {
        // 创建场景关系
        if (StringUtils.hasText(request.getSceneId())) {
            Map<String, Object> extraInfo = new HashMap<>();
            if (request.getSceneOverride() != null) {
                extraInfo.put("sceneOverride", request.getSceneOverride());
            }
            entityRelationService.createRelation(CreateEntityRelationRequest.builder()
                    .sourceType(ProjectConstants.EntityType.STORYBOARD)
                    .sourceId(storyboardId)
                    .targetType(ProjectConstants.EntityType.SCENE)
                    .targetId(request.getSceneId())
                    .relationType(ProjectConstants.RelationType.TAKES_PLACE_IN)
                    .extraInfo(extraInfo)
                    .build(), workspaceId, userId);
        }

        // 创建风格关系
        if (StringUtils.hasText(request.getStyleId())) {
            entityRelationService.createRelation(CreateEntityRelationRequest.builder()
                    .sourceType(ProjectConstants.EntityType.STORYBOARD)
                    .sourceId(storyboardId)
                    .targetType(ProjectConstants.EntityType.STYLE)
                    .targetId(request.getStyleId())
                    .relationType(ProjectConstants.RelationType.STYLED_BY)
                    .build(), workspaceId, userId);
        }

        // 创建角色出现关系
        if (request.getCharacters() != null) {
            for (int i = 0; i < request.getCharacters().size(); i++) {
                CreateStoryboardRequest.CharacterAppearance ca = request.getCharacters().get(i);
                if (StringUtils.hasText(ca.getCharacterId())) {
                    Map<String, Object> extraInfo = new HashMap<>();
                    if (ca.getPosition() != null) extraInfo.put("position", ca.getPosition());
                    if (ca.getPositionDetail() != null) extraInfo.put("positionDetail", ca.getPositionDetail());
                    if (ca.getAction() != null) extraInfo.put("action", ca.getAction());
                    if (ca.getExpression() != null) extraInfo.put("expression", ca.getExpression());
                    if (ca.getOutfitOverride() != null) extraInfo.put("outfitOverride", ca.getOutfitOverride());

                    entityRelationService.createRelation(CreateEntityRelationRequest.builder()
                            .sourceType(ProjectConstants.EntityType.STORYBOARD)
                            .sourceId(storyboardId)
                            .targetType(ProjectConstants.EntityType.CHARACTER)
                            .targetId(ca.getCharacterId())
                            .relationType(ProjectConstants.RelationType.APPEARS_IN)
                            .sequence(ca.getSequence() != null ? ca.getSequence() : i)
                            .extraInfo(extraInfo)
                            .build(), workspaceId, userId);
                }
            }
        }

        // 创建道具使用关系
        if (request.getProps() != null) {
            for (int i = 0; i < request.getProps().size(); i++) {
                CreateStoryboardRequest.PropUsage pu = request.getProps().get(i);
                if (StringUtils.hasText(pu.getPropId())) {
                    Map<String, Object> extraInfo = new HashMap<>();
                    if (pu.getPosition() != null) extraInfo.put("position", pu.getPosition());
                    if (pu.getInteraction() != null) extraInfo.put("interaction", pu.getInteraction());
                    if (pu.getState() != null) extraInfo.put("state", pu.getState());

                    entityRelationService.createRelation(CreateEntityRelationRequest.builder()
                            .sourceType(ProjectConstants.EntityType.STORYBOARD)
                            .sourceId(storyboardId)
                            .targetType(ProjectConstants.EntityType.PROP)
                            .targetId(pu.getPropId())
                            .relationType(ProjectConstants.RelationType.USES)
                            .sequence(pu.getSequence() != null ? pu.getSequence() : i)
                            .extraInfo(extraInfo)
                            .build(), workspaceId, userId);
                }
            }
        }

        // 创建对白关系
        if (request.getDialogues() != null) {
            for (int i = 0; i < request.getDialogues().size(); i++) {
                CreateStoryboardRequest.DialogueLine dl = request.getDialogues().get(i);
                if (StringUtils.hasText(dl.getCharacterId())) {
                    Map<String, Object> extraInfo = new HashMap<>();
                    extraInfo.put("dialogueIndex", i);
                    if (dl.getText() != null) extraInfo.put("text", dl.getText());
                    if (dl.getEmotion() != null) extraInfo.put("emotion", dl.getEmotion());
                    if (dl.getVoiceStyle() != null) extraInfo.put("voiceStyle", dl.getVoiceStyle());
                    if (dl.getTiming() != null) extraInfo.put("timing", dl.getTiming());

                    entityRelationService.createRelation(CreateEntityRelationRequest.builder()
                            .sourceType(ProjectConstants.EntityType.STORYBOARD)
                            .sourceId(storyboardId)
                            .targetType(ProjectConstants.EntityType.CHARACTER)
                            .targetId(dl.getCharacterId())
                            .relationType(ProjectConstants.RelationType.SPEAKS_IN)
                            .sequence(dl.getSequence() != null ? dl.getSequence() : i)
                            .extraInfo(extraInfo)
                            .build(), workspaceId, userId);
                }
            }
        }
    }

    /**
     * 更新分镜的实体关系
     */
    private void updateStoryboardRelations(String storyboardId, UpdateStoryboardRequest request, String workspaceId, String userId) {
        // 更新场景关系
        if (request.getSceneId() != null) {
            // 删除现有场景关系
            entityRelationService.deleteBySourceAndRelationType(
                    ProjectConstants.EntityType.STORYBOARD, storyboardId,
                    ProjectConstants.RelationType.TAKES_PLACE_IN, userId);

            // 创建新的场景关系
            if (!request.getSceneId().isEmpty()) {
                Map<String, Object> extraInfo = new HashMap<>();
                if (request.getSceneOverride() != null) {
                    extraInfo.put("sceneOverride", request.getSceneOverride());
                }
                // sceneOverridePatch 在新建场景关系时也作为初始值
                if (request.getSceneOverridePatch() != null && !request.getSceneOverridePatch().isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> existing = (Map<String, Object>) extraInfo.getOrDefault("sceneOverride", new HashMap<>());
                    existing.putAll(request.getSceneOverridePatch());
                    extraInfo.put("sceneOverride", existing);
                }
                entityRelationService.createRelation(CreateEntityRelationRequest.builder()
                        .sourceType(ProjectConstants.EntityType.STORYBOARD)
                        .sourceId(storyboardId)
                        .targetType(ProjectConstants.EntityType.SCENE)
                        .targetId(request.getSceneId())
                        .relationType(ProjectConstants.RelationType.TAKES_PLACE_IN)
                        .extraInfo(extraInfo)
                        .build(), workspaceId, userId);
            }
        } else if (request.getSceneOverridePatch() != null && !request.getSceneOverridePatch().isEmpty()) {
            // 仅更新现有场景关系的 sceneOverride（merge 语义）
            var sceneRelations = entityRelationService.listBySourceAndRelationType(
                    ProjectConstants.EntityType.STORYBOARD, storyboardId,
                    ProjectConstants.RelationType.TAKES_PLACE_IN);
            if (!sceneRelations.isEmpty()) {
                var relation = sceneRelations.get(0);
                Map<String, Object> patchExtraInfo = new HashMap<>();
                patchExtraInfo.put("sceneOverride", request.getSceneOverridePatch());
                entityRelationService.updateRelation(relation.getId(),
                        UpdateEntityRelationRequest.builder()
                                .extraInfo(patchExtraInfo)
                                .mergeExtraInfo(true)
                                .build(), userId);
            }
        }

        // 更新风格关系
        if (request.getStyleId() != null) {
            entityRelationService.deleteBySourceAndRelationType(
                    ProjectConstants.EntityType.STORYBOARD, storyboardId,
                    ProjectConstants.RelationType.STYLED_BY, userId);

            if (!request.getStyleId().isEmpty()) {
                entityRelationService.createRelation(CreateEntityRelationRequest.builder()
                        .sourceType(ProjectConstants.EntityType.STORYBOARD)
                        .sourceId(storyboardId)
                        .targetType(ProjectConstants.EntityType.STYLE)
                        .targetId(request.getStyleId())
                        .relationType(ProjectConstants.RelationType.STYLED_BY)
                        .build(), workspaceId, userId);
            }
        }

        // 替换角色列表
        if (request.getCharacters() != null) {
            entityRelationService.deleteBySourceAndRelationType(
                    ProjectConstants.EntityType.STORYBOARD, storyboardId,
                    ProjectConstants.RelationType.APPEARS_IN, userId);

            for (int i = 0; i < request.getCharacters().size(); i++) {
                CreateStoryboardRequest.CharacterAppearance ca = request.getCharacters().get(i);
                if (StringUtils.hasText(ca.getCharacterId())) {
                    Map<String, Object> extraInfo = new HashMap<>();
                    if (ca.getPosition() != null) extraInfo.put("position", ca.getPosition());
                    if (ca.getPositionDetail() != null) extraInfo.put("positionDetail", ca.getPositionDetail());
                    if (ca.getAction() != null) extraInfo.put("action", ca.getAction());
                    if (ca.getExpression() != null) extraInfo.put("expression", ca.getExpression());
                    if (ca.getOutfitOverride() != null) extraInfo.put("outfitOverride", ca.getOutfitOverride());

                    entityRelationService.createRelation(CreateEntityRelationRequest.builder()
                            .sourceType(ProjectConstants.EntityType.STORYBOARD)
                            .sourceId(storyboardId)
                            .targetType(ProjectConstants.EntityType.CHARACTER)
                            .targetId(ca.getCharacterId())
                            .relationType(ProjectConstants.RelationType.APPEARS_IN)
                            .sequence(ca.getSequence() != null ? ca.getSequence() : i)
                            .extraInfo(extraInfo)
                            .build(), workspaceId, userId);
                }
            }
        }

        // 添加角色
        if (request.getAddCharacters() != null) {
            for (CreateStoryboardRequest.CharacterAppearance ca : request.getAddCharacters()) {
                if (StringUtils.hasText(ca.getCharacterId())) {
                    Map<String, Object> extraInfo = new HashMap<>();
                    if (ca.getPosition() != null) extraInfo.put("position", ca.getPosition());
                    if (ca.getPositionDetail() != null) extraInfo.put("positionDetail", ca.getPositionDetail());
                    if (ca.getAction() != null) extraInfo.put("action", ca.getAction());
                    if (ca.getExpression() != null) extraInfo.put("expression", ca.getExpression());
                    if (ca.getOutfitOverride() != null) extraInfo.put("outfitOverride", ca.getOutfitOverride());

                    entityRelationService.getOrCreate(CreateEntityRelationRequest.builder()
                            .sourceType(ProjectConstants.EntityType.STORYBOARD)
                            .sourceId(storyboardId)
                            .targetType(ProjectConstants.EntityType.CHARACTER)
                            .targetId(ca.getCharacterId())
                            .relationType(ProjectConstants.RelationType.APPEARS_IN)
                            .sequence(ca.getSequence())
                            .extraInfo(extraInfo)
                            .build(), workspaceId, userId);
                }
            }
        }

        // 移除角色
        if (request.getRemoveCharacterIds() != null) {
            for (String characterId : request.getRemoveCharacterIds()) {
                entityRelationService.deleteRelation(
                        ProjectConstants.EntityType.STORYBOARD, storyboardId,
                        ProjectConstants.EntityType.CHARACTER, characterId,
                        ProjectConstants.RelationType.APPEARS_IN, userId);
            }
        }

        // 替换道具列表
        if (request.getProps() != null) {
            entityRelationService.deleteBySourceAndRelationType(
                    ProjectConstants.EntityType.STORYBOARD, storyboardId,
                    ProjectConstants.RelationType.USES, userId);

            for (int i = 0; i < request.getProps().size(); i++) {
                CreateStoryboardRequest.PropUsage pu = request.getProps().get(i);
                if (StringUtils.hasText(pu.getPropId())) {
                    Map<String, Object> extraInfo = new HashMap<>();
                    if (pu.getPosition() != null) extraInfo.put("position", pu.getPosition());
                    if (pu.getInteraction() != null) extraInfo.put("interaction", pu.getInteraction());
                    if (pu.getState() != null) extraInfo.put("state", pu.getState());

                    entityRelationService.createRelation(CreateEntityRelationRequest.builder()
                            .sourceType(ProjectConstants.EntityType.STORYBOARD)
                            .sourceId(storyboardId)
                            .targetType(ProjectConstants.EntityType.PROP)
                            .targetId(pu.getPropId())
                            .relationType(ProjectConstants.RelationType.USES)
                            .sequence(pu.getSequence() != null ? pu.getSequence() : i)
                            .extraInfo(extraInfo)
                            .build(), workspaceId, userId);
                }
            }
        }

        // 添加道具
        if (request.getAddProps() != null) {
            for (CreateStoryboardRequest.PropUsage pu : request.getAddProps()) {
                if (StringUtils.hasText(pu.getPropId())) {
                    Map<String, Object> extraInfo = new HashMap<>();
                    if (pu.getPosition() != null) extraInfo.put("position", pu.getPosition());
                    if (pu.getInteraction() != null) extraInfo.put("interaction", pu.getInteraction());
                    if (pu.getState() != null) extraInfo.put("state", pu.getState());

                    entityRelationService.getOrCreate(CreateEntityRelationRequest.builder()
                            .sourceType(ProjectConstants.EntityType.STORYBOARD)
                            .sourceId(storyboardId)
                            .targetType(ProjectConstants.EntityType.PROP)
                            .targetId(pu.getPropId())
                            .relationType(ProjectConstants.RelationType.USES)
                            .sequence(pu.getSequence())
                            .extraInfo(extraInfo)
                            .build(), workspaceId, userId);
                }
            }
        }

        // 移除道具
        if (request.getRemovePropIds() != null) {
            for (String propId : request.getRemovePropIds()) {
                entityRelationService.deleteRelation(
                        ProjectConstants.EntityType.STORYBOARD, storyboardId,
                        ProjectConstants.EntityType.PROP, propId,
                        ProjectConstants.RelationType.USES, userId);
            }
        }

        // 替换对白列表
        if (request.getDialogues() != null) {
            entityRelationService.deleteBySourceAndRelationType(
                    ProjectConstants.EntityType.STORYBOARD, storyboardId,
                    ProjectConstants.RelationType.SPEAKS_IN, userId);

            for (int i = 0; i < request.getDialogues().size(); i++) {
                CreateStoryboardRequest.DialogueLine dl = request.getDialogues().get(i);
                if (StringUtils.hasText(dl.getCharacterId())) {
                    Map<String, Object> extraInfo = new HashMap<>();
                    extraInfo.put("dialogueIndex", i);
                    if (dl.getText() != null) extraInfo.put("text", dl.getText());
                    if (dl.getEmotion() != null) extraInfo.put("emotion", dl.getEmotion());
                    if (dl.getVoiceStyle() != null) extraInfo.put("voiceStyle", dl.getVoiceStyle());
                    if (dl.getTiming() != null) extraInfo.put("timing", dl.getTiming());

                    entityRelationService.createRelation(CreateEntityRelationRequest.builder()
                            .sourceType(ProjectConstants.EntityType.STORYBOARD)
                            .sourceId(storyboardId)
                            .targetType(ProjectConstants.EntityType.CHARACTER)
                            .targetId(dl.getCharacterId())
                            .relationType(ProjectConstants.RelationType.SPEAKS_IN)
                            .sequence(dl.getSequence() != null ? dl.getSequence() : i)
                            .extraInfo(extraInfo)
                            .build(), workspaceId, userId);
                }
            }
        }

        // 添加对白
        if (request.getAddDialogues() != null) {
            for (CreateStoryboardRequest.DialogueLine dl : request.getAddDialogues()) {
                if (StringUtils.hasText(dl.getCharacterId())) {
                    Map<String, Object> extraInfo = new HashMap<>();
                    if (dl.getText() != null) extraInfo.put("text", dl.getText());
                    if (dl.getEmotion() != null) extraInfo.put("emotion", dl.getEmotion());
                    if (dl.getVoiceStyle() != null) extraInfo.put("voiceStyle", dl.getVoiceStyle());
                    if (dl.getTiming() != null) extraInfo.put("timing", dl.getTiming());

                    entityRelationService.createRelation(CreateEntityRelationRequest.builder()
                            .sourceType(ProjectConstants.EntityType.STORYBOARD)
                            .sourceId(storyboardId)
                            .targetType(ProjectConstants.EntityType.CHARACTER)
                            .targetId(dl.getCharacterId())
                            .relationType(ProjectConstants.RelationType.SPEAKS_IN)
                            .sequence(dl.getSequence())
                            .extraInfo(extraInfo)
                            .build(), workspaceId, userId);
                }
            }
        }

        // 移除对白（按角色ID）
        if (request.getRemoveDialogueCharacterIds() != null) {
            for (String characterId : request.getRemoveDialogueCharacterIds()) {
                entityRelationService.deleteRelation(
                        ProjectConstants.EntityType.STORYBOARD, storyboardId,
                        ProjectConstants.EntityType.CHARACTER, characterId,
                        ProjectConstants.RelationType.SPEAKS_IN, userId);
            }
        }
    }

    private Storyboard getStoryboardOrThrow(String storyboardId) {
        Storyboard storyboard = storyboardMapper.selectById(storyboardId);
        if (storyboard == null || storyboard.getDeleted() == CommonConstants.DELETED) {
            throw new BusinessException(ResultCode.STORYBOARD_NOT_FOUND);
        }
        return storyboard;
    }

    private List<StoryboardListResponse> convertToListResponses(List<Storyboard> storyboards) {
        if (storyboards == null || storyboards.isEmpty()) {
            return List.of();
        }

        List<StoryboardListResponse> responses = storyboards.stream()
                .map(StoryboardListResponse::fromEntity)
                .collect(Collectors.toList());

        populateCoverUrl(storyboards, responses);
        populateUserInfo(responses);
        return responses;
    }

    private void populateCoverUrl(List<Storyboard> storyboards, List<StoryboardListResponse> responses) {
        Set<String> coverAssetIds = storyboards.stream()
                .map(Storyboard::getCoverAssetId)
                .filter(id -> id != null && !id.isEmpty())
                .collect(Collectors.toSet());
        if (coverAssetIds.isEmpty()) {
            return;
        }
        try {
            var assets = assetService.batchGet(new ArrayList<>(coverAssetIds));
            Map<String, String> assetUrlMap = assets.stream()
                    .collect(Collectors.toMap(
                            AssetResponse::getId,
                            a -> a.getThumbnailUrl() != null ? a.getThumbnailUrl() : a.getFileUrl(),
                            (a, b) -> a
                    ));
            for (int i = 0; i < storyboards.size(); i++) {
                String coverAssetId = storyboards.get(i).getCoverAssetId();
                if (coverAssetId != null && assetUrlMap.containsKey(coverAssetId)) {
                    responses.get(i).setCoverUrl(assetUrlMap.get(coverAssetId));
                }
            }
        } catch (Exception e) {
            log.warn("批量获取分镜封面素材失败", e);
        }
    }

    private void populateUserInfo(List<StoryboardListResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return;
        }
        Set<String> userIds = responses.stream()
                .map(StoryboardListResponse::getCreatedBy)
                .filter(id -> id != null && !id.isEmpty())
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return;
        }
        Map<String, UserBasicInfo> userInfoMap = userInfoHelper.batchGetUserInfo(userIds);
        responses.forEach(response -> {
            UserBasicInfo userInfo = userInfoMap.get(response.getCreatedBy());
            if (userInfo != null) {
                response.setCreatedByUsername(userInfo.getUsername());
                response.setCreatedByNickname(userInfo.getNickname());
            }
        });
    }

    private Map<String, Object> toEntityDataMap(Storyboard storyboard) {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("title", storyboard.getTitle());
        data.put("synopsis", storyboard.getSynopsis());
        data.put("sequence", storyboard.getSequence());
        data.put("duration", storyboard.getDuration());
        data.put("version", storyboard.getVersion());
        return data;
    }
}
