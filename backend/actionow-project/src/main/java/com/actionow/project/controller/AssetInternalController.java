package com.actionow.project.controller;

import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.IgnoreAuth;
import com.actionow.project.dto.EntityInfoResponse;
import com.actionow.project.dto.asset.AssetResponse;
import com.actionow.project.dto.asset.CreateAssetRequest;
import com.actionow.project.dto.relation.CreateEntityAssetRelationRequest;
import com.actionow.project.dto.relation.EntityAssetRelationResponse;
import com.actionow.project.entity.Asset;
import com.actionow.project.scheduler.AssetTrashCleanupScheduler;
import com.actionow.project.security.InternalRateLimit;
import com.actionow.project.service.AssetService;
import com.actionow.project.service.EntityAssetRelationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 素材内部接口控制器
 * 供其他微服务通过 Feign 调用
 *
 * @author Actionow
 */
@Slf4j
@RestController
@RequestMapping("/internal/assets")
@RequiredArgsConstructor
@IgnoreAuth
@InternalRateLimit(permits = 200, intervalSeconds = 1, keyBy = InternalRateLimit.KeyBy.WORKSPACE)
public class AssetInternalController {

    private final AssetService assetService;
    private final EntityAssetRelationService entityAssetRelationService;
    private final AssetTrashCleanupScheduler trashCleanupScheduler;

    // ==================== 创建接口 ====================

    /**
     * 创建素材
     * 供 Task/Agent 服务调用，实现自动创建 Asset
     */
    @PostMapping
    public Result<Map<String, Object>> createAsset(
            @RequestHeader("X-Workspace-Id") String workspaceId,
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, Object> request) {

        CreateAssetRequest createRequest = mapToCreateAssetRequest(request);
        AssetResponse response = assetService.create(createRequest, workspaceId, userId);

        // 返回简化的 Map 格式
        Map<String, Object> result = new HashMap<>();
        result.put("id", response.getId());
        result.put("name", response.getName());
        result.put("assetType", response.getAssetType());
        result.put("source", response.getSource());
        result.put("scope", response.getScope());
        result.put("generationStatus", response.getGenerationStatus());
        return Result.success(result);
    }

    /**
     * 创建实体素材关联
     * 供 Task/Agent 服务调用
     */
    @PostMapping("/relations")
    public Result<Map<String, Object>> createEntityAssetRelation(
            @RequestHeader("X-Workspace-Id") String workspaceId,
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, Object> request) {

        CreateEntityAssetRelationRequest createRequest = CreateEntityAssetRelationRequest.builder()
                .entityType((String) request.get("entityType"))
                .entityId((String) request.get("entityId"))
                .assetId((String) request.get("assetId"))
                .relationType((String) request.get("relationType"))
                .description((String) request.get("description"))
                .build();

        EntityAssetRelationResponse response = entityAssetRelationService.createRelation(
                createRequest, workspaceId, userId);

        // 返回简化的 Map 格式
        Map<String, Object> result = new HashMap<>();
        result.put("id", response.getId());
        result.put("entityType", response.getEntityType());
        result.put("entityId", response.getEntityId());
        result.put("assetId", request.get("assetId")); // 从请求中获取
        result.put("relationType", response.getRelationType());
        return Result.success(result);
    }

    // ==================== 查询接口 ====================

    /**
     * 根据ID获取素材
     */
    @GetMapping("/{assetId}")
    public Result<AssetResponse> getById(@PathVariable String assetId) {
        return assetService.findById(assetId)
                .map(asset -> Result.success(AssetResponse.fromEntity(asset)))
                .orElseGet(() -> Result.success(null));
    }

