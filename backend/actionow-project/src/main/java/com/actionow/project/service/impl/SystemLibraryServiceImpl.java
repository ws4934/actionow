package com.actionow.project.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.common.core.constant.CommonConstants;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.result.ResultCode;
import com.actionow.project.dto.library.*;
import com.actionow.project.entity.Character;
import com.actionow.project.entity.Prop;
import com.actionow.project.entity.Scene;
import com.actionow.project.entity.Style;
import com.actionow.project.entity.Asset;
import com.actionow.project.mapper.CharacterMapper;
import com.actionow.project.mapper.PropMapper;
import com.actionow.project.mapper.SceneMapper;
import com.actionow.project.mapper.StyleMapper;
import com.actionow.project.mapper.AssetMapper;
import com.actionow.project.service.SystemLibraryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 系统管理员资源库服务实现
 * 仅系统租户 Admin+ 可调用（由 @RequireSystemTenant(minRole="Admin") 保护）
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemLibraryServiceImpl implements SystemLibraryService {

    private final CharacterMapper characterMapper;
    private final SceneMapper sceneMapper;
    private final PropMapper propMapper;
    private final StyleMapper styleMapper;
    private final AssetMapper assetMapper;

    // ==================== 角色 ====================

    @Override
    public Page<SystemCharacterResponse> listSystemCharacters(SystemLibraryQueryRequest request) {
        List<Character> all = characterMapper.selectSystemCharacterDrafts();
        all = filterByScope(all, request.getScope(), c -> c.getScope());
        all = filterByKeyword(all, request.getKeyword(), c -> c.getName() + " " + orEmpty(c.getDescription()));
        all = sort(all, request.getOrderBy(), request.getOrderDir(),
                c -> c.getCreatedAt(), c -> c.getName());
        Map<String, String> coverUrls = buildCoverUrlMap(
                all.stream().map(Character::getCoverAssetId).filter(Objects::nonNull).distinct().toList());
        return toPage(all, request.getPageNum(), request.getPageSize(), c -> {
            SystemCharacterResponse r = SystemCharacterResponse.fromEntity(c);
            r.setCoverUrl(coverUrls.get(c.getCoverAssetId()));
            return r;
        });
    }

    @Override
    public void publishCharacter(String id, PublishResourceRequest request, String operatorId) {
        Character c = characterMapper.selectSystemCharacterById(id);
        if (c == null) throw new BusinessException(ResultCode.LIBRARY_RESOURCE_NOT_FOUND);
        if (CommonConstants.SCOPE_SYSTEM.equals(c.getScope())) {
            throw new BusinessException(ResultCode.RESOURCE_ALREADY_PUBLISHED);
        }
        int rows = characterMapper.publishCharacter(id, LocalDateTime.now(), operatorId,
                request != null ? request.getPublishNote() : null);
        if (rows == 0) throw new BusinessException(ResultCode.LIBRARY_RESOURCE_NOT_FOUND);
        log.info("角色发布到公共库: id={}, operatorId={}", id, operatorId);
    }

    @Override
    public void unpublishCharacter(String id, String operatorId) {
        Character c = characterMapper.selectSystemCharacterById(id);
        if (c == null) throw new BusinessException(ResultCode.LIBRARY_RESOURCE_NOT_FOUND);
        if (!CommonConstants.SCOPE_SYSTEM.equals(c.getScope())) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_PUBLISHED);
        }
        characterMapper.unpublishCharacter(id, operatorId);
        log.info("角色已从公共库下架: id={}, operatorId={}", id, operatorId);
    }

    // ==================== 场景 ====================

    @Override
    public Page<SystemSceneResponse> listSystemScenes(SystemLibraryQueryRequest request) {
        List<Scene> all = sceneMapper.selectSystemSceneDrafts();
        all = filterByScope(all, request.getScope(), s -> s.getScope());
        all = filterByKeyword(all, request.getKeyword(), s -> s.getName() + " " + orEmpty(s.getDescription()));
        all = sort(all, request.getOrderBy(), request.getOrderDir(),
                s -> s.getCreatedAt(), s -> s.getName());
        Map<String, String> coverUrls = buildCoverUrlMap(
                all.stream().map(Scene::getCoverAssetId).filter(Objects::nonNull).distinct().toList());
        return toPage(all, request.getPageNum(), request.getPageSize(), s -> {
            SystemSceneResponse r = SystemSceneResponse.fromEntity(s);
            r.setCoverUrl(coverUrls.get(s.getCoverAssetId()));
            return r;
        });
    }

    @Override
    public void publishScene(String id, PublishResourceRequest request, String operatorId) {
        Scene s = sceneMapper.selectSystemSceneById(id);
        if (s == null) throw new BusinessException(ResultCode.LIBRARY_RESOURCE_NOT_FOUND);
        if (CommonConstants.SCOPE_SYSTEM.equals(s.getScope()))
            throw new BusinessException(ResultCode.RESOURCE_ALREADY_PUBLISHED);
        sceneMapper.publishScene(id, LocalDateTime.now(), operatorId,
                request != null ? request.getPublishNote() : null);
        log.info("场景发布到公共库: id={}", id);
    }

    @Override
    public void unpublishScene(String id, String operatorId) {
        Scene s = sceneMapper.selectSystemSceneById(id);
        if (s == null) throw new BusinessException(ResultCode.LIBRARY_RESOURCE_NOT_FOUND);
        if (!CommonConstants.SCOPE_SYSTEM.equals(s.getScope()))
            throw new BusinessException(ResultCode.RESOURCE_NOT_PUBLISHED);
        sceneMapper.unpublishScene(id, operatorId);
        log.info("场景已从公共库下架: id={}", id);
    }

    // ==================== 道具 ====================

    @Override
    public Page<SystemPropResponse> listSystemProps(SystemLibraryQueryRequest request) {
        List<Prop> all = propMapper.selectSystemPropDrafts();
        all = filterByScope(all, request.getScope(), p -> p.getScope());
        all = filterByKeyword(all, request.getKeyword(), p -> p.getName() + " " + orEmpty(p.getDescription()));
        all = sort(all, request.getOrderBy(), request.getOrderDir(),
                p -> p.getCreatedAt(), p -> p.getName());
        Map<String, String> coverUrls = buildCoverUrlMap(
                all.stream().map(Prop::getCoverAssetId).filter(Objects::nonNull).distinct().toList());
        return toPage(all, request.getPageNum(), request.getPageSize(), p -> {
            SystemPropResponse r = SystemPropResponse.fromEntity(p);
            r.setCoverUrl(coverUrls.get(p.getCoverAssetId()));
            return r;
        });
    }

    @Override
    public void publishProp(String id, PublishResourceRequest request, String operatorId) {
        Prop p = propMapper.selectSystemPropById(id);
        if (p == null) throw new BusinessException(ResultCode.LIBRARY_RESOURCE_NOT_FOUND);
        if (CommonConstants.SCOPE_SYSTEM.equals(p.getScope()))
            throw new BusinessException(ResultCode.RESOURCE_ALREADY_PUBLISHED);
        propMapper.publishProp(id, LocalDateTime.now(), operatorId,
                request != null ? request.getPublishNote() : null);
        log.info("道具发布到公共库: id={}", id);
    }

    @Override
    public void unpublishProp(String id, String operatorId) {
        Prop p = propMapper.selectSystemPropById(id);
        if (p == null) throw new BusinessException(ResultCode.LIBRARY_RESOURCE_NOT_FOUND);
        if (!CommonConstants.SCOPE_SYSTEM.equals(p.getScope()))
            throw new BusinessException(ResultCode.RESOURCE_NOT_PUBLISHED);
        propMapper.unpublishProp(id, operatorId);
        log.info("道具已从公共库下架: id={}", id);
    }

    // ==================== 风格 ====================

    @Override
    public Page<SystemStyleResponse> listSystemStyles(SystemLibraryQueryRequest request) {
        List<Style> all = styleMapper.selectSystemStyleDrafts();
        all = filterByScope(all, request.getScope(), s -> s.getScope());
        all = filterByKeyword(all, request.getKeyword(), s -> s.getName() + " " + orEmpty(s.getDescription()));
        all = sort(all, request.getOrderBy(), request.getOrderDir(),
                s -> s.getCreatedAt(), s -> s.getName());
        Map<String, String> coverUrls = buildCoverUrlMap(
                all.stream().map(Style::getCoverAssetId).filter(Objects::nonNull).distinct().toList());
        return toPage(all, request.getPageNum(), request.getPageSize(), s -> {
            SystemStyleResponse r = SystemStyleResponse.fromEntity(s);
            r.setCoverUrl(coverUrls.get(s.getCoverAssetId()));
            return r;
        });
    }

    @Override
    public void publishStyle(String id, PublishResourceRequest request, String operatorId) {
        Style s = styleMapper.selectSystemStyleById(id);
        if (s == null) throw new BusinessException(ResultCode.LIBRARY_RESOURCE_NOT_FOUND);
        if (CommonConstants.SCOPE_SYSTEM.equals(s.getScope()))
            throw new BusinessException(ResultCode.RESOURCE_ALREADY_PUBLISHED);
        styleMapper.publishStyle(id, LocalDateTime.now(), operatorId,
                request != null ? request.getPublishNote() : null);
        log.info("风格发布到公共库: id={}", id);
    }

    @Override
    public void unpublishStyle(String id, String operatorId) {
        Style s = styleMapper.selectSystemStyleById(id);
        if (s == null) throw new BusinessException(ResultCode.LIBRARY_RESOURCE_NOT_FOUND);
        if (!CommonConstants.SCOPE_SYSTEM.equals(s.getScope()))
            throw new BusinessException(ResultCode.RESOURCE_NOT_PUBLISHED);
        styleMapper.unpublishStyle(id, operatorId);
        log.info("风格已从公共库下架: id={}", id);
    }

    // ==================== 素材 ====================

    @Override
    public Page<SystemAssetResponse> listSystemAssets(SystemLibraryQueryRequest request) {
        List<com.actionow.project.entity.Asset> all = assetMapper.selectSystemAssetDrafts();
        all = filterByScope(all, request.getScope(), a -> a.getScope());
        all = filterByKeyword(all, request.getKeyword(), a -> a.getName() + " " + orEmpty(a.getDescription()));
        all = sort(all, request.getOrderBy(), request.getOrderDir(),
                a -> a.getCreatedAt(), a -> a.getName());
        return toPage(all, request.getPageNum(), request.getPageSize(), SystemAssetResponse::fromEntity);
    }

    @Override
    public void publishAsset(String id, PublishResourceRequest request, String operatorId) {
        com.actionow.project.entity.Asset a = assetMapper.selectSystemAssetById(id);
        if (a == null) throw new BusinessException(ResultCode.LIBRARY_RESOURCE_NOT_FOUND);
        if (CommonConstants.SCOPE_SYSTEM.equals(a.getScope()))
            throw new BusinessException(ResultCode.RESOURCE_ALREADY_PUBLISHED);
        int rows = assetMapper.publishAsset(id, LocalDateTime.now(), operatorId,
                request != null ? request.getPublishNote() : null);
        if (rows == 0) throw new BusinessException(ResultCode.LIBRARY_RESOURCE_NOT_FOUND);
        log.info("素材发布到公共库: id={}, operatorId={}", id, operatorId);
    }

    @Override
    public void unpublishAsset(String id, String operatorId) {
        com.actionow.project.entity.Asset a = assetMapper.selectSystemAssetById(id);
        if (a == null) throw new BusinessException(ResultCode.LIBRARY_RESOURCE_NOT_FOUND);
        if (!CommonConstants.SCOPE_SYSTEM.equals(a.getScope()))
            throw new BusinessException(ResultCode.RESOURCE_NOT_PUBLISHED);
        assetMapper.unpublishAsset(id, operatorId);
        log.info("素材已从公共库下架: id={}, operatorId={}", id, operatorId);
    }

    // ==================== 工具方法 ====================

    private <T> List<T> filterByScope(List<T> list, String scope, java.util.function.Function<T, String> scopeGetter) {
        if (!StringUtils.hasText(scope)) return list;
        return list.stream()
                .filter(e -> scope.equalsIgnoreCase(scopeGetter.apply(e)))
                .collect(Collectors.toList());
    }

    private <T> List<T> filterByKeyword(List<T> list, String keyword, java.util.function.Function<T, String> textGetter) {
        if (!StringUtils.hasText(keyword)) return list;
        String kw = keyword.toLowerCase();
        return list.stream()
                .filter(e -> textGetter.apply(e) != null && textGetter.apply(e).toLowerCase().contains(kw))
                .collect(Collectors.toList());
    }

    private <T> List<T> sort(List<T> list, String orderBy, String orderDir,
                              java.util.function.Function<T, LocalDateTime> timeGetter,
                              java.util.function.Function<T, String> nameGetter) {
        boolean asc = "asc".equalsIgnoreCase(orderDir);
        java.util.Comparator<T> comparator = "name".equalsIgnoreCase(orderBy)
                ? java.util.Comparator.comparing(e -> nameGetter.apply(e) != null ? nameGetter.apply(e) : "")
                : java.util.Comparator.comparing(e -> timeGetter.apply(e) != null ? timeGetter.apply(e) : LocalDateTime.MIN);
        List<T> result = new ArrayList<>(list);
        result.sort(asc ? comparator : comparator.reversed());
        return result;
    }

    private <T, R> Page<R> toPage(List<T> all, int pageNum, int pageSize,
                                   java.util.function.Function<T, R> mapper) {
        long total = all.size();
        int from = (pageNum - 1) * pageSize;
        int to = Math.min(from + pageSize, (int) total);
        List<T> pageData = from >= total ? List.of() : all.subList(from, to);
        Page<R> page = new Page<>(pageNum, pageSize, total);
        page.setRecords(pageData.stream().map(mapper).collect(Collectors.toList()));
        return page;
    }

    private String orEmpty(String s) {
        return s != null ? s : "";
    }

    private Map<String, String> buildCoverUrlMap(List<String> assetIds) {
        if (assetIds.isEmpty()) return new HashMap<>();
        return assetMapper.selectSystemAssetsByIds(assetIds).stream()
                .collect(Collectors.toMap(
                        Asset::getId,
                        a -> a.getThumbnailUrl() != null ? a.getThumbnailUrl() : a.getFileUrl(),
                        (a, b) -> a));
    }
}
