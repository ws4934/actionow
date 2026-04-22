package com.actionow.project.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.result.ResultCode;
import com.actionow.project.dto.*;
import com.actionow.project.dto.asset.AssetResponse;
import com.actionow.project.dto.asset.CreateAssetRequest;
import com.actionow.project.dto.library.*;
import com.actionow.project.entity.Asset;
import com.actionow.project.entity.Character;
import com.actionow.project.entity.Prop;
import com.actionow.project.entity.Scene;
import com.actionow.project.entity.Style;
import com.actionow.project.mapper.AssetMapper;
import com.actionow.project.mapper.CharacterMapper;
import com.actionow.project.mapper.PropMapper;
import com.actionow.project.mapper.SceneMapper;
import com.actionow.project.mapper.StyleMapper;
import com.actionow.project.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 公共资源库服务实现
 * 数据源：tenant_system schema（scope = SYSTEM 的已发布资源）
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublicLibraryServiceImpl implements PublicLibraryService {

    private final CharacterMapper characterMapper;
    private final SceneMapper sceneMapper;
    private final PropMapper propMapper;
    private final StyleMapper styleMapper;
    private final AssetMapper assetMapper;

    private final CharacterService characterService;
    private final SceneService sceneService;
    private final PropService propService;
    private final StyleService styleService;
    private final AssetService assetService;

    // ==================== 角色 ====================

    @Override
    public Page<LibraryCharacterResponse> listCharacters(LibraryQueryRequest request) {
        List<Character> all = characterMapper.selectSystemCharacters();
        all = filterByKeyword(all, request.getKeyword(),
                c -> c.getName() + " " + (c.getDescription() != null ? c.getDescription() : ""));
        all = sortEntities(all, request.getOrderBy(), request.getOrderDir(),
                c -> c.getPublishedAt(), c -> c.getName());
        Map<String, String> coverUrls = buildCoverUrlMap(
                all.stream().map(Character::getCoverAssetId).filter(Objects::nonNull).distinct().toList());
        return toPage(all, request.getPageNum(), request.getPageSize(), c -> {
            LibraryCharacterResponse r = LibraryCharacterResponse.fromEntity(c);
            r.setCoverUrl(coverUrls.get(c.getCoverAssetId()));
            return r;
        });
    }

    @Override
    public LibraryCharacterResponse getCharacter(String id) {
        Character c = characterMapper.selectSystemCharacterById(id);
        if (c == null) throw new BusinessException(ResultCode.LIBRARY_RESOURCE_NOT_FOUND);
        LibraryCharacterResponse r = LibraryCharacterResponse.fromEntity(c);
        r.setCoverUrl(resolveCoverUrl(c.getCoverAssetId()));
        return r;
    }

    @Override
    public CharacterDetailResponse copyCharacterToWorkspace(String characterId, String workspaceId, String userId) {
        Character source = characterMapper.selectSystemCharacterById(characterId);
        if (source == null) throw new BusinessException(ResultCode.LIBRARY_RESOURCE_NOT_FOUND);

        CreateCharacterRequest req = new CreateCharacterRequest();
        req.setScope("WORKSPACE");
        req.setName(source.getName());
        req.setDescription(source.getDescription());
        req.setFixedDesc(source.getFixedDesc());
        req.setAge(source.getAge());
        req.setGender(source.getGender());
        req.setCharacterType(source.getCharacterType());
        req.setAppearanceData(source.getAppearanceData());
        // 记录来源
        Map<String, Object> extraInfo = new HashMap<>();
        extraInfo.put("copiedFromLibrary", true);
        extraInfo.put("sourceSystemId", source.getId());
        req.setExtraInfo(extraInfo);

        log.info("复制公共库角色: characterId={}, workspaceId={}", characterId, workspaceId);
        return characterService.create(req, workspaceId, userId);
    }

    // ==================== 场景 ====================

    @Override
    public Page<LibrarySceneResponse> listScenes(LibraryQueryRequest request) {
        List<Scene> all = sceneMapper.selectSystemScenes();
        all = filterByKeyword(all, request.getKeyword(),
                s -> s.getName() + " " + (s.getDescription() != null ? s.getDescription() : ""));
        all = sortEntities(all, request.getOrderBy(), request.getOrderDir(),
                s -> s.getPublishedAt(), s -> s.getName());
        Map<String, String> coverUrls = buildCoverUrlMap(
                all.stream().map(Scene::getCoverAssetId).filter(Objects::nonNull).distinct().toList());
        return toPage(all, request.getPageNum(), request.getPageSize(), s -> {
            LibrarySceneResponse r = LibrarySceneResponse.fromEntity(s);
            r.setCoverUrl(coverUrls.get(s.getCoverAssetId()));
            return r;
        });
    }

    @Override
    public LibrarySceneResponse getScene(String id) {
        Scene s = sceneMapper.selectSystemSceneById(id);
        if (s == null) throw new BusinessException(ResultCode.LIBRARY_RESOURCE_NOT_FOUND);
        LibrarySceneResponse r = LibrarySceneResponse.fromEntity(s);
        r.setCoverUrl(resolveCoverUrl(s.getCoverAssetId()));
        return r;
    }

    @Override
    public SceneDetailResponse copySceneToWorkspace(String sceneId, String workspaceId, String userId) {
        Scene source = sceneMapper.selectSystemSceneById(sceneId);
        if (source == null) throw new BusinessException(ResultCode.LIBRARY_RESOURCE_NOT_FOUND);

        CreateSceneRequest req = new CreateSceneRequest();
        req.setScope("WORKSPACE");
        req.setName(source.getName());
        req.setDescription(source.getDescription());
        req.setFixedDesc(source.getFixedDesc());
        req.setSceneType(source.getSceneType());
        req.setAppearanceData(source.getAppearanceData());
        Map<String, Object> extraInfo = new HashMap<>();
        extraInfo.put("copiedFromLibrary", true);
        extraInfo.put("sourceSystemId", source.getId());
        req.setExtraInfo(extraInfo);

        log.info("复制公共库场景: sceneId={}, workspaceId={}", sceneId, workspaceId);
        return sceneService.create(req, workspaceId, userId);
    }

    // ==================== 道具 ====================

    @Override
    public Page<LibraryPropResponse> listProps(LibraryQueryRequest request) {
        List<Prop> all = propMapper.selectSystemProps();
        all = filterByKeyword(all, request.getKeyword(),
                p -> p.getName() + " " + (p.getDescription() != null ? p.getDescription() : ""));
        all = sortEntities(all, request.getOrderBy(), request.getOrderDir(),
                p -> p.getPublishedAt(), p -> p.getName());
        Map<String, String> coverUrls = buildCoverUrlMap(
                all.stream().map(Prop::getCoverAssetId).filter(Objects::nonNull).distinct().toList());
        return toPage(all, request.getPageNum(), request.getPageSize(), p -> {
            LibraryPropResponse r = LibraryPropResponse.fromEntity(p);
            r.setCoverUrl(coverUrls.get(p.getCoverAssetId()));
            return r;
        });
    }

    @Override
    public LibraryPropResponse getProp(String id) {
        Prop p = propMapper.selectSystemPropById(id);
        if (p == null) throw new BusinessException(ResultCode.LIBRARY_RESOURCE_NOT_FOUND);
        LibraryPropResponse r = LibraryPropResponse.fromEntity(p);
        r.setCoverUrl(resolveCoverUrl(p.getCoverAssetId()));
        return r;
    }

    @Override
    public PropDetailResponse copyPropToWorkspace(String propId, String workspaceId, String userId) {
        Prop source = propMapper.selectSystemPropById(propId);
        if (source == null) throw new BusinessException(ResultCode.LIBRARY_RESOURCE_NOT_FOUND);

        CreatePropRequest req = new CreatePropRequest();
        req.setScope("WORKSPACE");
        req.setName(source.getName());
        req.setDescription(source.getDescription());
        req.setFixedDesc(source.getFixedDesc());
        req.setPropType(source.getPropType());
        req.setAppearanceData(source.getAppearanceData());
        Map<String, Object> extraInfo = new HashMap<>();
        extraInfo.put("copiedFromLibrary", true);
        extraInfo.put("sourceSystemId", source.getId());
        req.setExtraInfo(extraInfo);

        log.info("复制公共库道具: propId={}, workspaceId={}", propId, workspaceId);
        return propService.create(req, workspaceId, userId);
    }

    // ==================== 风格 ====================

    @Override
    public Page<LibraryStyleResponse> listStyles(LibraryQueryRequest request) {
        List<Style> all = styleMapper.selectSystemStyles();
        all = filterByKeyword(all, request.getKeyword(),
                s -> s.getName() + " " + (s.getDescription() != null ? s.getDescription() : ""));
        all = sortEntities(all, request.getOrderBy(), request.getOrderDir(),
                s -> s.getPublishedAt(), s -> s.getName());
        Map<String, String> coverUrls = buildCoverUrlMap(
                all.stream().map(Style::getCoverAssetId).filter(Objects::nonNull).distinct().toList());
        return toPage(all, request.getPageNum(), request.getPageSize(), s -> {
            LibraryStyleResponse r = LibraryStyleResponse.fromEntity(s);
            r.setCoverUrl(coverUrls.get(s.getCoverAssetId()));
            return r;
        });
    }

    @Override
    public LibraryStyleResponse getStyle(String id) {
        Style s = styleMapper.selectSystemStyleById(id);
        if (s == null) throw new BusinessException(ResultCode.LIBRARY_RESOURCE_NOT_FOUND);
        LibraryStyleResponse r = LibraryStyleResponse.fromEntity(s);
        r.setCoverUrl(resolveCoverUrl(s.getCoverAssetId()));
        return r;
    }

    @Override
    public StyleDetailResponse copyStyleToWorkspace(String styleId, String workspaceId, String userId) {
        Style source = styleMapper.selectSystemStyleById(styleId);
        if (source == null) throw new BusinessException(ResultCode.LIBRARY_RESOURCE_NOT_FOUND);

        CreateStyleRequest req = new CreateStyleRequest();
        req.setScope("WORKSPACE");
        req.setName(source.getName());
        req.setDescription(source.getDescription());
        req.setFixedDesc(source.getFixedDesc());
        req.setStyleParams(source.getStyleParams());
        Map<String, Object> extraInfo = new HashMap<>();
        extraInfo.put("copiedFromLibrary", true);
        extraInfo.put("sourceSystemId", source.getId());
        req.setExtraInfo(extraInfo);

        log.info("复制公共库风格: styleId={}, workspaceId={}", styleId, workspaceId);
        return styleService.create(req, workspaceId, userId);
    }

    // ==================== 素材 ====================

    @Override
    public Page<LibraryAssetResponse> listAssets(LibraryQueryRequest request) {
        List<Asset> all = assetMapper.selectSystemAssets();
        all = filterByKeyword(all, request.getKeyword(),
                a -> a.getName() + " " + (a.getDescription() != null ? a.getDescription() : ""));
        all = sortEntities(all, request.getOrderBy(), request.getOrderDir(),
                a -> a.getPublishedAt(), a -> a.getName());
        return toPage(all, request.getPageNum(), request.getPageSize(),
                a -> LibraryAssetResponse.fromEntity(a));
    }

    @Override
    public LibraryAssetResponse getAsset(String id) {
        Asset a = assetMapper.selectSystemAssetById(id);
        if (a == null) throw new BusinessException(ResultCode.LIBRARY_RESOURCE_NOT_FOUND);
        return LibraryAssetResponse.fromEntity(a);
    }

    @Override
    public AssetResponse copyAssetToWorkspace(String assetId, String workspaceId, String userId) {
        Asset source = assetMapper.selectSystemAssetById(assetId);
        if (source == null) throw new BusinessException(ResultCode.LIBRARY_RESOURCE_NOT_FOUND);

        CreateAssetRequest req = new CreateAssetRequest();
        req.setScope("WORKSPACE");
        req.setName(source.getName());
        req.setDescription(source.getDescription());
        req.setAssetType(source.getAssetType());
        req.setSource("SYSTEM");
        req.setFileSize(source.getFileSize());
        req.setMimeType(source.getMimeType());
        req.setGenerationStatus("COMPLETED");
        Map<String, Object> extraInfo = new HashMap<>();
        extraInfo.put("copiedFromLibrary", true);
        extraInfo.put("sourceSystemId", source.getId());
        req.setExtraInfo(extraInfo);

        AssetResponse created = assetService.create(req, workspaceId, userId, true);
        // 同步文件信息（共用系统素材的文件，不重复上传）
        if (created != null && source.getFileUrl() != null) {
            assetService.updateFileInfo(created.getId(),
                    source.getFileKey(), source.getFileUrl(), source.getThumbnailUrl(),
                    source.getFileSize(), source.getMimeType(), source.getMetaInfo());
        }
        log.info("复制公共库素材: assetId={}, workspaceId={}", assetId, workspaceId);
        return created;
    }

    // ==================== 工具方法 ====================

    @FunctionalInterface
    private interface TextExtractor<T> {
        String extract(T entity);
    }

    @FunctionalInterface
    private interface TimeExtractor<T> {
        java.time.LocalDateTime extract(T entity);
    }

    private <T> List<T> filterByKeyword(List<T> list, String keyword, TextExtractor<T> extractor) {
        if (!StringUtils.hasText(keyword)) return list;
        String kw = keyword.toLowerCase();
        return list.stream()
                .filter(e -> extractor.extract(e) != null && extractor.extract(e).toLowerCase().contains(kw))
                .collect(Collectors.toList());
    }

    private <T> List<T> sortEntities(List<T> list, String orderBy, String orderDir,
                                      TimeExtractor<T> timeExtractor, TextExtractor<T> nameExtractor) {
        boolean asc = "asc".equalsIgnoreCase(orderDir);
        Comparator<T> comparator = "name".equalsIgnoreCase(orderBy)
                ? Comparator.comparing(e -> nameExtractor.extract(e) != null ? nameExtractor.extract(e) : "")
                : Comparator.comparing(e -> timeExtractor.extract(e) != null ? timeExtractor.extract(e) : java.time.LocalDateTime.MIN);
        list = new ArrayList<>(list);
        list.sort(asc ? comparator : comparator.reversed());
        return list;
    }

    private <T, R> Page<R> toPage(List<T> all, int pageNum, int pageSize, java.util.function.Function<T, R> mapper) {
        long total = all.size();
        int from = (pageNum - 1) * pageSize;
        int to = Math.min(from + pageSize, (int) total);
        List<T> pageData = from >= total ? List.of() : all.subList(from, to);
        Page<R> page = new Page<>(pageNum, pageSize, total);
        page.setRecords(pageData.stream().map(mapper).collect(Collectors.toList()));
        return page;
    }

    /**
     * 批量加载封面 URL：将 coverAssetId 列表映射为 id → URL（优先 thumbnailUrl，其次 fileUrl）
     */
    private Map<String, String> buildCoverUrlMap(List<String> assetIds) {
        if (assetIds.isEmpty()) return new HashMap<>();
        return assetMapper.selectSystemAssetsByIds(assetIds).stream()
                .collect(Collectors.toMap(
                        Asset::getId,
                        a -> a.getThumbnailUrl() != null ? a.getThumbnailUrl() : a.getFileUrl(),
                        (a, b) -> a));
    }

    /**
     * 单个封面 URL 解析（用于详情接口）
     */
    private String resolveCoverUrl(String coverAssetId) {
        if (coverAssetId == null) return null;
        Asset asset = assetMapper.selectSystemAssetById(coverAssetId);
        if (asset == null) return null;
        return asset.getThumbnailUrl() != null ? asset.getThumbnailUrl() : asset.getFileUrl();
    }
}