    /**
     * 获取素材详情（带工作空间）
     * 返回完整素材信息包括 extraInfo
     */
    @GetMapping("/{assetId}/detail")
    public Result<Map<String, Object>> getAssetDetail(
            @RequestHeader("X-Workspace-Id") String workspaceId,
            @PathVariable String assetId) {
        return assetService.findById(assetId)
                .map(asset -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("id", asset.getId());
                    result.put("name", asset.getName());
                    result.put("description", asset.getDescription());
                    result.put("assetType", asset.getAssetType());
                    result.put("source", asset.getSource());
                    result.put("scope", asset.getScope());
                    result.put("generationStatus", asset.getGenerationStatus());
                    result.put("fileUrl", asset.getFileUrl());
                    result.put("thumbnailUrl", asset.getThumbnailUrl());
                    result.put("fileSize", asset.getFileSize());
                    result.put("mimeType", asset.getMimeType());
                    result.put("taskId", asset.getTaskId());
                    result.put("extraInfo", asset.getExtraInfo());
                    result.put("createdAt", asset.getCreatedAt());
                    result.put("updatedAt", asset.getUpdatedAt());
                    return Result.success(result);
                })
                .orElseGet(() -> Result.success(null));
    }

    /**
     * 批量获取素材
     */
    @PostMapping("/batch")
    public Result<List<AssetResponse>> batchGet(@RequestBody List<String> assetIds) {
        List<AssetResponse> assets = assetService.batchGet(assetIds);
        return Result.success(assets);
    }

    /**
     * 批量获取素材信息（Canvas 服务使用）
     * 返回 EntityInfoResponse 格式，与 Canvas 的 EntityInfo 兼容
     */
    @PostMapping("/batch-get")
    public Result<List<EntityInfoResponse>> batchGetForCanvas(@RequestBody List<String> assetIds) {
        List<AssetResponse> assets = assetService.batchGet(assetIds);
        List<EntityInfoResponse> result = assets.stream()
                .map(asset -> EntityInfoResponse.builder()
                        .id(asset.getId())
                        .entityType("ASSET")
                        .name(asset.getName())
                        .description(asset.getDescription())
                        .coverUrl(asset.getThumbnailUrl() != null ? asset.getThumbnailUrl() : asset.getFileUrl())
                        .version(asset.getVersionNumber())
                        .status(asset.getGenerationStatus())
                        .updatedAt(asset.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
        return Result.success(result);
    }

    /**
     * 检查素材是否存在
     */
    @GetMapping("/{assetId}/exists")
    public Result<Boolean> existsById(@PathVariable String assetId) {
        return Result.success(assetService.findById(assetId).isPresent());
    }

    /**
     * 根据任务ID查询素材
     */
    @GetMapping("/by-task/{taskId}")
    public Result<AssetResponse> getByTaskId(@PathVariable String taskId) {
        return assetService.findByTaskId(taskId)
                .map(asset -> Result.success(AssetResponse.fromEntity(asset)))
                .orElseGet(() -> Result.success(null));
    }

    // ==================== 更新接口 ====================

    /**
     * 更新生成状态
     * 供 actionow-task 回调
     */
    @PutMapping("/{assetId}/generation-status")
    public Result<Void> updateGenerationStatus(
            @PathVariable String assetId,
            @RequestParam String status) {
        assetService.updateGenerationStatus(assetId, status, null);
        return Result.success();
    }

    /**
     * 更新文件信息
     * 供 actionow-task 回调（AI生成完成后）
     */
    @PutMapping("/{assetId}/file-info")
    public Result<Void> updateFileInfo(
            @PathVariable String assetId,
            @RequestBody Map<String, Object> fileInfo) {
        String fileKey = (String) fileInfo.get("fileKey");
        String fileUrl = (String) fileInfo.get("fileUrl");
        String thumbnailUrl = (String) fileInfo.get("thumbnailUrl");
        Long fileSize = fileInfo.get("fileSize") != null ? ((Number) fileInfo.get("fileSize")).longValue() : null;
        String mimeType = (String) fileInfo.get("mimeType");
        Boolean generateThumbnail = (Boolean) fileInfo.get("generateThumbnail");
        @SuppressWarnings("unchecked")
        Map<String, Object> metaInfo = (Map<String, Object>) fileInfo.get("metaInfo");

        assetService.updateFileInfo(assetId, fileKey, fileUrl, thumbnailUrl, fileSize, mimeType, metaInfo, generateThumbnail);
        return Result.success();
    }

    /**
     * 更新素材扩展信息
     * 供 Task/Agent 服务存储生成参数（用于重试）
     */
    @PutMapping("/{assetId}/extra-info")
    public Result<Void> updateAssetExtraInfo(
            @RequestHeader("X-Workspace-Id") String workspaceId,
            @PathVariable String assetId,
            @RequestBody Map<String, Object> extraInfo) {
        assetService.updateExtraInfo(assetId, extraInfo);
        return Result.success();
    }

    // ==================== 私有方法 ====================

    @SuppressWarnings("unchecked")
    private CreateAssetRequest mapToCreateAssetRequest(Map<String, Object> request) {
        return CreateAssetRequest.builder()
                .name((String) request.get("name"))
                .description((String) request.get("description"))
                .assetType((String) request.get("assetType"))
                .source((String) request.get("source"))
                .scope((String) request.get("scope"))
                .scriptId((String) request.get("scriptId"))
                .fileName((String) request.get("fileName"))
                .fileSize(request.get("fileSize") != null ? ((Number) request.get("fileSize")).longValue() : null)
                .mimeType((String) request.get("mimeType"))
                .extraInfo((Map<String, Object>) request.get("extraInfo"))
                .generationStatus((String) request.get("generationStatus"))
                .build();
    }

    // ==================== 回收站维护接口 ====================

    /**
     * 手动触发回收站清理
     * 使用配置的默认保留天数
     *
     * @return 清理的素材数量
     */
    @PostMapping("/trash/cleanup")
    @InternalRateLimit(permits = 2, intervalSeconds = 60, keyBy = InternalRateLimit.KeyBy.GLOBAL, name = "trash-cleanup")
    public Result<Map<String, Object>> cleanupTrash() {
        log.info("收到回收站清理请求");
        int cleanedCount = trashCleanupScheduler.triggerCleanup();
        return Result.success(Map.of(
                "cleanedCount", cleanedCount,
                "success", true
        ));
    }

    /**
     * 手动触发回收站清理（指定保留天数）
     *
     * @param retentionDays 保留天数
     * @return 清理的素材数量
     */
    @PostMapping("/trash/cleanup/{retentionDays}")
    @InternalRateLimit(permits = 2, intervalSeconds = 60, keyBy = InternalRateLimit.KeyBy.GLOBAL, name = "trash-cleanup-days")
    public Result<Map<String, Object>> cleanupTrashWithDays(@PathVariable int retentionDays) {
        log.info("收到回收站清理请求，保留天数: {}", retentionDays);
        int cleanedCount = trashCleanupScheduler.triggerCleanup(retentionDays);
        return Result.success(Map.of(
                "cleanedCount", cleanedCount,
                "retentionDays", retentionDays,
                "success", true
        ));
    }
}
