package com.actionow.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.common.core.constant.CommonConstants;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.id.UuidGenerator;
import com.actionow.common.core.result.ResultCode;
import com.actionow.project.constant.ProjectConstants;
import com.actionow.project.dto.CreateEpisodeRequest;
import com.actionow.project.dto.UpdateEpisodeRequest;
import com.actionow.project.dto.EpisodeQueryRequest;
import com.actionow.project.dto.EpisodeDetailResponse;
import com.actionow.project.dto.EpisodeListResponse;
import com.actionow.project.entity.Episode;
import com.actionow.project.entity.Script;
import com.actionow.project.mapper.EpisodeMapper;
import com.actionow.project.mapper.StoryboardMapper;
import com.actionow.project.service.AssetService;
import com.actionow.project.service.EpisodeService;
import com.actionow.project.service.ScriptService;
import com.actionow.project.service.UserInfoHelper;
import com.actionow.project.service.version.VersionService;
import com.actionow.project.dto.version.EpisodeVersionDetailResponse;
import com.actionow.project.feign.UserBasicInfo;
import com.actionow.common.mq.publisher.CanvasMessagePublisher;
import com.actionow.common.mq.message.CollabEntityChangeEvent;
import com.actionow.project.publisher.EntityChangeEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 剧集服务实现
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EpisodeServiceImpl implements EpisodeService {

    private final EpisodeMapper episodeMapper;
    private final StoryboardMapper storyboardMapper;
    private final ScriptService scriptService;
    private final UserInfoHelper userInfoHelper;
    private final CanvasMessagePublisher canvasMessagePublisher;
    private final EntityChangeEventPublisher entityChangeEventPublisher;
    private final VersionService<Episode, EpisodeVersionDetailResponse> episodeVersionService;
    private final AssetService assetService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EpisodeDetailResponse create(String scriptId, CreateEpisodeRequest request, String userId) {
        return create(scriptId, request, userId, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EpisodeDetailResponse create(String scriptId, CreateEpisodeRequest request, String userId, boolean skipCanvasSync) {
        // 验证剧本存在
        Script script = scriptService.findById(scriptId)
                .orElseThrow(() -> new BusinessException(ResultCode.SCRIPT_NOT_FOUND));

        // 确定序号
        int sequence = request.getSequence() != null
                ? request.getSequence()
                : episodeMapper.getMaxSequence(scriptId) + 1;

        Episode episode = new Episode();
        episode.setId(UuidGenerator.generateUuidV7());
        episode.setWorkspaceId(script.getWorkspaceId());
        episode.setScriptId(scriptId);
        episode.setTitle(request.getTitle());
        episode.setSynopsis(request.getSynopsis());
        episode.setSequence(sequence);
        episode.setStatus(ProjectConstants.ScriptStatus.DRAFT);
        episode.setVersion(1);
        episode.setCreatedBy(userId);

        episodeMapper.insert(episode);

        // 创建初始版本快照 (V1)
        episodeVersionService.createVersionSnapshot(episode, "创建剧集", userId);

        log.info("剧集创建成功: episodeId={}, scriptId={}, skipCanvasSync={}", episode.getId(), scriptId, skipCanvasSync);

        // 发布Canvas同步消息（未跳过同步时）
        if (!skipCanvasSync) {
            canvasMessagePublisher.publishEntityChange(
                    ProjectConstants.EntityType.EPISODE,
                    episode.getId(),
                    scriptId,
                    ProjectConstants.EntityType.SCRIPT,
                    scriptId,
                    script.getWorkspaceId(),
                    "CREATED",
                    toEntityDataMap(episode)
            );
        }

        // 发布协作事件
        entityChangeEventPublisher.publishEntityCreated(
                CollabEntityChangeEvent.EntityType.EPISODE,
                episode.getId(),
                scriptId,
                toEntityDataMap(episode)
        );

        EpisodeDetailResponse response = EpisodeDetailResponse.fromEntity(episode);
        response.setStoryboardCount(0);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<EpisodeDetailResponse> batchCreate(String scriptId, List<CreateEpisodeRequest> requests, String userId) {
        List<EpisodeDetailResponse> responses = new java.util.ArrayList<>();
        for (CreateEpisodeRequest request : requests) {
            responses.add(create(scriptId, request, userId, true));
        }
        log.info("批量创建剧集成功: scriptId={}, count={}", scriptId, requests.size());
        return responses;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EpisodeDetailResponse update(String episodeId, UpdateEpisodeRequest request, String userId) {
        Episode episode = getEpisodeOrThrow(episodeId);

        // 获取保存模式，默认为 NEW_VERSION
        String saveMode = request.getSaveMode();
        if (saveMode == null) {
            saveMode = ProjectConstants.SaveMode.NEW_VERSION;
        }

        // NEW_ENTITY 模式：另存为新实体
        if (ProjectConstants.SaveMode.NEW_ENTITY.equals(saveMode)) {
            CreateEpisodeRequest newRequest = new CreateEpisodeRequest();
            newRequest.setScriptId(episode.getScriptId());
            newRequest.setTitle(request.getTitle() != null ? request.getTitle() : episode.getTitle());
            newRequest.setSynopsis(request.getSynopsis() != null ? request.getSynopsis() : episode.getSynopsis());
            newRequest.setContent(request.getContent() != null ? request.getContent() : episode.getContent());
            newRequest.setCoverAssetId(request.getCoverAssetId() != null ? request.getCoverAssetId() : episode.getCoverAssetId());
            newRequest.setDocAssetId(request.getDocAssetId() != null ? request.getDocAssetId() : episode.getDocAssetId());
            newRequest.setExtraInfo(request.getExtraInfo() != null ? request.getExtraInfo() : episode.getExtraInfo());
            // 新实体序号追加到末尾
            return create(episode.getScriptId(), newRequest, userId);
        }

        // 检测实际变更，生成变更摘要
        StringBuilder changes = new StringBuilder();
        if (request.getTitle() != null && !request.getTitle().equals(episode.getTitle())) {
            changes.append("标题");
        }
        if (request.getSynopsis() != null && !request.getSynopsis().equals(episode.getSynopsis())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("简介");
        }
        if (request.getContent() != null && !request.getContent().equals(episode.getContent())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("内容");
        }
        if (request.getCoverAssetId() != null && !request.getCoverAssetId().equals(episode.getCoverAssetId())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("封面");
        }
        if (request.getDocAssetId() != null && !request.getDocAssetId().equals(episode.getDocAssetId())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("文档");
        }
        if (request.getExtraInfo() != null && !request.getExtraInfo().equals(episode.getExtraInfo())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("扩展信息");
        }
        if (request.getStatus() != null && !request.getStatus().equals(episode.getStatus())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("状态");
        }
        if (request.getSequence() != null && !request.getSequence().equals(episode.getSequence())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("序号");
        }

        // 1. 先更新数据
        if (request.getTitle() != null) {
            episode.setTitle(request.getTitle());
        }
        if (request.getSynopsis() != null) {
            episode.setSynopsis(request.getSynopsis());
        }
        if (request.getContent() != null) {
            episode.setContent(request.getContent());
        }
        if (request.getCoverAssetId() != null) {
            episode.setCoverAssetId(request.getCoverAssetId());
        }
        if (request.getDocAssetId() != null) {
            episode.setDocAssetId(request.getDocAssetId());
        }
        if (request.getExtraInfo() != null) {
            episode.setExtraInfo(request.getExtraInfo());
        }
        // extraInfoPatch: merge 语义（在全量替换之后应用）
        if (request.getExtraInfoPatch() != null && !request.getExtraInfoPatch().isEmpty()) {
            Map<String, Object> merged = episode.getExtraInfo() != null
                    ? new java.util.HashMap<>(episode.getExtraInfo()) : new java.util.HashMap<>();
            merged.putAll(request.getExtraInfoPatch());
            episode.setExtraInfo(merged);
            if (changes.isEmpty() || !changes.toString().contains("扩展信息")) {
                if (!changes.isEmpty()) changes.append("、");
                changes.append("扩展信息");
            }
        }
        if (request.getStatus() != null) {
            episode.setStatus(request.getStatus());
        }
        if (request.getSequence() != null) {
            episode.setSequence(request.getSequence());
        }
        episode.setUpdatedBy(userId);

        int rows = episodeMapper.updateById(episode);
        if (rows == 0) {
            log.warn("剧集更新失败（并发冲突）: episodeId={}, version={}", episodeId, episode.getVersion());
            throw new BusinessException(ResultCode.CONCURRENT_OPERATION);
        }

        // 2. NEW_VERSION 模式：仅在有实际变更时创建版本快照（保存更新后的数据）
        // OVERWRITE 模式：跳过版本快照创建
        if (ProjectConstants.SaveMode.NEW_VERSION.equals(saveMode) && !changes.isEmpty()) {
            String changeSummary = "更新" + changes;
            episode = getEpisodeOrThrow(episodeId);
            episodeVersionService.createVersionSnapshot(episode, changeSummary, userId);
        }

        log.info("剧集更新成功: episodeId={}, versionNumber={}, saveMode={}", episodeId, episode.getVersionNumber(), saveMode);

        // 发布Canvas同步消息
        canvasMessagePublisher.publishEntityChange(
                ProjectConstants.EntityType.EPISODE,
                episode.getId(),
                episode.getScriptId(),
                ProjectConstants.EntityType.SCRIPT,
                episode.getScriptId(),
                episode.getWorkspaceId(),
                "UPDATED",
                toEntityDataMap(episode)
        );

        // 发布协作事件
        entityChangeEventPublisher.publishEntityUpdated(
                CollabEntityChangeEvent.EntityType.EPISODE,
                episode.getId(),
                episode.getScriptId(),
                null,
                toEntityDataMap(episode)
        );

        EpisodeDetailResponse response = EpisodeDetailResponse.fromEntity(episode);
        response.setStoryboardCount(storyboardMapper.countByEpisodeId(episodeId));
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String episodeId, String userId) {
        // 先验证剧集存在
        Episode episode = getEpisodeOrThrow(episodeId);
        // 使用 MyBatis-Plus 的 deleteById，会自动转换为逻辑删除（UPDATE SET deleted=1）
        episodeMapper.deleteById(episodeId);

        log.info("剧集删除成功: episodeId={}", episodeId);

        // 发布Canvas同步消息
        canvasMessagePublisher.publishEntityChange(
                ProjectConstants.EntityType.EPISODE,
                episode.getId(),
                episode.getScriptId(),
                ProjectConstants.EntityType.SCRIPT,
                episode.getScriptId(),
                episode.getWorkspaceId(),
                "DELETED",
                null
        );

        // 发布协作事件
        entityChangeEventPublisher.publishEntityDeleted(
                CollabEntityChangeEvent.EntityType.EPISODE,
                episode.getId(),
                episode.getScriptId()
        );
    }

    @Override
    public EpisodeDetailResponse getById(String episodeId) {
        Episode episode = getEpisodeOrThrow(episodeId);
        EpisodeDetailResponse response = EpisodeDetailResponse.fromEntity(episode);
        response.setStoryboardCount(storyboardMapper.countByEpisodeId(episodeId));
        // 填充创建者信息
        if (episode.getCreatedBy() != null) {
            UserBasicInfo userInfo = userInfoHelper.getUserInfo(episode.getCreatedBy());
            if (userInfo != null) {
                response.setCreatedByUsername(userInfo.getUsername());
                response.setCreatedByNickname(userInfo.getNickname());
            }
        }
        // 填充封面URL
        if (episode.getCoverAssetId() != null) {
            try {
                var asset = assetService.getById(episode.getCoverAssetId());
                response.setCoverUrl(asset.getThumbnailUrl() != null ? asset.getThumbnailUrl() : asset.getFileUrl());
            } catch (Exception e) {
                log.warn("获取剧集封面素材失败: episodeId={}, coverAssetId={}", episodeId, episode.getCoverAssetId());
            }
        }
        return response;
    }

    @Override
    public List<EpisodeListResponse> listByScript(String scriptId) {
        List<Episode> episodes = episodeMapper.selectByScriptId(scriptId);
        return convertToListResponses(episodes);
    }

    @Override
    public List<EpisodeListResponse> listByScript(String scriptId, String keyword, Integer limit) {
        LambdaQueryWrapper<Episode> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Episode::getScriptId, scriptId)
                .eq(Episode::getDeleted, CommonConstants.NOT_DELETED);

        // 数据库级模糊搜索
        if (keyword != null && !keyword.isBlank()) {
            String kw = "%" + keyword + "%";
            wrapper.and(w -> w.like(Episode::getTitle, keyword)
                    .or().like(Episode::getSynopsis, keyword)
                    .or().like(Episode::getContent, keyword)
                    .or().apply("extra_info::text LIKE {0}", kw));
        }

        wrapper.orderByAsc(Episode::getSequence);

        // 数量限制
        if (limit != null && limit > 0) {
            wrapper.last("LIMIT " + limit);
        }

        List<Episode> episodes = episodeMapper.selectList(wrapper);
        return convertToListResponses(episodes);
    }

    @Override
    public Page<EpisodeListResponse> queryEpisodes(EpisodeQueryRequest request, String workspaceId) {
        Page<Episode> page = new Page<>(request.getPageNum(), request.getPageSize());

        LambdaQueryWrapper<Episode> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Episode::getWorkspaceId, workspaceId)
                .eq(Episode::getDeleted, CommonConstants.NOT_DELETED);

        // 剧本ID过滤（必填）
        if (StringUtils.hasText(request.getScriptId())) {
            wrapper.eq(Episode::getScriptId, request.getScriptId());
        }
        // 状态过滤
        if (StringUtils.hasText(request.getStatus())) {
            wrapper.eq(Episode::getStatus, request.getStatus());
        }
        // 关键词搜索（标题、简介、正文、附加信息）
        if (StringUtils.hasText(request.getKeyword())) {
            String kw = "%" + request.getKeyword() + "%";
            wrapper.and(w -> w.like(Episode::getTitle, request.getKeyword())
                    .or()
                    .like(Episode::getSynopsis, request.getKeyword())
                    .or()
                    .like(Episode::getContent, request.getKeyword())
                    .or()
                    .apply("extra_info::text LIKE {0}", kw));
        }

        // 排序
        String orderBy = request.getOrderBy();
        boolean isAsc = "asc".equalsIgnoreCase(request.getOrderDir());
        switch (orderBy) {
            case "title" -> wrapper.orderBy(true, isAsc, Episode::getTitle);
            case "created_at" -> wrapper.orderBy(true, isAsc, Episode::getCreatedAt);
            case "updated_at" -> wrapper.orderBy(true, isAsc, Episode::getUpdatedAt);
            default -> wrapper.orderBy(true, isAsc, Episode::getSequence);
        }

        Page<Episode> resultPage = episodeMapper.selectPage(page, wrapper);

        // 转换为响应对象
        Page<EpisodeListResponse> responsePage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        responsePage.setRecords(convertToListResponses(resultPage.getRecords()));

        return responsePage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reorder(String scriptId, List<String> episodeIds, String userId) {
        for (int i = 0; i < episodeIds.size(); i++) {
            Episode episode = episodeMapper.selectById(episodeIds.get(i));
            if (episode != null && episode.getScriptId().equals(scriptId)) {
                episode.setSequence(i + 1);
                episodeMapper.updateById(episode);
            }
        }

        log.info("剧集顺序调整: scriptId={}", scriptId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(String episodeId, String status, String userId) {
        Episode episode = getEpisodeOrThrow(episodeId);

        // 仅在状态实际变更时处理
        if (!status.equals(episode.getStatus())) {
            String changeSummary = String.format("状态变更: %s → %s", episode.getStatus(), status);

            // 1. 先更新数据
            episode.setStatus(status);
            episode.setUpdatedBy(userId);

            int rows = episodeMapper.updateById(episode);
            if (rows == 0) {
                throw new BusinessException(ResultCode.CONCURRENT_OPERATION);
            }

            // 2. 再创建版本快照（保存更新后的数据）
            episode = getEpisodeOrThrow(episodeId);
            episodeVersionService.createVersionSnapshot(episode, changeSummary, userId);

            log.info("剧集状态更新: episodeId={}, status={}, versionNumber={}", episodeId, status, episode.getVersionNumber());
        }
    }

    @Override
    public Optional<Episode> findById(String episodeId) {
        Episode episode = episodeMapper.selectById(episodeId);
        if (episode == null || episode.getDeleted() == CommonConstants.DELETED) {
            return Optional.empty();
        }
        return Optional.of(episode);
    }

    @Override
    public void setCover(String episodeId, String assetId, String userId) {
        Episode episode = getEpisodeOrThrow(episodeId);
        episode.setCoverAssetId(assetId);
        episodeMapper.updateById(episode);

        log.info("剧集封面设置成功: episodeId={}, assetId={}", episodeId, assetId);
    }

    private Episode getEpisodeOrThrow(String episodeId) {
        Episode episode = episodeMapper.selectById(episodeId);
        if (episode == null || episode.getDeleted() == CommonConstants.DELETED) {
            throw new BusinessException(ResultCode.EPISODE_NOT_FOUND);
        }
        return episode;
    }

    /**
     * 转换为列表响应并批量填充关联数据
     */
    private List<EpisodeListResponse> convertToListResponses(List<Episode> episodes) {
        if (episodes == null || episodes.isEmpty()) {
            return List.of();
        }

        // 批量获取所有 episodeId 的 storyboardCount，避免 N+1 问题
        List<String> episodeIds = episodes.stream().map(Episode::getId).toList();
        Map<String, Integer> storyboardCountMap = storyboardMapper.batchCountByEpisodeIds(episodeIds);

        List<EpisodeListResponse> responses = episodes.stream()
                .map(episode -> {
                    EpisodeListResponse response = EpisodeListResponse.fromEntity(episode);
                    response.setStoryboardCount(storyboardCountMap.getOrDefault(episode.getId(), 0));
                    return response;
                })
                .collect(Collectors.toList());

        // 批量填充用户信息
        populateUserInfo(responses);
        // 批量填充封面URL
        populateCoverUrl(episodes, responses);
        return responses;
    }

    private void populateUserInfo(List<EpisodeListResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return;
        }
        Set<String> userIds = responses.stream()
                .map(EpisodeListResponse::getCreatedBy)
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

    private void populateCoverUrl(List<Episode> episodes, List<EpisodeListResponse> responses) {
        Set<String> coverAssetIds = episodes.stream()
                .map(Episode::getCoverAssetId)
                .filter(id -> id != null && !id.isEmpty())
                .collect(Collectors.toSet());
        if (coverAssetIds.isEmpty()) {
            return;
        }
        try {
            var assets = assetService.batchGet(new java.util.ArrayList<>(coverAssetIds));
            // 过滤掉 URL 都为空的素材，避免 toMap 的 NPE
            Map<String, String> assetUrlMap = assets.stream()
                    .filter(a -> a.getThumbnailUrl() != null || a.getFileUrl() != null)
                    .collect(Collectors.toMap(
                            a -> a.getId(),
                            a -> a.getThumbnailUrl() != null ? a.getThumbnailUrl() : a.getFileUrl(),
                            (a, b) -> a
                    ));
            for (int i = 0; i < episodes.size(); i++) {
                String coverAssetId = episodes.get(i).getCoverAssetId();
                if (coverAssetId != null && assetUrlMap.containsKey(coverAssetId)) {
                    responses.get(i).setCoverUrl(assetUrlMap.get(coverAssetId));
                }
            }
        } catch (Exception e) {
            log.warn("批量获取剧集封面素材失败", e);
        }
    }

    private Map<String, Object> toEntityDataMap(Episode episode) {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("title", episode.getTitle());
        data.put("synopsis", episode.getSynopsis());
        data.put("sequence", episode.getSequence());
        data.put("version", episode.getVersion());
        return data;
    }
}
