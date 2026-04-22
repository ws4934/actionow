package com.actionow.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.common.core.constant.CommonConstants;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.id.UuidGenerator;
import com.actionow.common.core.result.ResultCode;
import com.actionow.common.mq.message.CollabEntityChangeEvent;
import com.actionow.common.mq.publisher.CanvasMessagePublisher;
import com.actionow.common.mq.publisher.CanvasMessagePublisher.RelatedEntity;
import com.actionow.common.file.service.FileStorageService;
import com.actionow.common.file.dto.PresignedUploadRequest;
import com.actionow.common.file.dto.PresignedUploadResult;
import com.actionow.project.constant.ProjectConstants;
import com.actionow.project.dto.asset.*;
import com.actionow.project.entity.Asset;
import com.actionow.project.feign.UserBasicInfo;
import com.actionow.project.dto.relation.CreateEntityAssetRelationRequest;
import com.actionow.project.mapper.AssetMapper;
import com.actionow.project.mapper.EntityAssetRelationMapper;
import com.actionow.project.publisher.EntityChangeEventPublisher;
import com.actionow.project.service.AssetService;
import com.actionow.project.service.EntityAssetRelationService;
import com.actionow.project.service.UserInfoHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 素材服务实现
 * 负责素材实体的 CRUD 操作，文件操作通过 common-file 模块直接操作
 *
 * @author Actionow
 */
@Slf4j
@Service
public class AssetServiceImpl implements AssetService {

    private final AssetMapper assetMapper;
    private final EntityAssetRelationMapper entityAssetRelationMapper;
    private final FileStorageService fileStorageService;
    private final CanvasMessagePublisher canvasMessagePublisher;
    private final EntityChangeEventPublisher entityChangeEventPublisher;
    private final UserInfoHelper userInfoHelper;
    private final EntityAssetRelationService entityAssetRelationService;

    public AssetServiceImpl(
            AssetMapper assetMapper,
            EntityAssetRelationMapper entityAssetRelationMapper,
            FileStorageService fileStorageService,
            CanvasMessagePublisher canvasMessagePublisher,
            EntityChangeEventPublisher entityChangeEventPublisher,
            UserInfoHelper userInfoHelper,
            @Lazy EntityAssetRelationService entityAssetRelationService) {
        this.assetMapper = assetMapper;
        this.entityAssetRelationMapper = entityAssetRelationMapper;
        this.fileStorageService = fileStorageService;
        this.canvasMessagePublisher = canvasMessagePublisher;
        this.entityChangeEventPublisher = entityChangeEventPublisher;
        this.userInfoHelper = userInfoHelper;
        this.entityAssetRelationService = entityAssetRelationService;
    }

    /**
     * 素材作用域
     */
    private static final class AssetScope {
        static final String WORKSPACE = "WORKSPACE";
        static final String SCRIPT = "SCRIPT";
    }

