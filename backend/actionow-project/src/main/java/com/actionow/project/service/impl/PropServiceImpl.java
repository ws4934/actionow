package com.actionow.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.common.core.constant.CommonConstants;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.id.UuidGenerator;
import com.actionow.common.core.result.ResultCode;
import com.actionow.project.constant.ProjectConstants;
import com.actionow.project.constant.EntityDefaults;
import com.actionow.project.dto.CreatePropRequest;
import com.actionow.project.dto.UpdatePropRequest;
import com.actionow.project.dto.PropQueryRequest;
import com.actionow.project.dto.PropDetailResponse;
import com.actionow.project.dto.PropListResponse;
import com.actionow.project.dto.asset.AssetResponse;
import com.actionow.project.entity.Prop;
import com.actionow.project.entity.EntityAssetRelation;
import com.actionow.project.mapper.PropMapper;
import com.actionow.project.mapper.EntityAssetRelationMapper;
import com.actionow.project.service.AssetService;
import com.actionow.project.service.PropService;
import com.actionow.project.service.UserInfoHelper;
import com.actionow.project.service.version.VersionService;
import com.actionow.project.dto.version.PropVersionDetailResponse;
import com.actionow.project.feign.UserBasicInfo;
import com.actionow.common.mq.publisher.CanvasMessagePublisher;
import com.actionow.common.mq.message.CollabEntityChangeEvent;
import com.actionow.project.publisher.EntityChangeEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 道具服务实现
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PropServiceImpl implements PropService {

    private final PropMapper propMapper;
    private final UserInfoHelper userInfoHelper;
    private final CanvasMessagePublisher canvasMessagePublisher;
    private final EntityChangeEventPublisher entityChangeEventPublisher;
    private final VersionService<Prop, PropVersionDetailResponse> propVersionService;
    private final AssetService assetService;
    private final EntityAssetRelationMapper entityAssetRelationMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PropDetailResponse create(CreatePropRequest request, String workspaceId, String userId) {
        return create(request, workspaceId, userId, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PropDetailResponse create(CreatePropRequest request, String workspaceId, String userId, boolean skipCanvasSync) {
        Prop prop = new Prop();
        prop.setId(UuidGenerator.generateUuidV7());
        prop.setWorkspaceId(workspaceId);
        prop.setScope(request.getScope());
        prop.setScriptId(request.getScriptId());
        prop.setName(request.getName());
        prop.setDescription(request.getDescription());
        prop.setFixedDesc(request.getFixedDesc());
        prop.setPropType(request.getPropType());
        // 合并 appearanceData，确保包含完整的默认结构
        prop.setAppearanceData(EntityDefaults.mergePropAppearanceData(request.getAppearanceData()));
        prop.setVersion(1);
        prop.setCreatedBy(userId);

        propMapper.insert(prop);

        // 创建初始版本快照 (V1)
        propVersionService.createVersionSnapshot(prop, "创建道具", userId);

        log.info("道具创建成功: propId={}, name={}, scope={}, skipCanvasSync={}",
                prop.getId(), prop.getName(), prop.getScope(), skipCanvasSync);

        // 发布Canvas同步消息（仅剧本级道具，且未跳过同步）
        if (!skipCanvasSync && ProjectConstants.Scope.SCRIPT.equals(prop.getScope()) && prop.getScriptId() != null) {
            canvasMessagePublisher.publishEntityChange(
                    ProjectConstants.EntityType.PROP,
                    prop.getId(),
                    prop.getScriptId(),
                    ProjectConstants.EntityType.SCRIPT,
                    prop.getScriptId(),
                    workspaceId,
                    "CREATED",
                    toEntityDataMap(prop)
            );
        }

        // 发布协作事件（仅剧本级道具）
        if (ProjectConstants.Scope.SCRIPT.equals(prop.getScope()) && prop.getScriptId() != null) {
            entityChangeEventPublisher.publishEntityCreated(
                    CollabEntityChangeEvent.EntityType.PROP,
                    prop.getId(),
                    prop.getScriptId(),
                    toEntityDataMap(prop)
            );
        }

        return PropDetailResponse.fromEntity(prop);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<PropDetailResponse> batchCreate(List<CreatePropRequest> requests, String workspaceId, String userId) {
        List<PropDetailResponse> responses = new java.util.ArrayList<>();
        for (CreatePropRequest request : requests) {
            responses.add(create(request, workspaceId, userId, true));
        }
        log.info("批量创建道具成功: workspaceId={}, count={}", workspaceId, requests.size());
        return responses;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PropDetailResponse update(String propId, UpdatePropRequest request, String userId) {
        Prop prop = getPropOrThrow(propId);

        // 获取保存模式，默认为 NEW_VERSION
        String saveMode = request.getSaveMode();
        if (saveMode == null) {
            saveMode = ProjectConstants.SaveMode.NEW_VERSION;
        }

        // NEW_ENTITY 模式：另存为新实体
        if (ProjectConstants.SaveMode.NEW_ENTITY.equals(saveMode)) {
            CreatePropRequest newRequest = new CreatePropRequest();
            newRequest.setScope(prop.getScope());
            newRequest.setScriptId(prop.getScriptId());
            newRequest.setName(request.getName() != null ? request.getName() : prop.getName());
            newRequest.setDescription(request.getDescription() != null ? request.getDescription() : prop.getDescription());
            newRequest.setFixedDesc(request.getFixedDesc() != null ? request.getFixedDesc() : prop.getFixedDesc());
            newRequest.setPropType(request.getPropType() != null ? request.getPropType() : prop.getPropType());
            newRequest.setAppearanceData(request.getAppearanceData() != null ? request.getAppearanceData() : prop.getAppearanceData());
            newRequest.setCoverAssetId(request.getCoverAssetId() != null ? request.getCoverAssetId() : prop.getCoverAssetId());
            newRequest.setExtraInfo(request.getExtraInfo() != null ? request.getExtraInfo() : prop.getExtraInfo());
            return create(newRequest, prop.getWorkspaceId(), userId);
        }

        // 检测实际变更，生成变更摘要
        StringBuilder changes = new StringBuilder();
        if (request.getName() != null && !request.getName().equals(prop.getName())) {
            changes.append("名称");
        }
        if (request.getDescription() != null && !request.getDescription().equals(prop.getDescription())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("描述");
        }
        if (request.getFixedDesc() != null && !request.getFixedDesc().equals(prop.getFixedDesc())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("固定描述");
        }
        if (request.getPropType() != null && !request.getPropType().equals(prop.getPropType())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("道具类型");
        }
        if (request.getAppearanceData() != null && !request.getAppearanceData().equals(prop.getAppearanceData())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("外观数据");
        }
        if (request.getCoverAssetId() != null && !request.getCoverAssetId().equals(prop.getCoverAssetId())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("封面");
        }
        if (request.getExtraInfo() != null && !request.getExtraInfo().equals(prop.getExtraInfo())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("扩展信息");
        }

        // 1. 先更新数据
        if (request.getName() != null) {
            prop.setName(request.getName());
        }
        if (request.getDescription() != null) {
            prop.setDescription(request.getDescription());
        }
        if (request.getFixedDesc() != null) {
            prop.setFixedDesc(request.getFixedDesc());
        }
        if (request.getPropType() != null) {
            prop.setPropType(request.getPropType());
        }
        if (request.getAppearanceData() != null) {
            prop.setAppearanceData(request.getAppearanceData());
        }
        // appearanceDataPatch: merge 语义
        if (request.getAppearanceDataPatch() != null && !request.getAppearanceDataPatch().isEmpty()) {
            Map<String, Object> merged = prop.getAppearanceData() != null
                    ? new HashMap<>(prop.getAppearanceData()) : new HashMap<>();
            merged.putAll(request.getAppearanceDataPatch());
            prop.setAppearanceData(merged);
        }
        if (request.getCoverAssetId() != null) {
            prop.setCoverAssetId(request.getCoverAssetId());
        }
        if (request.getExtraInfo() != null) {
            prop.setExtraInfo(request.getExtraInfo());
        }
        // extraInfoPatch: merge 语义
        if (request.getExtraInfoPatch() != null && !request.getExtraInfoPatch().isEmpty()) {
            Map<String, Object> merged = prop.getExtraInfo() != null
                    ? new HashMap<>(prop.getExtraInfo()) : new HashMap<>();
            merged.putAll(request.getExtraInfoPatch());
            prop.setExtraInfo(merged);
        }
        prop.setUpdatedBy(userId);
        // 不要手动设置 version，MyBatis Plus @Version 会自动处理

        int rows = propMapper.updateById(prop);
        if (rows == 0) {
            log.warn("道具更新失败（并发冲突）: propId={}, version={}", propId, prop.getVersion());
            throw new BusinessException(ResultCode.CONCURRENT_OPERATION);
        }

        // 2. NEW_VERSION 模式：仅在有实际变更时创建版本快照（保存更新后的数据）
        // OVERWRITE 模式：跳过版本快照创建
        if (ProjectConstants.SaveMode.NEW_VERSION.equals(saveMode) && !changes.isEmpty()) {
            String changeSummary = "更新" + changes;
            prop = getPropOrThrow(propId);
            propVersionService.createVersionSnapshot(prop, changeSummary, userId);
        }

        log.info("道具更新成功: propId={}, versionNumber={}, saveMode={}", propId, prop.getVersionNumber(), saveMode);

        // 发布Canvas同步消息（仅剧本级道具）
        if (ProjectConstants.Scope.SCRIPT.equals(prop.getScope()) && prop.getScriptId() != null) {
            canvasMessagePublisher.publishEntityChange(
                    ProjectConstants.EntityType.PROP,
                    prop.getId(),
                    prop.getScriptId(),
                    ProjectConstants.EntityType.SCRIPT,
                    prop.getScriptId(),
                    prop.getWorkspaceId(),
                    "UPDATED",
                    toEntityDataMap(prop)
            );

            // 发布协作事件
            entityChangeEventPublisher.publishEntityUpdated(
                    CollabEntityChangeEvent.EntityType.PROP,
                    prop.getId(),
                    prop.getScriptId(),
                    null,
                    toEntityDataMap(prop)
            );
        }

        return PropDetailResponse.fromEntity(prop);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String propId, String userId) {
        // 先验证道具存在
        Prop prop = getPropOrThrow(propId);
        // 使用 MyBatis-Plus 的 deleteById，会自动转换为逻辑删除（UPDATE SET deleted=1）
        propMapper.deleteById(propId);

        log.info("道具删除成功: propId={}", propId);

        // 发布Canvas同步消息（仅剧本级道具）
        if (ProjectConstants.Scope.SCRIPT.equals(prop.getScope()) && prop.getScriptId() != null) {
            canvasMessagePublisher.publishEntityChange(
                    ProjectConstants.EntityType.PROP,
                    prop.getId(),
                    prop.getScriptId(),
                    ProjectConstants.EntityType.SCRIPT,
                    prop.getScriptId(),
                    prop.getWorkspaceId(),
                    "DELETED",
                    null
            );

            // 发布协作事件
            entityChangeEventPublisher.publishEntityDeleted(
                    CollabEntityChangeEvent.EntityType.PROP,
                    prop.getId(),
                    prop.getScriptId()
            );
        }
    }

    @Override
    public PropDetailResponse getById(String propId) {
        Prop prop = getPropOrThrow(propId);
        PropDetailResponse response = PropDetailResponse.fromEntity(prop);
        // 填充创建者信息
        if (prop.getCreatedBy() != null) {
            UserBasicInfo userInfo = userInfoHelper.getUserInfo(prop.getCreatedBy());
            if (userInfo != null) {
                response.setCreatedByUsername(userInfo.getUsername());
                response.setCreatedByNickname(userInfo.getNickname());
            }
        }
        // 填充封面URL
        if (prop.getCoverAssetId() != null) {
            try {
                var asset = assetService.getById(prop.getCoverAssetId());
                response.setCoverUrl(asset.getThumbnailUrl() != null ? asset.getThumbnailUrl() : asset.getFileUrl());
            } catch (Exception e) {
                log.warn("获取道具封面素材失败: propId={}, coverAssetId={}", propId, prop.getCoverAssetId());
            }
        }
        // 填充音色URL
        try {
            List<EntityAssetRelation> voiceRelations = entityAssetRelationMapper.selectVoiceRelationsByEntities(
                    ProjectConstants.EntityType.PROP, List.of(propId));
            if (!voiceRelations.isEmpty()) {
                EntityAssetRelation voiceRelation = voiceRelations.get(0);
                AssetResponse voiceAsset = assetService.getById(voiceRelation.getAssetId());
                response.setVoiceAssetId(voiceRelation.getAssetId());
                response.setVoiceUrl(voiceAsset.getFileUrl());
            }
        } catch (Exception e) {
            log.warn("获取道具音色素材失败: propId={}", propId);
        }
        return response;
    }

    @Override
    public Optional<Prop> findById(String propId) {
        Prop prop = propMapper.selectById(propId);
        if (prop == null || prop.getDeleted() == CommonConstants.DELETED) {
            return Optional.empty();
        }
        return Optional.of(prop);
    }

    @Override
    public List<PropListResponse> listWorkspaceProps(String workspaceId) {
        List<Prop> props = propMapper.selectWorkspaceProps(workspaceId);
        return convertToListResponses(props);
    }

    @Override
    public List<PropListResponse> listScriptProps(String scriptId) {
        List<Prop> props = propMapper.selectScriptProps(scriptId);
        return convertToListResponses(props);
    }

    @Override
    public List<PropListResponse> listAvailableProps(String workspaceId, String scriptId) {
        List<Prop> props = propMapper.selectAvailableProps(workspaceId, scriptId);
        return convertToListResponses(props);
    }

    @Override
    public List<PropListResponse> listAvailableProps(String workspaceId, String scriptId, String keyword, Integer limit) {
        List<Prop> props = propMapper.selectAvailablePropsFiltered(
                workspaceId, scriptId, keyword, limit);
        return convertToListResponses(props);
    }

    @Override
    public Page<PropListResponse> queryProps(PropQueryRequest request, String workspaceId) {
        if ("SYSTEM".equalsIgnoreCase(request.getScope())) {
            return querySystemProps(request);
        }

        // 跨 schema 可用道具查询：scriptId 非空且未指定具体 scope 时，合并 SYSTEM+WORKSPACE+SCRIPT
        if (StringUtils.hasText(request.getScriptId())
                && !StringUtils.hasText(request.getScope())) {
            int offset = (request.getPageNum() - 1) * request.getPageSize();
            List<Prop> records = propMapper.selectAvailablePropsPaginated(
                    workspaceId, request.getScriptId(), request.getKeyword(),
                    request.getPropType(),
                    request.getPageSize(), offset);
            long total = propMapper.countAvailableProps(
                    workspaceId, request.getScriptId(), request.getKeyword(),
                    request.getPropType());
            Page<PropListResponse> responsePage = new Page<>(request.getPageNum(), request.getPageSize(), total);
            responsePage.setRecords(convertToListResponses(records));
            return responsePage;
        }

        Page<Prop> page = new Page<>(request.getPageNum(), request.getPageSize());

        LambdaQueryWrapper<Prop> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Prop::getWorkspaceId, workspaceId)
                .eq(Prop::getDeleted, CommonConstants.NOT_DELETED);

        // 作用域过滤
        if (StringUtils.hasText(request.getScope())) {
            wrapper.eq(Prop::getScope, request.getScope());
        }
        // 剧本ID过滤
        if (StringUtils.hasText(request.getScriptId())) {
            wrapper.eq(Prop::getScriptId, request.getScriptId());
        }
        // 道具类型过滤
        if (StringUtils.hasText(request.getPropType())) {
            wrapper.eq(Prop::getPropType, request.getPropType());
        }
        // 关键词搜索（名称、描述、固定描述、外观数据、附加信息）
        if (StringUtils.hasText(request.getKeyword())) {
            String kw = "%" + request.getKeyword() + "%";
            wrapper.and(w -> w.like(Prop::getName, request.getKeyword())
                    .or()
                    .like(Prop::getDescription, request.getKeyword())
                    .or()
                    .like(Prop::getFixedDesc, request.getKeyword())
                    .or()
                    .apply("appearance_data::text LIKE {0}", kw)
                    .or()
                    .apply("extra_info::text LIKE {0}", kw));
        }

        // 排序
        String orderBy = request.getOrderBy();
        boolean isAsc = "asc".equalsIgnoreCase(request.getOrderDir());
        switch (orderBy) {
            case "name" -> wrapper.orderBy(true, isAsc, Prop::getName);
            case "updated_at" -> wrapper.orderBy(true, isAsc, Prop::getUpdatedAt);
            default -> wrapper.orderBy(true, isAsc, Prop::getCreatedAt);
        }

        Page<Prop> resultPage = propMapper.selectPage(page, wrapper);

        // 转换为响应对象
        Page<PropListResponse> responsePage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        responsePage.setRecords(convertToListResponses(resultPage.getRecords()));

        return responsePage;
    }

    private Page<PropListResponse> querySystemProps(PropQueryRequest request) {
        List<Prop> all = new ArrayList<>(propMapper.selectSystemProps());

        if (StringUtils.hasText(request.getKeyword())) {
            String kw = request.getKeyword().toLowerCase();
            all = all.stream()
                    .filter(p -> (p.getName() != null && p.getName().toLowerCase().contains(kw))
                            || (p.getDescription() != null && p.getDescription().toLowerCase().contains(kw)))
                    .collect(Collectors.toList());
        }
        if (StringUtils.hasText(request.getPropType())) {
            all = all.stream()
                    .filter(p -> request.getPropType().equalsIgnoreCase(p.getPropType()))
                    .collect(Collectors.toList());
        }

        boolean isAsc = !"desc".equalsIgnoreCase(request.getOrderDir());
        Comparator<Prop> comparator = switch (request.getOrderBy()) {
            case "name" -> Comparator.comparing((Prop p) -> p.getName() != null ? p.getName() : "");
            case "updated_at" -> Comparator.comparing((Prop p) ->
                    p.getUpdatedAt() != null ? p.getUpdatedAt() : java.time.LocalDateTime.MIN);
            default -> Comparator.comparing((Prop p) ->
                    p.getCreatedAt() != null ? p.getCreatedAt() : java.time.LocalDateTime.MIN);
        };
        all.sort(isAsc ? comparator : comparator.reversed());

        long total = all.size();
        int from = (request.getPageNum() - 1) * request.getPageSize();
        int to = Math.min(from + request.getPageSize(), (int) total);
        List<Prop> pageData = from >= total ? List.of() : all.subList(from, to);

        Page<PropListResponse> responsePage = new Page<>(request.getPageNum(), request.getPageSize(), total);
        responsePage.setRecords(convertToListResponses(pageData));
        return responsePage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setCover(String propId, String assetId, String userId) {
        Prop prop = getPropOrThrow(propId);
        prop.setCoverAssetId(assetId);
        propMapper.updateById(prop);

        log.info("道具封面设置成功: propId={}, assetId={}", propId, assetId);
    }

    private Prop getPropOrThrow(String propId) {
        Prop prop = propMapper.selectById(propId);
        if (prop == null || prop.getDeleted() == CommonConstants.DELETED) {
            throw new BusinessException(ResultCode.PROP_NOT_FOUND);
        }
        return prop;
    }

    /**
     * 转换为列表响应并批量填充用户信息
     */
    private List<PropListResponse> convertToListResponses(List<Prop> props) {
        if (props == null || props.isEmpty()) {
            return List.of();
        }

        List<PropListResponse> responses = props.stream()
                .map(PropListResponse::fromEntity)
                .collect(Collectors.toList());

        // 批量填充用户信息
        populateUserInfo(responses);
        // 批量填充封面URL
        populateCoverUrl(props, responses);
        // 批量填充音色URL
        populateVoiceUrl(props, responses);
        return responses;
    }

    private void populateUserInfo(List<PropListResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return;
        }
        Set<String> userIds = responses.stream()
                .map(PropListResponse::getCreatedBy)
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

    private void populateCoverUrl(List<Prop> props, List<PropListResponse> responses) {
        Set<String> coverAssetIds = props.stream()
                .map(Prop::getCoverAssetId)
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
            for (int i = 0; i < props.size(); i++) {
                String coverAssetId = props.get(i).getCoverAssetId();
                if (coverAssetId != null && assetUrlMap.containsKey(coverAssetId)) {
                    responses.get(i).setCoverUrl(assetUrlMap.get(coverAssetId));
                }
            }
        } catch (Exception e) {
            log.warn("批量获取道具封面素材失败", e);
        }
    }

    private void populateVoiceUrl(List<Prop> props, List<PropListResponse> responses) {
        List<String> entityIds = props.stream()
                .map(Prop::getId)
                .collect(Collectors.toList());
        if (entityIds.isEmpty()) {
            return;
        }
        try {
            List<EntityAssetRelation> voiceRelations = entityAssetRelationMapper.selectVoiceRelationsByEntities(
                    ProjectConstants.EntityType.PROP, entityIds);
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
            for (int i = 0; i < props.size(); i++) {
                EntityAssetRelation voiceRelation = entityVoiceMap.get(props.get(i).getId());
                if (voiceRelation != null) {
                    responses.get(i).setVoiceAssetId(voiceRelation.getAssetId());
                    String voiceUrl = voiceUrlMap.get(voiceRelation.getAssetId());
                    if (voiceUrl != null) {
                        responses.get(i).setVoiceUrl(voiceUrl);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("批量获取道具音色素材失败", e);
        }
    }

    private Map<String, Object> toEntityDataMap(Prop prop) {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("name", prop.getName());
        data.put("description", prop.getDescription());
        data.put("propType", prop.getPropType());
        data.put("version", prop.getVersion());
        data.put("coverAssetId", prop.getCoverAssetId());
        return data;
    }
}
