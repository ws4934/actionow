package com.actionow.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.common.core.constant.CommonConstants;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.id.UuidGenerator;
import com.actionow.common.core.result.ResultCode;
import com.actionow.project.constant.ProjectConstants;
import com.actionow.project.constant.EntityDefaults;
import com.actionow.project.dto.CreateSceneRequest;
import com.actionow.project.dto.UpdateSceneRequest;
import com.actionow.project.dto.SceneQueryRequest;
import com.actionow.project.dto.SceneDetailResponse;
import com.actionow.project.dto.SceneListResponse;
import com.actionow.project.dto.asset.AssetResponse;
import com.actionow.project.entity.Scene;
import com.actionow.project.entity.EntityAssetRelation;
import com.actionow.project.mapper.SceneMapper;
import com.actionow.project.mapper.EntityAssetRelationMapper;
import com.actionow.project.service.AssetService;
import com.actionow.project.service.SceneService;
import com.actionow.project.service.UserInfoHelper;
import com.actionow.project.service.version.VersionService;
import com.actionow.project.dto.version.SceneVersionDetailResponse;
import com.actionow.project.feign.UserBasicInfo;
import com.actionow.common.mq.publisher.CanvasMessagePublisher;
import com.actionow.common.mq.message.CollabEntityChangeEvent;
import com.actionow.project.publisher.EntityChangeEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 场景服务实现
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SceneServiceImpl implements SceneService {

    private final SceneMapper sceneMapper;
    private final UserInfoHelper userInfoHelper;
    private final CanvasMessagePublisher canvasMessagePublisher;
    private final EntityChangeEventPublisher entityChangeEventPublisher;
    private final VersionService<Scene, SceneVersionDetailResponse> sceneVersionService;
    private final AssetService assetService;
    private final EntityAssetRelationMapper entityAssetRelationMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SceneDetailResponse create(CreateSceneRequest request, String workspaceId, String userId) {
        return create(request, workspaceId, userId, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SceneDetailResponse create(CreateSceneRequest request, String workspaceId, String userId, boolean skipCanvasSync) {
        Scene scene = new Scene();
        scene.setId(UuidGenerator.generateUuidV7());
        scene.setWorkspaceId(workspaceId);
        scene.setScope(request.getScope());
        scene.setSceneType(request.getSceneType());
        scene.setScriptId(request.getScriptId());
        scene.setName(request.getName());
        scene.setDescription(request.getDescription());
        scene.setFixedDesc(request.getFixedDesc());
        // 合并 appearanceData，确保包含完整的默认结构
        scene.setAppearanceData(EntityDefaults.mergeSceneAppearanceData(request.getAppearanceData()));
        scene.setCoverAssetId(request.getCoverAssetId());
        scene.setExtraInfo(request.getExtraInfo());
        scene.setVersion(1);
        scene.setCreatedBy(userId);

        sceneMapper.insert(scene);

        // 创建初始版本快照 (V1)
        sceneVersionService.createVersionSnapshot(scene, "创建场景", userId);

        log.info("场景创建成功: sceneId={}, name={}, scope={}, skipCanvasSync={}",
                scene.getId(), scene.getName(), scene.getScope(), skipCanvasSync);

        // 发布Canvas同步消息（仅剧本级场景，且未跳过同步）
        if (!skipCanvasSync && ProjectConstants.Scope.SCRIPT.equals(scene.getScope()) && scene.getScriptId() != null) {
            canvasMessagePublisher.publishEntityChange(
                    ProjectConstants.EntityType.SCENE,
                    scene.getId(),
                    scene.getScriptId(),
                    ProjectConstants.EntityType.SCRIPT,
                    scene.getScriptId(),
                    workspaceId,
                    "CREATED",
                    toEntityDataMap(scene)
            );
        }

        // 发布协作事件（仅剧本级场景）
        if (ProjectConstants.Scope.SCRIPT.equals(scene.getScope()) && scene.getScriptId() != null) {
            entityChangeEventPublisher.publishEntityCreated(
                    CollabEntityChangeEvent.EntityType.SCENE,
                    scene.getId(),
                    scene.getScriptId(),
                    toEntityDataMap(scene)
            );
        }

        return SceneDetailResponse.fromEntity(scene);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<SceneDetailResponse> batchCreate(List<CreateSceneRequest> requests, String workspaceId, String userId) {
        List<SceneDetailResponse> responses = new java.util.ArrayList<>();
        for (CreateSceneRequest request : requests) {
            responses.add(create(request, workspaceId, userId, true));
        }
        log.info("批量创建场景成功: workspaceId={}, count={}", workspaceId, requests.size());
        return responses;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SceneDetailResponse update(String sceneId, UpdateSceneRequest request, String userId) {
        Scene scene = getSceneOrThrow(sceneId);

        // 获取保存模式，默认为 NEW_VERSION
        String saveMode = request.getSaveMode();
        if (saveMode == null) {
            saveMode = ProjectConstants.SaveMode.NEW_VERSION;
        }

        // NEW_ENTITY 模式：另存为新实体
        if (ProjectConstants.SaveMode.NEW_ENTITY.equals(saveMode)) {
            CreateSceneRequest newRequest = new CreateSceneRequest();
            newRequest.setScope(scene.getScope());
            newRequest.setSceneType(scene.getSceneType());
            newRequest.setScriptId(scene.getScriptId());
            newRequest.setName(request.getName() != null ? request.getName() : scene.getName());
            newRequest.setDescription(request.getDescription() != null ? request.getDescription() : scene.getDescription());
            newRequest.setFixedDesc(request.getFixedDesc() != null ? request.getFixedDesc() : scene.getFixedDesc());
            newRequest.setAppearanceData(request.getAppearanceData() != null ? request.getAppearanceData() : scene.getAppearanceData());
            newRequest.setCoverAssetId(request.getCoverAssetId() != null ? request.getCoverAssetId() : scene.getCoverAssetId());
            newRequest.setExtraInfo(request.getExtraInfo() != null ? request.getExtraInfo() : scene.getExtraInfo());
            return create(newRequest, scene.getWorkspaceId(), userId);
        }

        // 检测实际变更，生成变更摘要
        StringBuilder changes = new StringBuilder();
        if (request.getName() != null && !request.getName().equals(scene.getName())) {
            changes.append("名称");
        }
        if (request.getDescription() != null && !request.getDescription().equals(scene.getDescription())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("描述");
        }
        if (request.getSceneType() != null && !request.getSceneType().equals(scene.getSceneType())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("场景类型");
        }
        if (request.getFixedDesc() != null && !request.getFixedDesc().equals(scene.getFixedDesc())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("固定描述");
        }
        if (request.getAppearanceData() != null && !request.getAppearanceData().equals(scene.getAppearanceData())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("外观数据");
        }
        if (request.getCoverAssetId() != null && !request.getCoverAssetId().equals(scene.getCoverAssetId())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("封面");
        }
        if (request.getExtraInfo() != null && !request.getExtraInfo().equals(scene.getExtraInfo())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("扩展信息");
        }

        // 1. 先更新数据
        if (request.getName() != null) {
            scene.setName(request.getName());
        }
        if (request.getDescription() != null) {
            scene.setDescription(request.getDescription());
        }
        if (request.getSceneType() != null) {
            scene.setSceneType(request.getSceneType());
        }
        if (request.getFixedDesc() != null) {
            scene.setFixedDesc(request.getFixedDesc());
        }
        if (request.getAppearanceData() != null) {
            scene.setAppearanceData(request.getAppearanceData());
        }
        // appearanceDataPatch: merge 语义
        if (request.getAppearanceDataPatch() != null && !request.getAppearanceDataPatch().isEmpty()) {
            Map<String, Object> merged = scene.getAppearanceData() != null
                    ? new HashMap<>(scene.getAppearanceData()) : new HashMap<>();
            merged.putAll(request.getAppearanceDataPatch());
            scene.setAppearanceData(merged);
        }
        if (request.getCoverAssetId() != null) {
            scene.setCoverAssetId(request.getCoverAssetId());
        }
        if (request.getExtraInfo() != null) {
            scene.setExtraInfo(request.getExtraInfo());
        }
        // extraInfoPatch: merge 语义
        if (request.getExtraInfoPatch() != null && !request.getExtraInfoPatch().isEmpty()) {
            Map<String, Object> merged = scene.getExtraInfo() != null
                    ? new HashMap<>(scene.getExtraInfo()) : new HashMap<>();
            merged.putAll(request.getExtraInfoPatch());
            scene.setExtraInfo(merged);
        }
        scene.setUpdatedBy(userId);
        // 不要手动设置 version，MyBatis Plus @Version 会自动处理

        int rows = sceneMapper.updateById(scene);
        if (rows == 0) {
            log.warn("场景更新失败（并发冲突）: sceneId={}, version={}", sceneId, scene.getVersion());
            throw new BusinessException(ResultCode.CONCURRENT_OPERATION);
        }

        // 2. NEW_VERSION 模式：仅在有实际变更时创建版本快照（保存更新后的数据）
        // OVERWRITE 模式：跳过版本快照创建
        if (ProjectConstants.SaveMode.NEW_VERSION.equals(saveMode) && !changes.isEmpty()) {
            String changeSummary = "更新" + changes;
            scene = getSceneOrThrow(sceneId);
            sceneVersionService.createVersionSnapshot(scene, changeSummary, userId);
        }

        log.info("场景更新成功: sceneId={}, versionNumber={}, saveMode={}", sceneId, scene.getVersionNumber(), saveMode);

        // 发布Canvas同步消息（仅剧本级场景）
        if (ProjectConstants.Scope.SCRIPT.equals(scene.getScope()) && scene.getScriptId() != null) {
            canvasMessagePublisher.publishEntityChange(
                    ProjectConstants.EntityType.SCENE,
                    scene.getId(),
                    scene.getScriptId(),
                    ProjectConstants.EntityType.SCRIPT,
                    scene.getScriptId(),
                    scene.getWorkspaceId(),
                    "UPDATED",
                    toEntityDataMap(scene)
            );

            // 发布协作事件
            entityChangeEventPublisher.publishEntityUpdated(
                    CollabEntityChangeEvent.EntityType.SCENE,
                    scene.getId(),
                    scene.getScriptId(),
                    null,
                    toEntityDataMap(scene)
            );
        }

        return SceneDetailResponse.fromEntity(scene);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String sceneId, String userId) {
        // 先验证场景存在
        Scene scene = getSceneOrThrow(sceneId);
        // 使用 MyBatis-Plus 的 deleteById，会自动转换为逻辑删除（UPDATE SET deleted=1）
        sceneMapper.deleteById(sceneId);

        log.info("场景删除成功: sceneId={}", sceneId);

        // 发布Canvas同步消息（仅剧本级场景）
        if (ProjectConstants.Scope.SCRIPT.equals(scene.getScope()) && scene.getScriptId() != null) {
            canvasMessagePublisher.publishEntityChange(
                    ProjectConstants.EntityType.SCENE,
                    scene.getId(),
                    scene.getScriptId(),
                    ProjectConstants.EntityType.SCRIPT,
                    scene.getScriptId(),
                    scene.getWorkspaceId(),
                    "DELETED",
                    null
            );

            // 发布协作事件
            entityChangeEventPublisher.publishEntityDeleted(
                    CollabEntityChangeEvent.EntityType.SCENE,
                    scene.getId(),
                    scene.getScriptId()
            );
        }
    }

    @Override
    public SceneDetailResponse getById(String sceneId) {
        Scene scene = getSceneOrThrow(sceneId);
        SceneDetailResponse response = SceneDetailResponse.fromEntity(scene);
        // 填充创建者信息
        if (scene.getCreatedBy() != null) {
            UserBasicInfo userInfo = userInfoHelper.getUserInfo(scene.getCreatedBy());
            if (userInfo != null) {
                response.setCreatedByUsername(userInfo.getUsername());
                response.setCreatedByNickname(userInfo.getNickname());
            }
        }
        // 填充封面URL
        if (scene.getCoverAssetId() != null) {
            try {
                var asset = assetService.getById(scene.getCoverAssetId());
                response.setCoverUrl(asset.getThumbnailUrl() != null ? asset.getThumbnailUrl() : asset.getFileUrl());
            } catch (Exception e) {
                log.warn("获取场景封面素材失败: sceneId={}, coverAssetId={}", sceneId, scene.getCoverAssetId());
            }
        }
        // 填充音色URL
        try {
            List<EntityAssetRelation> voiceRelations = entityAssetRelationMapper.selectVoiceRelationsByEntities(
                    ProjectConstants.EntityType.SCENE, List.of(sceneId));
            if (!voiceRelations.isEmpty()) {
                EntityAssetRelation voiceRelation = voiceRelations.get(0);
                AssetResponse voiceAsset = assetService.getById(voiceRelation.getAssetId());
                response.setVoiceAssetId(voiceRelation.getAssetId());
                response.setVoiceUrl(voiceAsset.getFileUrl());
            }
        } catch (Exception e) {
            log.warn("获取场景音色素材失败: sceneId={}", sceneId);
        }
        return response;
    }

    @Override
    public Optional<Scene> findById(String sceneId) {
        Scene scene = sceneMapper.selectById(sceneId);
        if (scene == null || scene.getDeleted() == CommonConstants.DELETED) {
            return Optional.empty();
        }
        return Optional.of(scene);
    }

    @Override
    public List<SceneListResponse> listWorkspaceScenes(String workspaceId) {
        List<Scene> scenes = sceneMapper.selectWorkspaceScenes(workspaceId);
        return convertToListResponses(scenes);
    }

    @Override
    public List<SceneListResponse> listScriptScenes(String scriptId) {
        List<Scene> scenes = sceneMapper.selectScriptScenes(scriptId);
        return convertToListResponses(scenes);
    }

    @Override
    public List<SceneListResponse> listAvailableScenes(String workspaceId, String scriptId) {
        List<Scene> scenes = sceneMapper.selectAvailableScenes(workspaceId, scriptId);
        return convertToListResponses(scenes);
    }

    @Override
    public List<SceneListResponse> listAvailableScenes(String workspaceId, String scriptId, String keyword, Integer limit) {
        List<Scene> scenes = sceneMapper.selectAvailableScenesFiltered(
                workspaceId, scriptId, keyword, limit);
        return convertToListResponses(scenes);
    }

    @Override
    public Page<SceneListResponse> queryScenes(SceneQueryRequest request, String workspaceId) {
        if ("SYSTEM".equalsIgnoreCase(request.getScope())) {
            return querySystemScenes(request);
        }

        // 跨 schema 可用场景查询：scriptId 非空且未指定具体 scope 时，合并 SYSTEM+WORKSPACE+SCRIPT
        if (StringUtils.hasText(request.getScriptId())
                && !StringUtils.hasText(request.getScope())) {
            int offset = (request.getPageNum() - 1) * request.getPageSize();
            List<Scene> records = sceneMapper.selectAvailableScenesPaginated(
                    workspaceId, request.getScriptId(), request.getKeyword(),
                    request.getPageSize(), offset);
            long total = sceneMapper.countAvailableScenes(
                    workspaceId, request.getScriptId(), request.getKeyword());
            Page<SceneListResponse> responsePage = new Page<>(request.getPageNum(), request.getPageSize(), total);
            responsePage.setRecords(convertToListResponses(records));
            return responsePage;
        }

        Page<Scene> page = new Page<>(request.getPageNum(), request.getPageSize());

        LambdaQueryWrapper<Scene> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Scene::getWorkspaceId, workspaceId)
                .eq(Scene::getDeleted, CommonConstants.NOT_DELETED);

        // 作用域过滤
        if (StringUtils.hasText(request.getScope())) {
            wrapper.eq(Scene::getScope, request.getScope());
        }
        // 剧本ID过滤
        if (StringUtils.hasText(request.getScriptId())) {
            wrapper.eq(Scene::getScriptId, request.getScriptId());
        }
        // 关键词搜索（名称、描述、固定描述、外观数据、附加信息）
        if (StringUtils.hasText(request.getKeyword())) {
            String kw = "%" + request.getKeyword() + "%";
            wrapper.and(w -> w.like(Scene::getName, request.getKeyword())
                    .or()
                    .like(Scene::getDescription, request.getKeyword())
                    .or()
                    .like(Scene::getFixedDesc, request.getKeyword())
                    .or()
                    .apply("appearance_data::text LIKE {0}", kw)
                    .or()
                    .apply("extra_info::text LIKE {0}", kw));
        }

        // 排序
        String orderBy = request.getOrderBy();
        boolean isAsc = "asc".equalsIgnoreCase(request.getOrderDir());
        switch (orderBy) {
            case "name" -> wrapper.orderBy(true, isAsc, Scene::getName);
            case "updated_at" -> wrapper.orderBy(true, isAsc, Scene::getUpdatedAt);
            default -> wrapper.orderBy(true, isAsc, Scene::getCreatedAt);
        }

        Page<Scene> resultPage = sceneMapper.selectPage(page, wrapper);

        // 转换为响应对象
        Page<SceneListResponse> responsePage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        responsePage.setRecords(convertToListResponses(resultPage.getRecords()));

        return responsePage;
    }

    private Page<SceneListResponse> querySystemScenes(SceneQueryRequest request) {
        List<Scene> all = new ArrayList<>(sceneMapper.selectSystemScenes());

        if (StringUtils.hasText(request.getKeyword())) {
            String kw = request.getKeyword().toLowerCase();
            all = all.stream()
                    .filter(s -> (s.getName() != null && s.getName().toLowerCase().contains(kw))
                            || (s.getDescription() != null && s.getDescription().toLowerCase().contains(kw)))
                    .collect(Collectors.toList());
        }

        boolean isAsc = !"desc".equalsIgnoreCase(request.getOrderDir());
        Comparator<Scene> comparator = switch (request.getOrderBy()) {
            case "name" -> Comparator.comparing((Scene s) -> s.getName() != null ? s.getName() : "");
            case "updated_at" -> Comparator.comparing((Scene s) ->
                    s.getUpdatedAt() != null ? s.getUpdatedAt() : java.time.LocalDateTime.MIN);
            default -> Comparator.comparing((Scene s) ->
                    s.getCreatedAt() != null ? s.getCreatedAt() : java.time.LocalDateTime.MIN);
        };
        all.sort(isAsc ? comparator : comparator.reversed());

        long total = all.size();
        int from = (request.getPageNum() - 1) * request.getPageSize();
        int to = Math.min(from + request.getPageSize(), (int) total);
        List<Scene> pageData = from >= total ? List.of() : all.subList(from, to);

        Page<SceneListResponse> responsePage = new Page<>(request.getPageNum(), request.getPageSize(), total);
        responsePage.setRecords(convertToListResponses(pageData));
        return responsePage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setCover(String sceneId, String assetId, String userId) {
        Scene scene = getSceneOrThrow(sceneId);
        scene.setCoverAssetId(assetId);
        sceneMapper.updateById(scene);

        log.info("场景封面设置成功: sceneId={}, assetId={}", sceneId, assetId);
    }

    private Scene getSceneOrThrow(String sceneId) {
        Scene scene = sceneMapper.selectById(sceneId);
        if (scene == null || scene.getDeleted() == CommonConstants.DELETED) {
            throw new BusinessException(ResultCode.SCENE_NOT_FOUND);
        }
        return scene;
    }

    /**
     * 转换为列表响应并批量填充用户信息
     */
    private List<SceneListResponse> convertToListResponses(List<Scene> scenes) {
        if (scenes == null || scenes.isEmpty()) {
            return List.of();
        }

        List<SceneListResponse> responses = scenes.stream()
                .map(SceneListResponse::fromEntity)
                .collect(Collectors.toList());

        // 批量填充用户信息
        populateUserInfo(responses);
        // 批量填充封面URL
        populateCoverUrl(scenes, responses);
        // 批量填充音色URL
        populateVoiceUrl(scenes, responses);
        return responses;
    }

    private void populateUserInfo(List<SceneListResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return;
        }
        Set<String> userIds = responses.stream()
                .map(SceneListResponse::getCreatedBy)
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

    private void populateCoverUrl(List<Scene> scenes, List<SceneListResponse> responses) {
        Set<String> coverAssetIds = scenes.stream()
                .map(Scene::getCoverAssetId)
                .filter(id -> id != null && !id.isEmpty())
                .collect(Collectors.toSet());
        if (coverAssetIds.isEmpty()) {
            return;
        }
        try {
            var assets = assetService.batchGet(new java.util.ArrayList<>(coverAssetIds));
            Map<String, String> assetUrlMap = assets.stream()
                    .collect(Collectors.toMap(
                            a -> a.getId(),
                            a -> a.getThumbnailUrl() != null ? a.getThumbnailUrl() : a.getFileUrl(),
                            (a, b) -> a
                    ));
            for (int i = 0; i < scenes.size(); i++) {
                String coverAssetId = scenes.get(i).getCoverAssetId();
                if (coverAssetId != null && assetUrlMap.containsKey(coverAssetId)) {
                    responses.get(i).setCoverUrl(assetUrlMap.get(coverAssetId));
                }
            }
        } catch (Exception e) {
            log.warn("批量获取场景封面素材失败", e);
        }
    }

    private void populateVoiceUrl(List<Scene> scenes, List<SceneListResponse> responses) {
        List<String> entityIds = scenes.stream()
                .map(Scene::getId)
                .collect(Collectors.toList());
        if (entityIds.isEmpty()) {
            return;
        }
        try {
            List<EntityAssetRelation> voiceRelations = entityAssetRelationMapper.selectVoiceRelationsByEntities(
                    ProjectConstants.EntityType.SCENE, entityIds);
            if (voiceRelations.isEmpty()) {
                return;
            }
            List<String> voiceAssetIds = voiceRelations.stream()
                    .map(EntityAssetRelation::getAssetId)
                    .distinct()
                    .collect(Collectors.toList());
            List<AssetResponse> voiceAssets = assetService.batchGet(voiceAssetIds);
            Map<String, String> voiceUrlMap = voiceAssets.stream()
                    .filter(a -> a.getFileUrl() != null)
                    .collect(Collectors.toMap(AssetResponse::getId, AssetResponse::getFileUrl, (a, b) -> a));
            Map<String, EntityAssetRelation> entityVoiceMap = voiceRelations.stream()
                    .collect(Collectors.toMap(EntityAssetRelation::getEntityId, r -> r, (a, b) -> a));
            for (int i = 0; i < scenes.size(); i++) {
                EntityAssetRelation voiceRelation = entityVoiceMap.get(scenes.get(i).getId());
                if (voiceRelation != null) {
                    responses.get(i).setVoiceAssetId(voiceRelation.getAssetId());
                    String voiceUrl = voiceUrlMap.get(voiceRelation.getAssetId());
                    if (voiceUrl != null) {
                        responses.get(i).setVoiceUrl(voiceUrl);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("批量获取场景音色素材失败", e);
        }
    }

    private Map<String, Object> toEntityDataMap(Scene scene) {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("name", scene.getName());
        data.put("description", scene.getDescription());
        data.put("version", scene.getVersion());
        data.put("coverAssetId", scene.getCoverAssetId());
        return data;
    }
}