    /**
     * 素材来源
     */
    private static final class AssetSource {
        static final String UPLOAD = "UPLOAD";
        static final String AI_GENERATED = "AI_GENERATED";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssetResponse create(CreateAssetRequest request, String workspaceId, String userId) {
        return create(request, workspaceId, userId, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssetResponse create(CreateAssetRequest request, String workspaceId, String userId, boolean skipCanvasSync) {
        // 处理作用域：有 scriptId 且无关联实体时默认挂到剧本
        String scope = StringUtils.hasText(request.getScope()) ? request.getScope() : AssetScope.WORKSPACE;
        if (!StringUtils.hasText(request.getScope())
                && StringUtils.hasText(request.getScriptId())
                && (request.getRelatedEntities() == null || request.getRelatedEntities().isEmpty())) {
            scope = AssetScope.SCRIPT;
        }
        if (AssetScope.SCRIPT.equals(scope) && !StringUtils.hasText(request.getScriptId())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "剧本级素材必须指定剧本ID");
        }

        // 创建素材记录
        Asset asset = new Asset();
        asset.setId(UuidGenerator.generateUuidV7());
        asset.setWorkspaceId(workspaceId);
        asset.setScope(scope);
        asset.setScriptId(request.getScriptId());
        asset.setName(request.getName());
        asset.setDescription(request.getDescription());
        asset.setAssetType(request.getAssetType());
        asset.setSource(StringUtils.hasText(request.getSource()) ? request.getSource() : AssetSource.UPLOAD);
        asset.setFileSize(request.getFileSize());
        asset.setMimeType(request.getMimeType());
        asset.setExtraInfo(request.getExtraInfo());
        asset.setCreatedBy(userId);
        asset.setVersionNumber(1);
        // 设置生成状态（AI生成时使用）
        if (StringUtils.hasText(request.getGenerationStatus())) {
            asset.setGenerationStatus(request.getGenerationStatus());
        }

        assetMapper.insert(asset);

        log.info("素材创建成功: assetId={}, name={}, workspaceId={}, skipCanvasSync={}",
                asset.getId(), asset.getName(), workspaceId, skipCanvasSync);

        // 发布Canvas同步消息（如果不跳过，且有 scriptId）
        if (!skipCanvasSync && asset.getScriptId() != null) {
            // 转换关联实体列表
            List<RelatedEntity> relatedEntities = null;
            if (request.getRelatedEntities() != null && !request.getRelatedEntities().isEmpty()) {
                relatedEntities = request.getRelatedEntities().stream()
                        .map(r -> RelatedEntity.builder()
                                .entityType(r.getEntityType())
                                .entityId(r.getEntityId())
                                .relationType(r.getRelationType())
                                .build())
                        .collect(Collectors.toList());
            }

            canvasMessagePublisher.publishEntityChange(
                    ProjectConstants.EntityType.ASSET,
                    asset.getId(),
                    asset.getScriptId(),
                    ProjectConstants.EntityType.SCRIPT,
                    asset.getScriptId(),
                    workspaceId,
                    ProjectConstants.ChangeType.CREATED,
                    null,
                    relatedEntities,
                    false
            );
        }

        // 发布协作实体变更事件（WebSocket通知）
        if (asset.getScriptId() != null) {
            entityChangeEventPublisher.publishEntityCreated(
                    CollabEntityChangeEvent.EntityType.ASSET,
                    asset.getId(),
                    asset.getScriptId(),
                    toEntityDataMap(asset)
            );
        }

        return enrichWithFileUrl(AssetResponse.fromEntity(asset));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssetResponse update(String assetId, UpdateAssetRequest request, String userId) {
        Asset asset = getAssetOrThrow(assetId);

        // 更新字段
        if (StringUtils.hasText(request.getName())) {
            asset.setName(request.getName());
        }
        if (request.getDescription() != null) {
            asset.setDescription(request.getDescription());
        }
        if (request.getExtraInfo() != null) {
            asset.setExtraInfo(request.getExtraInfo());
        }

        // 版本号+1
        asset.setVersionNumber(asset.getVersionNumber() + 1);

        int rows = assetMapper.updateById(asset);
        if (rows == 0) {
            log.warn("素材更新失败（可能已被其他请求修改）: assetId={}", assetId);
            throw new BusinessException("素材已被修改，请刷新后重试");
        }

        log.info("素材更新成功: assetId={}, version={}", assetId, asset.getVersionNumber());

        // 发布Canvas同步消息
        canvasMessagePublisher.publishEntityChange(
                ProjectConstants.EntityType.ASSET,
                asset.getId(),
                asset.getScriptId(),
                ProjectConstants.EntityType.SCRIPT,
                asset.getScriptId(),
                asset.getWorkspaceId(),
                ProjectConstants.ChangeType.UPDATED,
                null
        );

        // 发布协作实体变更事件（WebSocket通知）
        if (asset.getScriptId() != null) {
            entityChangeEventPublisher.publishEntityUpdated(
                    CollabEntityChangeEvent.EntityType.ASSET,
                    asset.getId(),
                    asset.getScriptId(),
                    null,
                    toEntityDataMap(asset)
            );
        }

        return enrichWithFileUrl(AssetResponse.fromEntity(asset));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String assetId, String userId) {
        Asset asset = getAssetOrThrow(assetId);

        String trashPath = null;
        // 将文件移动到回收站（软删除）
        if (StringUtils.hasText(asset.getFileKey())) {
            try {
                trashPath = fileStorageService.moveToTrash(asset.getFileKey(), asset.getWorkspaceId());
                if (trashPath != null) {
                    log.info("文件已移动到回收站: fileKey={}, trashPath={}", asset.getFileKey(), trashPath);
                }
            } catch (Exception e) {
                log.warn("移动文件到回收站失败: fileKey={}, error={}", asset.getFileKey(), e.getMessage());
            }
        }

        // 使用自定义 Mapper 方法执行软删除（完全绕过 @TableLogic）
        int rows = assetMapper.softDelete(assetId, LocalDateTime.now(), trashPath, asset.getVersion());

        if (rows == 0) {
            log.warn("素材删除失败（可能已被其他请求修改）: assetId={}", assetId);
            throw new BusinessException("素材已被修改，请刷新后重试");
        }

        log.info("素材删除成功: assetId={}", assetId);

        // 发布Canvas同步消息
        canvasMessagePublisher.publishEntityChange(
                ProjectConstants.EntityType.ASSET,
                asset.getId(),
                asset.getScriptId(),
                ProjectConstants.EntityType.SCRIPT,
                asset.getScriptId(),
                asset.getWorkspaceId(),
                ProjectConstants.ChangeType.DELETED,
                null
        );

        // 发布协作实体变更事件（WebSocket通知）
        if (asset.getScriptId() != null) {
            entityChangeEventPublisher.publishEntityDeleted(
                    CollabEntityChangeEvent.EntityType.ASSET,
                    asset.getId(),
                    asset.getScriptId()
            );
        }
    }

    @Override
    public AssetResponse getById(String assetId) {
        Asset asset = getAssetOrThrow(assetId);
        return enrichWithFileUrl(AssetResponse.fromEntity(asset));
    }

    @Override
    public Optional<Asset> findById(String assetId) {
        Asset asset = assetMapper.selectById(assetId);
        if (asset == null || asset.getDeleted() == CommonConstants.DELETED) {
            return Optional.empty();
        }
        return Optional.of(asset);
    }

    @Override
    public List<AssetResponse> listByWorkspace(String workspaceId) {
        List<Asset> assets = assetMapper.selectByWorkspaceId(workspaceId);
        List<AssetResponse> responses = assets.stream()
                .map(AssetResponse::fromEntity)
                .collect(Collectors.toList());
        return enrichWithFileUrls(responses);
    }

    @Override
    public List<AssetResponse> listByScript(String scriptId) {
        List<Asset> assets = assetMapper.selectByScriptId(scriptId);
        List<AssetResponse> responses = assets.stream()
                .map(AssetResponse::fromEntity)
                .collect(Collectors.toList());
        return enrichWithFileUrls(responses);
    }

    @Override
    public Page<AssetResponse> queryAssets(AssetQueryRequest request, String workspaceId) {
        int pageNum = request.getPage() != null ? request.getPage() : 1;
        int pageSize = request.getSize() != null ? request.getSize() : 20;
        Page<Asset> page = new Page<>(pageNum, pageSize);

        LambdaQueryWrapper<Asset> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Asset::getWorkspaceId, workspaceId)
                .eq(Asset::getDeleted, CommonConstants.NOT_DELETED);

        // 过滤条件
        if (StringUtils.hasText(request.getScriptId())) {
            wrapper.eq(Asset::getScriptId, request.getScriptId());
        }
        if (StringUtils.hasText(request.getAssetType())) {
            wrapper.eq(Asset::getAssetType, request.getAssetType());
        }
        if (StringUtils.hasText(request.getSource())) {
            wrapper.eq(Asset::getSource, request.getSource());
        }
        if (StringUtils.hasText(request.getGenerationStatus())) {
            wrapper.eq(Asset::getGenerationStatus, request.getGenerationStatus());
        }
        if (StringUtils.hasText(request.getScope())) {
            wrapper.eq(Asset::getScope, request.getScope());
        }
        if (StringUtils.hasText(request.getKeyword())) {
            String kw = "%" + request.getKeyword() + "%";
            wrapper.and(w -> w.like(Asset::getName, request.getKeyword())
                    .or()
                    .like(Asset::getDescription, request.getKeyword())
                    .or()
                    .apply("extra_info::text LIKE {0}", kw));
        }

        // 默认按更新时间倒序
        wrapper.orderByDesc(Asset::getUpdatedAt);

        Page<Asset> resultPage = assetMapper.selectPage(page, wrapper);

        // 转换为响应对象
        Page<AssetResponse> responsePage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        List<AssetResponse> responses = resultPage.getRecords().stream()
                .map(AssetResponse::fromEntity)
                .collect(Collectors.toList());
        responsePage.setRecords(enrichWithFileUrls(responses));

        return responsePage;
    }

    @Override
    public List<AssetResponse> listByType(String workspaceId, String assetType) {
        List<Asset> assets = assetMapper.selectByType(workspaceId, assetType);
        List<AssetResponse> responses = assets.stream()
                .map(AssetResponse::fromEntity)
                .collect(Collectors.toList());
        return enrichWithFileUrls(responses);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateGenerationStatus(String assetId, String status, String userId) {
        Asset asset = assetMapper.selectById(assetId);
        if (asset == null || asset.getDeleted() == CommonConstants.DELETED) {
            log.warn("更新素材生成状态：素材不存在或已删除: assetId={}", assetId);
            return;
        }

        asset.setGenerationStatus(status);
        assetMapper.updateById(asset);

        log.info("素材生成状态更新: assetId={}, status={}", assetId, status);

        // 发布协作实体变更事件（WebSocket通知）
        if (asset.getScriptId() != null) {
            entityChangeEventPublisher.publishEntityUpdated(
                    CollabEntityChangeEvent.EntityType.ASSET,
                    asset.getId(),
                    asset.getScriptId(),
                    null,
                    toEntityDataMap(asset)
            );
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateFileInfo(String assetId, String fileKey, String fileUrl, String thumbnailUrl,
                               Long fileSize, String mimeType, Map<String, Object> metaInfo) {
        Asset asset = assetMapper.selectById(assetId);
        if (asset == null || asset.getDeleted() == CommonConstants.DELETED) {
            log.warn("更新素材文件信息：素材不存在或已删除: assetId={}", assetId);
            return;
        }

        if (StringUtils.hasText(fileKey)) {
            asset.setFileKey(fileKey);
        }
        if (StringUtils.hasText(fileUrl)) {
            asset.setFileUrl(fileUrl);
        }
        if (StringUtils.hasText(thumbnailUrl)) {
            asset.setThumbnailUrl(thumbnailUrl);
        }
        if (fileSize != null) {
            asset.setFileSize(fileSize);
        }
        if (StringUtils.hasText(mimeType)) {
            asset.setMimeType(mimeType);
        }
        if (metaInfo != null && !metaInfo.isEmpty()) {
            Map<String, Object> existingMeta = asset.getMetaInfo() != null
                    ? new HashMap<>(asset.getMetaInfo()) : new HashMap<>();
            existingMeta.putAll(metaInfo);
            asset.setMetaInfo(existingMeta);
        }

        assetMapper.updateById(asset);

        log.info("素材文件信息更新: assetId={}, fileKey={}", assetId, fileKey);

        // 发布协作实体变更事件（WebSocket通知）
        if (asset.getScriptId() != null) {
            entityChangeEventPublisher.publishEntityUpdated(
                    CollabEntityChangeEvent.EntityType.ASSET,
                    asset.getId(),
                    asset.getScriptId(),
                    null,
                    toEntityDataMap(asset)
            );
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateFileInfo(String assetId, String fileKey, String fileUrl, String thumbnailUrl,
                               Long fileSize, String mimeType, Map<String, Object> metaInfo, Boolean generateThumbnail) {
        Asset asset = assetMapper.selectById(assetId);
        if (asset == null || asset.getDeleted() == CommonConstants.DELETED) {
            log.warn("更新素材文件信息：素材不存在或已删除: assetId={}", assetId);
            return;
        }

        if (StringUtils.hasText(fileKey)) {
            asset.setFileKey(fileKey);
        }
        if (StringUtils.hasText(fileUrl)) {
            asset.setFileUrl(fileUrl);
        }
        if (StringUtils.hasText(thumbnailUrl)) {
            asset.setThumbnailUrl(thumbnailUrl);
        }
        if (fileSize != null) {
            asset.setFileSize(fileSize);
        }
        if (StringUtils.hasText(mimeType)) {
            asset.setMimeType(mimeType);
        }
        if (metaInfo != null && !metaInfo.isEmpty()) {
            Map<String, Object> existingMeta = asset.getMetaInfo() != null
                    ? new HashMap<>(asset.getMetaInfo()) : new HashMap<>();
            existingMeta.putAll(metaInfo);
            asset.setMetaInfo(existingMeta);
        }

        // 生成缩略图（AI生成的图片没有缩略图，需要自动生成）
        if (Boolean.TRUE.equals(generateThumbnail) && !StringUtils.hasText(thumbnailUrl)
                && StringUtils.hasText(fileKey) && StringUtils.hasText(mimeType)
                && mimeType.startsWith("image/")) {
            try {
                String generatedThumbnailUrl = fileStorageService.generateThumbnail(fileKey, mimeType);
                if (StringUtils.hasText(generatedThumbnailUrl)) {
                    asset.setThumbnailUrl(generatedThumbnailUrl);
                    log.info("AI生成素材缩略图创建成功: assetId={}, thumbnailUrl={}", assetId, generatedThumbnailUrl);
                }
            } catch (Exception e) {
                log.warn("生成缩略图失败，继续保存素材: assetId={}, error={}", assetId, e.getMessage());
            }
        }

        assetMapper.updateById(asset);

        log.info("素材文件信息更新: assetId={}, fileKey={}, thumbnailUrl={}", assetId, fileKey, asset.getThumbnailUrl());

        // 发布协作实体变更事件（WebSocket通知）
        if (asset.getScriptId() != null) {
            entityChangeEventPublisher.publishEntityUpdated(
                    CollabEntityChangeEvent.EntityType.ASSET,
                    asset.getId(),
                    asset.getScriptId(),
                    null,
                    toEntityDataMap(asset)
            );
        }
    }

    @Override
    public void updateExtraInfo(String assetId, Map<String, Object> extraInfo) {
        Asset asset = assetMapper.selectById(assetId);
        if (asset == null || asset.getDeleted() == CommonConstants.DELETED) {
            log.warn("更新素材扩展信息：素材不存在或已删除: assetId={}", assetId);
            return;
        }

        if (extraInfo != null) {
            // 合并扩展信息
            Map<String, Object> existingExtra = asset.getExtraInfo() != null
                    ? new HashMap<>(asset.getExtraInfo()) : new HashMap<>();
            existingExtra.putAll(extraInfo);
            asset.setExtraInfo(existingExtra);
            assetMapper.updateById(asset);
            log.info("素材扩展信息更新: assetId={}", assetId);

            // 发布协作实体变更事件（WebSocket通知）
            if (asset.getScriptId() != null) {
                entityChangeEventPublisher.publishEntityUpdated(
                        CollabEntityChangeEvent.EntityType.ASSET,
                        asset.getId(),
                        asset.getScriptId(),
                        null,
                        toEntityDataMap(asset)
                );
            }
        }
    }

    @Override
    public Optional<Asset> findByTaskId(String taskId) {
        Asset asset = assetMapper.selectByTaskId(taskId);
        if (asset == null || asset.getDeleted() == CommonConstants.DELETED) {
            return Optional.empty();
        }
        return Optional.of(asset);
    }

    @Override
    public List<AssetResponse> batchGet(List<String> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<Asset> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Asset::getId, assetIds)
                .eq(Asset::getDeleted, CommonConstants.NOT_DELETED);
        List<Asset> assets = assetMapper.selectList(wrapper);

        List<AssetResponse> responses = assets.stream()
                .map(AssetResponse::fromEntity)
                .collect(Collectors.toList());
        return enrichWithFileUrls(responses);
    }

    @Override
    public Asset createForInspiration(String url, String thumbnailUrl, String assetType,
                                      String mimeType, Long fileSize,
                                      Map<String, Object> metaInfo, String workspaceId, String userId) {
        Asset asset = new Asset();
        asset.setId(UuidGenerator.generateUuidV7());
        asset.setWorkspaceId(workspaceId);
        asset.setScope(AssetScope.WORKSPACE);
        asset.setSource(AssetSource.AI_GENERATED);
        asset.setAssetType(assetType != null ? assetType : "IMAGE");
        asset.setFileUrl(url);
        asset.setThumbnailUrl(thumbnailUrl);
        asset.setMimeType(mimeType);
        asset.setFileSize(fileSize);
        asset.setMetaInfo(metaInfo);
        asset.setGenerationStatus("COMPLETED");
        asset.setVersionNumber(1);
        asset.setName("灵感生成-" + asset.getAssetType().toLowerCase());
        asset.setCreatedBy(userId);

        assetMapper.insert(asset);
        log.debug("灵感素材创建成功: assetId={}, workspaceId={}", asset.getId(), workspaceId);
        return asset;
    }

    // ==================== 游离素材管理 ====================

    @Override
    public Page<AssetResponse> listUnattachedByScript(String scriptId, String workspaceId, String assetType, int page, int size) {
        Page<Asset> pageParam = new Page<>(page, size);

        LambdaQueryWrapper<Asset> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Asset::getScope, AssetScope.SCRIPT)
                .eq(Asset::getScriptId, scriptId)
                .eq(Asset::getWorkspaceId, workspaceId)
                .eq(Asset::getDeleted, CommonConstants.NOT_DELETED)
                .notInSql(Asset::getId,
                        "SELECT asset_id FROM t_entity_asset_relation WHERE deleted = 0");
        if (StringUtils.hasText(assetType)) {
            wrapper.eq(Asset::getAssetType, assetType);
        }
        wrapper.orderByDesc(Asset::getCreatedAt);

        Page<Asset> resultPage = assetMapper.selectPage(pageParam, wrapper);

        Page<AssetResponse> responsePage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        List<AssetResponse> responses = resultPage.getRecords().stream()
                .map(AssetResponse::fromEntity)
                .collect(Collectors.toList());
        responsePage.setRecords(enrichWithFileUrls(responses));
        return responsePage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssetResponse copyAsset(String assetId, CopyAssetRequest request, String workspaceId, String userId) {
        Asset source = getAssetOrThrow(assetId);

        // 创建新 Asset 记录（共享同一 fileKey/fileUrl）
        Asset copy = new Asset();
        copy.setId(UuidGenerator.generateUuidV7());
        copy.setWorkspaceId(workspaceId);
        copy.setScope(AssetScope.SCRIPT);
        copy.setScriptId(StringUtils.hasText(request.getTargetScriptId()) ? request.getTargetScriptId() : source.getScriptId());
        copy.setName(source.getName());
        copy.setDescription(source.getDescription());
        copy.setAssetType(source.getAssetType());
        copy.setSource(source.getSource());
        copy.setFileKey(source.getFileKey());
        copy.setFileUrl(source.getFileUrl());
        copy.setThumbnailUrl(source.getThumbnailUrl());
        copy.setFileSize(source.getFileSize());
        copy.setMimeType(source.getMimeType());
        copy.setMetaInfo(source.getMetaInfo());
        copy.setExtraInfo(source.getExtraInfo());
        copy.setGenerationStatus(source.getGenerationStatus());
        copy.setCreatedBy(userId);
        copy.setVersionNumber(1);

        assetMapper.insert(copy);

        log.info("素材复制成功: sourceAssetId={}, newAssetId={}, targetScriptId={}",
                assetId, copy.getId(), copy.getScriptId());

        // 如果指定了目标实体，同时挂载
        if (StringUtils.hasText(request.getTargetEntityType()) && StringUtils.hasText(request.getTargetEntityId())) {
            CreateEntityAssetRelationRequest relationRequest = CreateEntityAssetRelationRequest.builder()
                    .entityType(request.getTargetEntityType())
                    .entityId(request.getTargetEntityId())
                    .assetId(copy.getId())
                    .relationType(request.getRelationType())
                    .build();
            entityAssetRelationService.createRelation(relationRequest, workspaceId, userId);
        }

        return enrichWithFileUrl(AssetResponse.fromEntity(copy));
    }

    // ==================== 上传相关 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssetUploadInitResponse initUpload(AssetUploadInitRequest request, String workspaceId, String userId) {
        // 验证文件类型
        if (!fileStorageService.isAllowedMimeType(request.getMimeType())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "不支持的文件类型: " + request.getMimeType());
        }

        // 验证文件大小
        if (!fileStorageService.isAllowedFileSize(request.getMimeType(), request.getFileSize())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "文件大小超出限制");
        }

        // 处理作用域：有 scriptId 时默认挂到剧本
        String scope = StringUtils.hasText(request.getScope()) ? request.getScope() : AssetScope.WORKSPACE;
        if (!StringUtils.hasText(request.getScope()) && StringUtils.hasText(request.getScriptId())) {
            scope = AssetScope.SCRIPT;
        }
        if (AssetScope.SCRIPT.equals(scope) && !StringUtils.hasText(request.getScriptId())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "剧本级素材必须指定剧本ID");
        }

        // 自动推断素材类型
        String assetType = StringUtils.hasText(request.getAssetType())
                ? request.getAssetType()
                : fileStorageService.getFileType(request.getMimeType());

        // 创建素材记录（状态为 PENDING）
        Asset asset = new Asset();
        asset.setId(UuidGenerator.generateUuidV7());
        asset.setWorkspaceId(workspaceId);
        asset.setScope(scope);
        asset.setScriptId(request.getScriptId());
        asset.setName(request.getName());
        asset.setDescription(request.getDescription());
        asset.setAssetType(assetType);
        asset.setSource(AssetSource.UPLOAD);
        asset.setFileSize(request.getFileSize());
        asset.setMimeType(request.getMimeType());
        asset.setExtraInfo(request.getExtraInfo());
        asset.setCreatedBy(userId);
        asset.setVersionNumber(1);
        asset.setGenerationStatus(AssetUploadInitResponse.UploadStatus.PENDING);

        assetMapper.insert(asset);

        // 获取预签名上传 URL
        PresignedUploadRequest presignedRequest = PresignedUploadRequest.builder()
                .workspaceId(workspaceId)
                .fileName(request.getFileName())
                .mimeType(request.getMimeType())
                .fileSize(request.getFileSize())
                .fileType(assetType)
                .build();

        PresignedUploadResult presignedResult = fileStorageService.getPresignedUploadUrl(presignedRequest);

        // 更新素材的 fileKey
        asset.setFileKey(presignedResult.getFileKey());
        assetMapper.updateById(asset);

        log.info("素材上传初始化成功: assetId={}, fileKey={}", asset.getId(), presignedResult.getFileKey());

        return AssetUploadInitResponse.builder()
                .assetId(asset.getId())
                .name(asset.getName())
                .assetType(asset.getAssetType())
                .uploadStatus(AssetUploadInitResponse.UploadStatus.PENDING)
                .uploadUrl(presignedResult.getUploadUrl())
                .method(presignedResult.getMethod())
                .headers(presignedResult.getHeaders())
                .fileKey(presignedResult.getFileKey())
                .expiresAt(presignedResult.getExpiresAt())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssetResponse confirmUpload(String assetId, AssetUploadConfirmRequest request, String userId) {
        Asset asset = getAssetOrThrow(assetId);

        // 验证 fileKey 匹配
        if (!request.getFileKey().equals(asset.getFileKey())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "fileKey 不匹配");
        }

        // 验证文件已上传到 OSS
        if (!fileStorageService.confirmUpload(request.getFileKey())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "文件上传确认失败，文件不存在");
        }

        // 获取文件 URL
        String fileUrl = fileStorageService.getFileUrl(request.getFileKey());
        asset.setFileUrl(fileUrl);

        // 异步生成缩略图（通过消息队列，缩略图URL将在消费者处理后更新）
        fileStorageService.generateThumbnailAsync(
                request.getFileKey(),
                asset.getMimeType(),
                asset.getWorkspaceId(),
                asset.getId(),
                "ASSET"
        );

        // 更新文件大小（如果提供了实际大小）
        if (request.getActualFileSize() != null) {
            asset.setFileSize(request.getActualFileSize());
        }

        // 更新元数据
        if (request.getMetaInfo() != null && !request.getMetaInfo().isEmpty()) {
            Map<String, Object> existingMeta = asset.getMetaInfo() != null
                    ? new HashMap<>(asset.getMetaInfo()) : new HashMap<>();
            existingMeta.putAll(request.getMetaInfo());
            asset.setMetaInfo(existingMeta);
        }

        // 更新状态为已完成
        asset.setGenerationStatus(AssetUploadInitResponse.UploadStatus.COMPLETED);

        assetMapper.updateById(asset);

        log.info("素材上传确认成功: assetId={}, fileUrl={}", assetId, fileUrl);

        // 发布 Canvas 同步消息
        if (asset.getScriptId() != null) {
            canvasMessagePublisher.publishEntityChange(
                    ProjectConstants.EntityType.ASSET,
                    asset.getId(),
                    asset.getScriptId(),
                    ProjectConstants.EntityType.SCRIPT,
                    asset.getScriptId(),
                    asset.getWorkspaceId(),
                    ProjectConstants.ChangeType.CREATED,
                    null
            );

            // 发布协作实体变更事件（WebSocket通知）
            entityChangeEventPublisher.publishEntityCreated(
                    CollabEntityChangeEvent.EntityType.ASSET,
                    asset.getId(),
                    asset.getScriptId(),
                    toEntityDataMap(asset)
            );
        }

        return enrichWithFileUrl(AssetResponse.fromEntity(asset));
    }

