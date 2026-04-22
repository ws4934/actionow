package com.actionow.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.common.core.constant.CommonConstants;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.id.UuidGenerator;
import com.actionow.common.core.result.ResultCode;
import com.actionow.project.constant.ProjectConstants;
import com.actionow.project.dto.CreateStyleRequest;
import com.actionow.project.dto.UpdateStyleRequest;
import com.actionow.project.dto.StyleQueryRequest;
import com.actionow.project.dto.StyleDetailResponse;
import com.actionow.project.dto.StyleListResponse;
import com.actionow.project.entity.Style;
import com.actionow.project.mapper.StyleMapper;
import com.actionow.project.service.AssetService;
import com.actionow.project.service.StyleService;
import com.actionow.project.service.UserInfoHelper;
import com.actionow.project.service.version.VersionService;
import com.actionow.project.dto.version.StyleVersionDetailResponse;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 风格服务实现
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StyleServiceImpl implements StyleService {

    private final StyleMapper styleMapper;
    private final UserInfoHelper userInfoHelper;
    private final CanvasMessagePublisher canvasMessagePublisher;
    private final EntityChangeEventPublisher entityChangeEventPublisher;
    private final VersionService<Style, StyleVersionDetailResponse> styleVersionService;
    private final AssetService assetService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StyleDetailResponse create(CreateStyleRequest request, String workspaceId, String userId) {
        return create(request, workspaceId, userId, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StyleDetailResponse create(CreateStyleRequest request, String workspaceId, String userId, boolean skipCanvasSync) {
        Style style = new Style();
        style.setId(UuidGenerator.generateUuidV7());
        style.setWorkspaceId(workspaceId);
        style.setScope(request.getScope());
        style.setScriptId(request.getScriptId());
        style.setName(request.getName());
        style.setDescription(request.getDescription());
        style.setFixedDesc(request.getFixedDesc());
        style.setStyleParams(request.getStyleParams());
        style.setVersion(1);
        style.setCreatedBy(userId);

        styleMapper.insert(style);

        // 创建初始版本快照 (V1)
        styleVersionService.createVersionSnapshot(style, "创建风格", userId);

        log.info("风格创建成功: styleId={}, name={}, scope={}, skipCanvasSync={}",
                style.getId(), style.getName(), style.getScope(), skipCanvasSync);

        // 发布Canvas同步消息（仅剧本级风格，且未跳过同步）
        if (!skipCanvasSync && ProjectConstants.Scope.SCRIPT.equals(style.getScope()) && style.getScriptId() != null) {
            canvasMessagePublisher.publishEntityChange(
                    ProjectConstants.EntityType.STYLE,
                    style.getId(),
                    style.getScriptId(),
                    ProjectConstants.EntityType.SCRIPT,
                    style.getScriptId(),
                    workspaceId,
                    "CREATED",
                    toEntityDataMap(style)
            );
        }

        // 发布协作事件（仅剧本级风格）
        if (ProjectConstants.Scope.SCRIPT.equals(style.getScope()) && style.getScriptId() != null) {
            entityChangeEventPublisher.publishEntityCreated(
                    CollabEntityChangeEvent.EntityType.STYLE,
                    style.getId(),
                    style.getScriptId(),
                    toEntityDataMap(style)
            );
        }

        return StyleDetailResponse.fromEntity(style);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<StyleDetailResponse> batchCreate(List<CreateStyleRequest> requests, String workspaceId, String userId) {
        List<StyleDetailResponse> responses = new java.util.ArrayList<>();
        for (CreateStyleRequest request : requests) {
            responses.add(create(request, workspaceId, userId, true));
        }
        log.info("批量创建风格成功: workspaceId={}, count={}", workspaceId, requests.size());
        return responses;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StyleDetailResponse update(String styleId, UpdateStyleRequest request, String userId) {
        Style style = getStyleOrThrow(styleId);

        // 获取保存模式，默认为 NEW_VERSION
        String saveMode = request.getSaveMode();
        if (saveMode == null) {
            saveMode = ProjectConstants.SaveMode.NEW_VERSION;
        }

        // NEW_ENTITY 模式：另存为新实体
        if (ProjectConstants.SaveMode.NEW_ENTITY.equals(saveMode)) {
            CreateStyleRequest newRequest = new CreateStyleRequest();
            newRequest.setScope(style.getScope());
            newRequest.setScriptId(style.getScriptId());
            newRequest.setName(request.getName() != null ? request.getName() : style.getName());
            newRequest.setDescription(request.getDescription() != null ? request.getDescription() : style.getDescription());
            newRequest.setFixedDesc(request.getFixedDesc() != null ? request.getFixedDesc() : style.getFixedDesc());
            newRequest.setStyleParams(request.getStyleParams() != null ? request.getStyleParams() : style.getStyleParams());
            newRequest.setCoverAssetId(request.getCoverAssetId() != null ? request.getCoverAssetId() : style.getCoverAssetId());
            newRequest.setExtraInfo(request.getExtraInfo() != null ? request.getExtraInfo() : style.getExtraInfo());
            return create(newRequest, style.getWorkspaceId(), userId);
        }

        // 检测实际变更，生成变更摘要
        StringBuilder changes = new StringBuilder();
        if (request.getName() != null && !request.getName().equals(style.getName())) {
            changes.append("名称");
        }
        if (request.getDescription() != null && !request.getDescription().equals(style.getDescription())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("描述");
        }
        if (request.getFixedDesc() != null && !request.getFixedDesc().equals(style.getFixedDesc())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("固定描述");
        }
        if (request.getStyleParams() != null && !request.getStyleParams().equals(style.getStyleParams())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("风格参数");
        }
        if (request.getCoverAssetId() != null && !request.getCoverAssetId().equals(style.getCoverAssetId())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("封面");
        }
        if (request.getExtraInfo() != null && !request.getExtraInfo().equals(style.getExtraInfo())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("扩展信息");
        }

        // 1. 先更新数据
        if (request.getName() != null) {
            style.setName(request.getName());
        }
        if (request.getDescription() != null) {
            style.setDescription(request.getDescription());
        }
        if (request.getFixedDesc() != null) {
            style.setFixedDesc(request.getFixedDesc());
        }
        if (request.getStyleParams() != null) {
            style.setStyleParams(request.getStyleParams());
        }
        if (request.getCoverAssetId() != null) {
            style.setCoverAssetId(request.getCoverAssetId());
        }
        if (request.getExtraInfo() != null) {
            style.setExtraInfo(request.getExtraInfo());
        }
        style.setUpdatedBy(userId);
        // 不要手动设置 version，MyBatis Plus @Version 会自动处理

        int rows = styleMapper.updateById(style);
        if (rows == 0) {
            log.warn("风格更新失败（并发冲突）: styleId={}, version={}", styleId, style.getVersion());
            throw new BusinessException(ResultCode.CONCURRENT_OPERATION);
        }

        // 2. NEW_VERSION 模式：仅在有实际变更时创建版本快照（保存更新后的数据）
        // OVERWRITE 模式：跳过版本快照创建
        if (ProjectConstants.SaveMode.NEW_VERSION.equals(saveMode) && !changes.isEmpty()) {
            String changeSummary = "更新" + changes;
            style = getStyleOrThrow(styleId);
            styleVersionService.createVersionSnapshot(style, changeSummary, userId);
        }

        log.info("风格更新成功: styleId={}, versionNumber={}, saveMode={}", styleId, style.getVersionNumber(), saveMode);

        // 发布Canvas同步消息（仅剧本级风格）
        if (ProjectConstants.Scope.SCRIPT.equals(style.getScope()) && style.getScriptId() != null) {
            canvasMessagePublisher.publishEntityChange(
                    ProjectConstants.EntityType.STYLE,
                    style.getId(),
                    style.getScriptId(),
                    ProjectConstants.EntityType.SCRIPT,
                    style.getScriptId(),
                    style.getWorkspaceId(),
                    "UPDATED",
                    toEntityDataMap(style)
            );

            // 发布协作事件
            entityChangeEventPublisher.publishEntityUpdated(
                    CollabEntityChangeEvent.EntityType.STYLE,
                    style.getId(),
                    style.getScriptId(),
                    null,
                    toEntityDataMap(style)
            );
        }

        return StyleDetailResponse.fromEntity(style);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String styleId, String userId) {
        // 先验证风格存在
        Style style = getStyleOrThrow(styleId);
        // 使用 MyBatis-Plus 的 deleteById，会自动转换为逻辑删除（UPDATE SET deleted=1）
        styleMapper.deleteById(styleId);

        log.info("风格删除成功: styleId={}", styleId);

        // 发布Canvas同步消息（仅剧本级风格）
        if (ProjectConstants.Scope.SCRIPT.equals(style.getScope()) && style.getScriptId() != null) {
            canvasMessagePublisher.publishEntityChange(
                    ProjectConstants.EntityType.STYLE,
                    style.getId(),
                    style.getScriptId(),
                    ProjectConstants.EntityType.SCRIPT,
                    style.getScriptId(),
                    style.getWorkspaceId(),
                    "DELETED",
                    null
            );

            // 发布协作事件
            entityChangeEventPublisher.publishEntityDeleted(
                    CollabEntityChangeEvent.EntityType.STYLE,
                    style.getId(),
                    style.getScriptId()
            );
        }
    }

    @Override
    public StyleDetailResponse getById(String styleId) {
        Style style = getStyleOrThrow(styleId);
        StyleDetailResponse response = StyleDetailResponse.fromEntity(style);
        // 填充创建者信息
        if (style.getCreatedBy() != null) {
            UserBasicInfo userInfo = userInfoHelper.getUserInfo(style.getCreatedBy());
            if (userInfo != null) {
                response.setCreatedByUsername(userInfo.getUsername());
                response.setCreatedByNickname(userInfo.getNickname());
            }
        }
        // 填充封面URL
        if (style.getCoverAssetId() != null) {
            try {
                var asset = assetService.getById(style.getCoverAssetId());
                response.setCoverUrl(asset.getThumbnailUrl() != null ? asset.getThumbnailUrl() : asset.getFileUrl());
            } catch (Exception e) {
                log.warn("获取风格封面素材失败: styleId={}, coverAssetId={}", styleId, style.getCoverAssetId());
            }
        }
        return response;
    }

    @Override
    public Optional<Style> findById(String styleId) {
        Style style = styleMapper.selectById(styleId);
        if (style == null || style.getDeleted() == CommonConstants.DELETED) {
            return Optional.empty();
        }
        return Optional.of(style);
    }

    @Override
    public List<StyleListResponse> listWorkspaceStyles(String workspaceId) {
        List<Style> styles = styleMapper.selectWorkspaceStyles(workspaceId);
        return convertToListResponses(styles);
    }

    @Override
    public List<StyleListResponse> listScriptStyles(String scriptId) {
        List<Style> styles = styleMapper.selectScriptStyles(scriptId);
        return convertToListResponses(styles);
    }

    @Override
    public List<StyleListResponse> listAvailableStyles(String workspaceId, String scriptId) {
        List<Style> styles = styleMapper.selectAvailableStyles(workspaceId, scriptId);
        return convertToListResponses(styles);
    }

    @Override
    public List<StyleListResponse> listAvailableStyles(String workspaceId, String scriptId, String keyword, Integer limit) {
        List<Style> styles = styleMapper.selectAvailableStylesFiltered(workspaceId, scriptId, keyword, limit);
        return convertToListResponses(styles);
    }

    @Override
    public Page<StyleListResponse> queryStyles(StyleQueryRequest request, String workspaceId) {
        if ("SYSTEM".equalsIgnoreCase(request.getScope())) {
            return querySystemStyles(request);
        }

        // 跨 schema 可用风格查询：scriptId 非空且未指定具体 scope 时，合并 SYSTEM+WORKSPACE+SCRIPT
        if (StringUtils.hasText(request.getScriptId())
                && !StringUtils.hasText(request.getScope())) {
            int offset = (request.getPageNum() - 1) * request.getPageSize();
            List<Style> records = styleMapper.selectAvailableStylesPaginated(
                    workspaceId, request.getScriptId(), request.getKeyword(),
                    request.getPageSize(), offset);
            long total = styleMapper.countAvailableStyles(
                    workspaceId, request.getScriptId(), request.getKeyword());
            Page<StyleListResponse> responsePage = new Page<>(request.getPageNum(), request.getPageSize(), total);
            responsePage.setRecords(convertToListResponses(records));
            return responsePage;
        }

        Page<Style> page = new Page<>(request.getPageNum(), request.getPageSize());

        LambdaQueryWrapper<Style> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Style::getWorkspaceId, workspaceId)
                .eq(Style::getDeleted, CommonConstants.NOT_DELETED);

        // 作用域过滤
        if (StringUtils.hasText(request.getScope())) {
            wrapper.eq(Style::getScope, request.getScope());
        }
        // 剧本ID过滤
        if (StringUtils.hasText(request.getScriptId())) {
            wrapper.eq(Style::getScriptId, request.getScriptId());
        }
        // 关键词搜索（名称、描述、固定描述、风格参数、附加信息）
        if (StringUtils.hasText(request.getKeyword())) {
            String kw = "%" + request.getKeyword() + "%";
            wrapper.and(w -> w.like(Style::getName, request.getKeyword())
                    .or()
                    .like(Style::getDescription, request.getKeyword())
                    .or()
                    .like(Style::getFixedDesc, request.getKeyword())
                    .or()
                    .apply("style_params::text LIKE {0}", kw)
                    .or()
                    .apply("extra_info::text LIKE {0}", kw));
        }

        // 排序
        String orderBy = request.getOrderBy();
        boolean isAsc = "asc".equalsIgnoreCase(request.getOrderDir());
        switch (orderBy) {
            case "name" -> wrapper.orderBy(true, isAsc, Style::getName);
            case "updated_at" -> wrapper.orderBy(true, isAsc, Style::getUpdatedAt);
            default -> wrapper.orderBy(true, isAsc, Style::getCreatedAt);
        }

        Page<Style> resultPage = styleMapper.selectPage(page, wrapper);

        // 转换为响应对象
        Page<StyleListResponse> responsePage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        responsePage.setRecords(convertToListResponses(resultPage.getRecords()));

        return responsePage;
    }

    private Page<StyleListResponse> querySystemStyles(StyleQueryRequest request) {
        List<Style> all = new ArrayList<>(styleMapper.selectSystemStyles());

        if (StringUtils.hasText(request.getKeyword())) {
            String kw = request.getKeyword().toLowerCase();
            all = all.stream()
                    .filter(s -> (s.getName() != null && s.getName().toLowerCase().contains(kw))
                            || (s.getDescription() != null && s.getDescription().toLowerCase().contains(kw))
                            || (s.getFixedDesc() != null && s.getFixedDesc().toLowerCase().contains(kw))
                            || (s.getStyleParams() != null && s.getStyleParams().toString().toLowerCase().contains(kw))
                            || (s.getExtraInfo() != null && s.getExtraInfo().toString().toLowerCase().contains(kw)))
                    .collect(Collectors.toList());
        }

        boolean isAsc = !"desc".equalsIgnoreCase(request.getOrderDir());
        Comparator<Style> comparator = switch (request.getOrderBy()) {
            case "name" -> Comparator.comparing((Style s) -> s.getName() != null ? s.getName() : "");
            case "updated_at" -> Comparator.comparing((Style s) ->
                    s.getUpdatedAt() != null ? s.getUpdatedAt() : java.time.LocalDateTime.MIN);
            default -> Comparator.comparing((Style s) ->
                    s.getCreatedAt() != null ? s.getCreatedAt() : java.time.LocalDateTime.MIN);
        };
        all.sort(isAsc ? comparator : comparator.reversed());

        long total = all.size();
        int from = (request.getPageNum() - 1) * request.getPageSize();
        int to = Math.min(from + request.getPageSize(), (int) total);
        List<Style> pageData = from >= total ? List.of() : all.subList(from, to);

        Page<StyleListResponse> responsePage = new Page<>(request.getPageNum(), request.getPageSize(), total);
        responsePage.setRecords(convertToListResponses(pageData));
        return responsePage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setCover(String styleId, String assetId, String userId) {
        Style style = getStyleOrThrow(styleId);
        style.setCoverAssetId(assetId);
        styleMapper.updateById(style);

        log.info("风格封面设置成功: styleId={}, assetId={}", styleId, assetId);
    }

    private Style getStyleOrThrow(String styleId) {
        Style style = styleMapper.selectById(styleId);
        if (style == null || style.getDeleted() == CommonConstants.DELETED) {
            throw new BusinessException(ResultCode.STYLE_NOT_FOUND);
        }
        return style;
    }

    /**
     * 转换为列表响应并批量填充用户信息
     */
    private List<StyleListResponse> convertToListResponses(List<Style> styles) {
        if (styles == null || styles.isEmpty()) {
            return List.of();
        }

        List<StyleListResponse> responses = styles.stream()
                .map(StyleListResponse::fromEntity)
                .collect(Collectors.toList());

        // 批量填充用户信息
        populateUserInfo(responses);
        // 批量填充封面URL
        populateCoverUrl(styles, responses);
        return responses;
    }

    private void populateUserInfo(List<StyleListResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return;
        }
        Set<String> userIds = responses.stream()
                .map(StyleListResponse::getCreatedBy)
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

    private void populateCoverUrl(List<Style> styles, List<StyleListResponse> responses) {
        Set<String> coverAssetIds = styles.stream()
                .map(Style::getCoverAssetId)
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
            for (int i = 0; i < styles.size(); i++) {
                String coverAssetId = styles.get(i).getCoverAssetId();
                if (coverAssetId != null && assetUrlMap.containsKey(coverAssetId)) {
                    responses.get(i).setCoverUrl(assetUrlMap.get(coverAssetId));
                }
            }
        } catch (Exception e) {
            log.warn("批量获取风格封面素材失败", e);
        }
    }

    private Map<String, Object> toEntityDataMap(Style style) {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("name", style.getName());
        data.put("description", style.getDescription());
        data.put("version", style.getVersion());
        data.put("coverAssetId", style.getCoverAssetId());
        return data;
    }
}
