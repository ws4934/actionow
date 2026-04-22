package com.actionow.task.service;

import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.result.Result;
import com.actionow.common.core.result.ResultCode;
import com.actionow.task.constant.TaskConstants;
import com.actionow.task.dto.*;
import com.actionow.task.entity.Task;
import com.actionow.task.feign.AssetFeignClient;
import com.actionow.task.mapper.TaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 素材生命周期服务
 * 从 AiGenerationOrchestrator 抽取，负责：
 * - 素材状态管理（GENERATING → COMPLETED / FAILED）
 * - 文件信息更新
 * - 实体上下文切换
 * - 实体生成辅助方法（创建 Asset、关联、参数构建）
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetLifecycleService {

    private static final Set<String> VALID_ENTITY_TYPES = Set.of(
            "SCRIPT", "EPISODE", "STORYBOARD", "CHARACTER", "SCENE", "PROP", "STYLE", "ASSET");
    private static final Set<String> VALID_GEN_TYPES = Set.of("IMAGE", "VIDEO", "AUDIO");

    private final AssetFeignClient assetFeignClient;
    private final TaskMapper taskMapper;

    // ==================== 素材状态管理 ====================

    /**
     * 更新素材状态为 GENERATING
     */
    public void updateAssetStatusGenerating(String assetId, String taskId, String providerId) {
        assetFeignClient.updateGenerationStatus(assetId, "GENERATING");
        Map<String, Object> fileInfo = new HashMap<>();
        fileInfo.put("taskId", taskId);
        fileInfo.put("providerId", providerId);
        assetFeignClient.updateFileInfo(assetId, fileInfo);
    }

    /**
     * 处理素材执行成功：更新 COMPLETED + 文件信息
     */
    public void handleSuccessfulAssetUpdate(Task task, ProviderExecutionResult result) {
        if (!"ASSET".equals(task.getEntityType())) {
            return;
        }
        try {
            assetFeignClient.updateGenerationStatus(task.getEntityId(), "COMPLETED");
            Map<String, Object> fileInfo = new HashMap<>();
            fileInfo.put("taskId", task.getId());
            fileInfo.put("fileKey", result.getFileKey());
            fileInfo.put("fileUrl", result.getFileUrl());
            fileInfo.put("thumbnailUrl", result.getThumbnailUrl());
            fileInfo.put("mimeType", result.getMimeType());
            fileInfo.put("fileSize", result.getFileSize());
            fileInfo.put("generateThumbnail", true);
            if (result.getMetaInfo() != null) {
                fileInfo.put("metaInfo", result.getMetaInfo());
            }
            assetFeignClient.updateFileInfo(task.getEntityId(), fileInfo);
        } catch (Exception e) {
            log.error("更新素材状态失败: assetId={}", task.getEntityId(), e);
        }
    }

    /**
     * 处理素材执行失败：更新 FAILED + 错误信息
     */
    public void handleFailedAssetUpdate(Task task, String errorMessage) {
        if (!"ASSET".equals(task.getEntityType())) {
            return;
        }
        try {
            assetFeignClient.updateGenerationStatus(task.getEntityId(), "FAILED");
            Map<String, Object> fileInfo = new HashMap<>();
            fileInfo.put("taskId", task.getId());
            fileInfo.put("errorMessage", errorMessage != null ? errorMessage : "");
            assetFeignClient.updateFileInfo(task.getEntityId(), fileInfo);
        } catch (Exception e) {
            log.error("更新素材状态失败: assetId={}", task.getEntityId(), e);
        }
    }

    /**
     * 将 input_params 中保存的源实体上下文（CHARACTER/SCENE 等）应用到任务记录。
     * 在完成/失败/取消时调用，与状态更新合并为一次 updateById，避免独立 UPDATE 引发乐观锁竞争。
     */
    public void applySourceEntityContext(Task task) {
        Map<String, Object> params = task.getInputParams();
        if (params == null) return;
        String sourceEntityType = (String) params.get("sourceEntityType");
        String sourceEntityId = (String) params.get("sourceEntityId");
        if (StringUtils.hasText(sourceEntityType) && StringUtils.hasText(sourceEntityId)) {
            task.setEntityType(sourceEntityType);
            task.setEntityId(sourceEntityId);
            String sourceEntityName = (String) params.get("sourceEntityName");
            if (StringUtils.hasText(sourceEntityName)) {
                task.setEntityName(sourceEntityName);
            }
        }
    }

    /**
     * 检查素材是否已在生成中
     */
    public void checkAssetNotGenerating(String assetId) {
        List<Task> existingTasks = taskMapper.selectByEntity(assetId, "ASSET");
        boolean hasRunningTask = existingTasks.stream()
                .anyMatch(t -> TaskConstants.TaskStatus.PENDING.equals(t.getStatus())
                        || TaskConstants.TaskStatus.RUNNING.equals(t.getStatus()));

        if (hasRunningTask) {
            throw new BusinessException(ResultCode.CONCURRENT_OPERATION, "该素材已有生成任务在执行中");
        }
    }

    // ==================== 实体生成辅助方法 ====================

    /**
     * 验证实体生成请求
     */
    public void validateEntityRequest(EntityGenerationRequest request) {
        if (!StringUtils.hasText(request.getEntityType())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "entityType 不能为空");
        }
        if (!StringUtils.hasText(request.getEntityId())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "entityId 不能为空");
        }
        if (!StringUtils.hasText(request.getGenerationType())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "generationType 不能为空");
        }
        if (request.getParams() == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "params 不能为空");
        }

        String entityType = request.getEntityType().toUpperCase();
        if (!VALID_ENTITY_TYPES.contains(entityType)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "不支持的 entityType: " + request.getEntityType());
        }

        String generationType = request.getGenerationType().toUpperCase();
        if (!VALID_GEN_TYPES.contains(generationType)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "不支持的 generationType: " + request.getGenerationType());
        }
    }

    /**
     * 为实体创建 Asset
     */
    public Result<Map<String, Object>> createAssetForEntity(EntityGenerationRequest request,
                                                             String workspaceId, String userId) {
        Map<String, Object> assetRequest = new HashMap<>();
        assetRequest.put("name", buildEntityAssetName(request));
        Object prompt = request.getParams() != null ? request.getParams().get("prompt") : null;
        String description = prompt != null ? "AI生成素材 - " + prompt : "AI生成素材";
        assetRequest.put("description", description);
        assetRequest.put("assetType", request.getGenerationType().toUpperCase());
        assetRequest.put("source", "AI_GENERATED");
        assetRequest.put("generationStatus", "GENERATING");

        if (StringUtils.hasText(request.getScriptId())) {
            assetRequest.put("scope", "SCRIPT");
            assetRequest.put("scriptId", request.getScriptId());
        } else {
            assetRequest.put("scope", "WORKSPACE");
        }

        return assetFeignClient.createAsset(workspaceId, userId, assetRequest);
    }

    /**
     * 构建素材名称
     */
    public String buildEntityAssetName(EntityGenerationRequest request) {
        if (StringUtils.hasText(request.getAssetName())) {
            return request.getAssetName();
        }
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String type = request.getGenerationType().toLowerCase();
        String entity = request.getEntityType().toLowerCase();
        return String.format("ai_%s_%s_%s", type, entity, timestamp);
    }

    /**
     * 创建实体素材关联
     */
    public Result<Map<String, Object>> createRelationForEntity(String entityType, String entityId,
                                                                String assetId, String relationType,
                                                                String workspaceId, String userId) {
        Map<String, Object> relationRequest = new HashMap<>();
        relationRequest.put("entityType", entityType.toUpperCase());
        relationRequest.put("entityId", entityId);
        relationRequest.put("assetId", assetId);
        relationRequest.put("relationType", relationType != null ? relationType.toUpperCase() : "DRAFT");
        return assetFeignClient.createEntityAssetRelation(workspaceId, userId, relationRequest);
    }

    /**
     * 构建实体生成参数
     */
    public Map<String, Object> buildEntityGenerationParams(EntityGenerationRequest request,
                                                            String workspaceId, String userId) {
        Map<String, Object> result = new HashMap<>();
        result.put("entityType", request.getEntityType().toUpperCase());
        result.put("entityId", request.getEntityId());
        result.put("generationType", request.getGenerationType().toUpperCase());
        result.put("workspaceId", workspaceId);
        result.put("userId", userId);
        result.put("createdAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        if (StringUtils.hasText(request.getProviderId())) {
            result.put("providerId", request.getProviderId());
        }
        if (request.getParams() != null && !request.getParams().isEmpty()) {
            result.put("params", request.getParams());
        }
        if (request.getReferenceAssetIds() != null && !request.getReferenceAssetIds().isEmpty()) {
            result.put("referenceAssetIds", request.getReferenceAssetIds());
        }
        if (request.getPriority() != null) {
            result.put("priority", request.getPriority());
        }
        if (StringUtils.hasText(request.getScriptId())) {
            result.put("scriptId", request.getScriptId());
        }
        if (StringUtils.hasText(request.getRelationType())) {
            result.put("relationType", request.getRelationType().toUpperCase());
        }

        return result;
    }

    /**
     * 更新 Asset 的 extraInfo
     */
    public Result<Void> updateAssetExtraInfoForEntity(String assetId, Map<String, Object> generationParams,
                                                       String workspaceId) {
        Map<String, Object> extraInfo = new HashMap<>();
        extraInfo.put("generationParams", generationParams);
        extraInfo.put("retryCount", 0);
        return assetFeignClient.updateAssetExtraInfo(workspaceId, assetId, extraInfo);
    }

    /**
     * 标记素材为失败状态
     */
    public void markAssetFailedStatus(String assetId, String errorMessage) {
        try {
            assetFeignClient.updateGenerationStatus(assetId, "FAILED");
            Map<String, Object> fileInfo = new HashMap<>();
            fileInfo.put("errorMessage", errorMessage);
            fileInfo.put("failedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            assetFeignClient.updateFileInfo(assetId, fileInfo);
            log.info("素材已标记为失败: assetId={}, errorMessage={}", assetId, errorMessage);
        } catch (Exception e) {
            log.error("标记素材失败状态异常: assetId={}", assetId, e);
        }
    }

    /**
     * 合并重试参数
     */
    @SuppressWarnings("unchecked")
    public EntityGenerationRequest mergeRetryParameters(RetryGenerationRequest retryRequest,
                                                         Map<String, Object> originalParams) {
        EntityGenerationRequest request = new EntityGenerationRequest();

        request.setEntityType((String) originalParams.get("entityType"));
        request.setEntityId((String) originalParams.get("entityId"));
        request.setGenerationType((String) originalParams.get("generationType"));

        request.setProviderId(StringUtils.hasText(retryRequest.getProviderId()) ?
                retryRequest.getProviderId() : (String) originalParams.get("providerId"));
        request.setPriority(retryRequest.getPriority() != null ?
                retryRequest.getPriority() : (Integer) originalParams.get("priority"));

        Map<String, Object> mergedParams = new HashMap<>();
        Map<String, Object> originalGenParams = (Map<String, Object>) originalParams.get("params");
        if (originalGenParams != null) {
            mergedParams.putAll(originalGenParams);
        }
        if (retryRequest.getParams() != null) {
            mergedParams.putAll(retryRequest.getParams());
        }
        request.setParams(mergedParams);

        request.setScriptId((String) originalParams.get("scriptId"));
        request.setRelationType((String) originalParams.get("relationType"));
        List<String> refAssetIds = (List<String>) originalParams.get("referenceAssetIds");
        if (refAssetIds != null) {
            request.setReferenceAssetIds(refAssetIds);
        }

        return request;
    }
}