    /**
     * 获取素材或抛出异常
     */
    private Asset getAssetOrThrow(String assetId) {
        Asset asset = assetMapper.selectById(assetId);
        if (asset == null || asset.getDeleted() == CommonConstants.DELETED) {
            throw new BusinessException(ResultCode.ASSET_NOT_FOUND);
        }
        return asset;
    }

    // ==================== 预签名 URL 相关 ====================

    /**
     * 将素材实体转换为协作事件数据Map
     */
    private Map<String, Object> toEntityDataMap(Asset asset) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", asset.getName());
        data.put("description", asset.getDescription());
        data.put("assetType", asset.getAssetType());
        data.put("fileUrl", asset.getFileUrl());
        data.put("thumbnailUrl", asset.getThumbnailUrl());
        data.put("generationStatus", asset.getGenerationStatus());
        data.put("versionNumber", asset.getVersionNumber());
        return data;
    }

    /**
     * 为素材响应添加文件 URL 和用户信息
     * 使用公开 URL（如果配置了公开访问域名）而非预签名 URL
     *
     * @param response 素材响应
     * @return 添加 URL 和用户信息后的响应
     */
    private AssetResponse enrichWithFileUrl(AssetResponse response) {
        if (response == null) {
            return null;
        }

        // 生成文件下载 URL（使用公开 URL）
        if (StringUtils.hasText(response.getFileKey())) {
            String fileUrl = fileStorageService.getFileUrl(response.getFileKey());
            response.setFileUrl(fileUrl);
        }

        // 生成缩略图下载 URL
        if (StringUtils.hasText(response.getThumbnailUrl())) {
            String thumbnailKey = fileStorageService.generateThumbnailKey(response.getFileKey());
            if (fileStorageService.exists(thumbnailKey)) {
                String thumbnailUrl = fileStorageService.getFileUrl(thumbnailKey);
                response.setThumbnailUrl(thumbnailUrl);
            }
        }

        // 填充创建者用户信息
        if (StringUtils.hasText(response.getCreatedBy())) {
            UserBasicInfo userInfo = userInfoHelper.getUserInfo(response.getCreatedBy());
            if (userInfo != null) {
                response.setCreatedByNickname(userInfo.getNickname());
                response.setCreatedByUsername(userInfo.getUsername());
            }
        }

        return response;
    }

    /**
     * 批量为素材响应列表添加文件 URL 和用户信息
     *
     * @param responses 素材响应列表
     * @return 添加 URL 和用户信息后的响应列表
     */
    private List<AssetResponse> enrichWithFileUrls(List<AssetResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return responses;
        }

        // 批量获取用户信息
        Set<String> userIds = responses.stream()
                .map(AssetResponse::getCreatedBy)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        Map<String, UserBasicInfo> userInfoMap = userInfoHelper.batchGetUserInfo(userIds);

        // 为每个响应填充信息
        for (AssetResponse response : responses) {
            // 生成文件下载 URL（使用公开 URL）
            if (StringUtils.hasText(response.getFileKey())) {
                String fileUrl = fileStorageService.getFileUrl(response.getFileKey());
                response.setFileUrl(fileUrl);
            }

            // 生成缩略图下载 URL
            if (StringUtils.hasText(response.getThumbnailUrl())) {
                String thumbnailKey = fileStorageService.generateThumbnailKey(response.getFileKey());
                if (fileStorageService.exists(thumbnailKey)) {
                    String thumbnailUrl = fileStorageService.getFileUrl(thumbnailKey);
                    response.setThumbnailUrl(thumbnailUrl);
                }
            }

            // 填充创建者用户信息
            if (StringUtils.hasText(response.getCreatedBy())) {
                UserBasicInfo userInfo = userInfoMap.get(response.getCreatedBy());
                if (userInfo != null) {
                    response.setCreatedByNickname(userInfo.getNickname());
                    response.setCreatedByUsername(userInfo.getUsername());
                }
            }
        }

        return responses;
    }

    // ==================== 回收站相关 ====================

    @Override
    public Page<AssetResponse> listTrash(String workspaceId, int page, int size) {
        // 使用自定义 Mapper 方法绕过 @TableLogic
        // 因为 @TableLogic 会自动添加 deleted = 0 条件，与我们要查询 deleted = 1 冲突
        List<Asset> allTrashAssets = assetMapper.selectTrashByWorkspaceId(workspaceId);
        int total = allTrashAssets.size();

        // 手动分页
        int fromIndex = (page - 1) * size;
        int toIndex = Math.min(fromIndex + size, total);

        List<Asset> pagedAssets;
        if (fromIndex >= total) {
            pagedAssets = Collections.emptyList();
        } else {
            pagedAssets = allTrashAssets.subList(fromIndex, toIndex);
        }

        Page<AssetResponse> responsePage = new Page<>(page, size, total);
        List<AssetResponse> responses = pagedAssets.stream()
                .map(AssetResponse::fromEntity)
                .collect(Collectors.toList());

        // 填充文件URL和用户信息
        responsePage.setRecords(enrichWithFileUrls(responses));

        return responsePage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssetResponse restoreFromTrash(String assetId, String userId) {
        // 查询已删除的素材（绕过 @TableLogic）
        Asset asset = assetMapper.selectDeletedById(assetId);
        if (asset == null) {
            throw new BusinessException(ResultCode.ASSET_NOT_FOUND, "回收站中未找到该素材");
        }

        // 从回收站恢复文件到原路径
        if (StringUtils.hasText(asset.getTrashPath())) {
            try {
                String restoredPath = fileStorageService.restoreFromTrash(asset.getTrashPath(), asset.getWorkspaceId());
                if (restoredPath != null) {
                    // 恢复成功，文件已移回原路径（fileKey 保持不变）
                    log.info("文件已从回收站恢复: trashPath={}, restoredPath={}", asset.getTrashPath(), restoredPath);
                } else {
                    log.warn("从回收站恢复文件失败，文件可能不存在: trashPath={}", asset.getTrashPath());
                }
            } catch (Exception e) {
                log.warn("从回收站恢复文件失败: trashPath={}, error={}", asset.getTrashPath(), e.getMessage());
                // 即使文件恢复失败，也允许恢复素材记录（文件可能已被手动恢复或不需要）
            }
        }

        // 使用自定义 Mapper 方法恢复素材（完全绕过 @TableLogic）
        int rows = assetMapper.restoreFromTrash(assetId, asset.getVersion());

        if (rows == 0) {
            log.warn("素材恢复失败（可能已被其他请求修改）: assetId={}", assetId);
            throw new BusinessException("素材恢复失败，请刷新后重试");
        }

        log.info("素材已从回收站恢复: assetId={}", assetId);

        // 更新内存中的 asset 对象以返回正确的响应
        asset.setDeleted(CommonConstants.NOT_DELETED);
        asset.setDeletedAt(null);
        asset.setTrashPath(null);

        // 发布Canvas同步消息（恢复创建）
        if (asset.getScriptId() != null) {
            canvasMessagePublisher.publishEntityChange(
                    ProjectConstants.EntityType.ASSET,
                    asset.getId(),
                    asset.getScriptId(),
                    ProjectConstants.EntityType.SCRIPT,
                    asset.getScriptId(),
                    asset.getWorkspaceId(),
                    ProjectConstants.ChangeType.CREATED,
                    null
            );

            // 发布协作实体变更事件（WebSocket通知）
            entityChangeEventPublisher.publishEntityCreated(
                    CollabEntityChangeEvent.EntityType.ASSET,
                    asset.getId(),
                    asset.getScriptId(),
                    toEntityDataMap(asset)
            );
        }

        return enrichWithFileUrl(AssetResponse.fromEntity(asset));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void permanentDelete(String assetId, String userId) {
        // 查询已删除的素材（绕过 @TableLogic）
        Asset asset = assetMapper.selectDeletedById(assetId);
        if (asset == null) {
            throw new BusinessException(ResultCode.ASSET_NOT_FOUND, "回收站中未找到该素材");
        }

        int liveRelations = entityAssetRelationMapper.selectByAssetId(assetId).size();
        if (liveRelations > 0) {
            throw new BusinessException(ResultCode.ASSET_HAS_REFERENCES,
                    "素材仍被 " + liveRelations + " 个实体引用，无法永久删除");
        }

        // 仅在没有其他记录共享同一存储对象时才删除文件
        if (StringUtils.hasText(asset.getTrashPath())
                && assetMapper.countOtherByTrashPath(asset.getTrashPath(), assetId) == 0) {
            try {
                fileStorageService.deleteFile(asset.getTrashPath());
                log.info("回收站文件已永久删除: trashPath={}", asset.getTrashPath());
            } catch (Exception e) {
                log.warn("删除回收站文件失败: trashPath={}, error={}", asset.getTrashPath(), e.getMessage());
            }
        }

        if (StringUtils.hasText(asset.getFileKey())
                && assetMapper.countOtherByFileKey(asset.getFileKey(), assetId) == 0) {
            try {
                String thumbnailKey = fileStorageService.generateThumbnailKey(asset.getFileKey());
                String trashThumbnailKey = asset.getTrashPath() != null
                        ? fileStorageService.generateThumbnailKey(asset.getTrashPath())
                        : thumbnailKey;
                fileStorageService.deleteFile(trashThumbnailKey);
            } catch (Exception e) {
                log.warn("删除缩略图失败: error={}", e.getMessage());
            }
        }

        // 物理删除素材记录
        assetMapper.hardDeleteById(assetId);

        log.info("素材已永久删除: assetId={}", assetId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int emptyTrash(String workspaceId, String userId) {
        // 使用自定义 Mapper 方法绕过 @TableLogic 查询回收站中所有素材
        List<Asset> trashAssets = assetMapper.selectTrashByWorkspaceId(workspaceId);
        if (trashAssets.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (Asset asset : trashAssets) {
            try {
                permanentDelete(asset.getId(), userId);
                count++;
            } catch (Exception e) {
                log.warn("清空回收站时删除素材失败: assetId={}, error={}", asset.getId(), e.getMessage());
            }
        }

        log.info("回收站已清空: workspaceId={}, deletedCount={}", workspaceId, count);
        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cleanupExpiredTrash(int retentionDays) {
        LocalDateTime expireTime = LocalDateTime.now().minusDays(retentionDays);

        // 使用自定义 Mapper 方法绕过 @TableLogic 查询过期的回收站素材
        List<Asset> expiredAssets = assetMapper.selectExpiredTrash(expireTime);
        if (expiredAssets.isEmpty()) {
            return 0;
        }

        int count = 0;
        int skipped = 0;
        for (Asset asset : expiredAssets) {
            try {
                int liveRelations = entityAssetRelationMapper.selectByAssetId(asset.getId()).size();
                if (liveRelations > 0) {
                    log.warn("跳过过期素材物理删除：仍存在 {} 个活跃引用: assetId={}", liveRelations, asset.getId());
                    skipped++;
                    continue;
                }

                // 仅在没有其他记录共享同一存储对象时才删除文件，避免误删被复用/版本化的存储
                if (StringUtils.hasText(asset.getTrashPath())
                        && assetMapper.countOtherByTrashPath(asset.getTrashPath(), asset.getId()) == 0) {
                    fileStorageService.deleteFile(asset.getTrashPath());
                }
                if (StringUtils.hasText(asset.getFileKey())
                        && assetMapper.countOtherByFileKey(asset.getFileKey(), asset.getId()) == 0) {
                    try {
                        String thumbnailKey = fileStorageService.generateThumbnailKey(asset.getFileKey());
                        fileStorageService.deleteFile(thumbnailKey);
                    } catch (Exception thumbEx) {
                        log.warn("删除缩略图失败: assetId={}, error={}", asset.getId(), thumbEx.getMessage());
                    }
                }

                assetMapper.hardDeleteById(asset.getId());
                count++;
            } catch (Exception e) {
                log.warn("清理过期素材失败: assetId={}, error={}", asset.getId(), e.getMessage());
            }
        }

        log.info("清理过期回收站素材完成: retentionDays={}, cleanedCount={}, skipped={}",
                retentionDays, count, skipped);
        return count;
    }
}
