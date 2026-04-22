package com.actionow.agent.service.impl;

import com.actionow.agent.dto.request.BatchEntityGenerationRequest;
import com.actionow.agent.dto.request.EntityGenerationRequest;
import com.actionow.agent.dto.request.RetryGenerationRequest;
import com.actionow.agent.dto.response.EntityGenerationResponse;
import com.actionow.agent.feign.AssetFeignClient;
import com.actionow.agent.feign.ProjectFeignClient;
import com.actionow.agent.feign.TaskFeignClient;
import com.actionow.agent.service.EntityGenerationFacade;
import com.actionow.common.core.result.Result;
import com.actionow.common.redis.lock.DistributedLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 实体生成编排服务实现
 * 提供一体化的 AI 生成编排：创建Asset → 创建关联 → 提交任务
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityGenerationOrchestrator implements EntityGenerationFacade {

    private static final String LOCK_PREFIX = "entity_generation:";
    private static final long LOCK_WAIT_TIME = 5L;
    private static final long LOCK_LEASE_TIME = 60L;

    private final AssetFeignClient assetFeignClient;
    private final TaskFeignClient taskFeignClient;
    private final ProjectFeignClient projectFeignClient;
    private final DistributedLockService lockService;

    @Override
    public EntityGenerationResponse submitEntityGeneration(
            EntityGenerationRequest request,
            String workspaceId,
            String userId) {

        // 验证请求
        validateRequest(request);

        String lockKey = buildLockKey(request.getEntityType(), request.getEntityId());
        log.info("提交实体生成任务: entityType={}, entityId={}, generationType={}, lockKey={}",
                request.getEntityType(), request.getEntityId(), request.getGenerationType(), lockKey);

        // 获取分布式锁
        if (!lockService.tryLock(lockKey, LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
            log.warn("获取分布式锁失败，存在并发生成请求: {}", lockKey);
            return EntityGenerationResponse.fail(null, "存在并发生成请求，请稍后重试");
        }

        String assetId = null;
        String relationId = null;
        Map<String, Object> generationParams = null;
        try {
            // 1. 创建 Asset
            Result<Map<String, Object>> assetResult = createAsset(request, workspaceId, userId);
            if (!assetResult.isSuccess()) {
                log.error("创建素材失败: {}", assetResult.getMessage());
                return EntityGenerationResponse.fail(null, "创建素材失败: " + assetResult.getMessage());
            }
            assetId = (String) assetResult.getData().get("id");
            log.info("素材创建成功: assetId={}", assetId);

            // 2. 创建 EntityAssetRelation（entityType=ASSET 时跳过）
            if (!"ASSET".equalsIgnoreCase(request.getEntityType())) {
                Result<Map<String, Object>> relationResult = createRelation(
                        request.getEntityType(), request.getEntityId(), assetId,
                        request.getRelationType(), workspaceId, userId);
                if (!relationResult.isSuccess()) {
                    log.error("创建实体素材关联失败: {}", relationResult.getMessage());
                    markAssetFailed(assetId, "创建实体素材关联失败", workspaceId);
                    return EntityGenerationResponse.fail(assetId, "创建实体素材关联失败: " + relationResult.getMessage());
                }
                relationId = (String) relationResult.getData().get("id");
                log.info("实体素材关联创建成功: relationId={}", relationId);
            }

            // 3. 构建完整生成参数
            generationParams = buildGenerationParams(request, workspaceId, userId);

            // 4. 更新 Asset.extraInfo 存储完整生成参数
            Result<Void> extraInfoResult = updateAssetExtraInfo(assetId, generationParams, workspaceId);
            if (!extraInfoResult.isSuccess()) {
                log.warn("更新素材扩展信息失败，但继续提交任务: {}", extraInfoResult.getMessage());
            }

        } catch (Exception e) {
            log.error("创建素材/关联阶段异常: entityType={}, entityId={}",
                    request.getEntityType(), request.getEntityId(), e);
            if (assetId != null) {
                markAssetFailed(assetId, "系统异常: " + e.getMessage(), workspaceId);
            }
            return EntityGenerationResponse.fail(assetId, "系统异常: " + e.getMessage());
        } finally {
            // 5. Asset + Relation 已创建完成，提前释放分布式锁。
            //    后续 submitTask 调用 Task 模块，Task 模块有自己的生成锁，无需继续持有此锁。
            //    提前释放可避免锁续期压力和潜在的级联超时。
            lockService.unlock(lockKey);
        }

        // 6. 锁释放后提交 AI 生成任务（Task 模块内部持有独立锁）
        try {
            Result<Map<String, Object>> taskResult = submitTask(assetId, request, workspaceId, userId);
            if (!taskResult.isSuccess()) {
                log.error("提交生成任务失败: {}", taskResult.getMessage());
                markAssetFailed(assetId, "提交生成任务失败: " + taskResult.getMessage(), workspaceId);
                return EntityGenerationResponse.fail(assetId, "提交生成任务失败: " + taskResult.getMessage());
            }

            Map<String, Object> taskData = taskResult.getData();
            String taskId = (String) taskData.get("taskId");
            String taskStatus = (String) taskData.get("status");
            String providerId = (String) taskData.get("providerId");
            Long creditCost = taskData.get("creditCost") != null ?
                    ((Number) taskData.get("creditCost")).longValue() : 0L;

            log.info("生成任务提交成功: assetId={}, taskId={}, creditCost={}", assetId, taskId, creditCost);

            return EntityGenerationResponse.success(assetId, relationId, taskId, taskStatus,
                    providerId, creditCost, generationParams);

        } catch (Exception e) {
            log.error("提交 AI 生成任务异常: assetId={}", assetId, e);
            markAssetFailed(assetId, "提交任务异常: " + e.getMessage(), workspaceId);
            return EntityGenerationResponse.fail(assetId, "提交任务异常: " + e.getMessage());
        }
    }

    @Override
    public List<EntityGenerationResponse> submitBatchEntityGeneration(
            BatchEntityGenerationRequest request,
            String workspaceId,
            String userId) {

        List<EntityGenerationRequest> requests = request.getRequests();
        if (requests == null || requests.isEmpty()) {
            return Collections.emptyList();
        }

        if (Boolean.TRUE.equals(request.getParallel())) {
            // 并行处理
            List<CompletableFuture<EntityGenerationResponse>> futures = requests.stream()
                    .map(req -> CompletableFuture.supplyAsync(() ->
                            submitEntityGeneration(req, workspaceId, userId)))
                    .toList();

            return futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());
        } else {
            // 顺序处理
            return requests.stream()
                    .map(req -> submitEntityGeneration(req, workspaceId, userId))
                    .collect(Collectors.toList());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public EntityGenerationResponse retryGeneration(
            RetryGenerationRequest request,
            String workspaceId,
            String userId) {

        String assetId = request.getAssetId();
        log.info("重试生成任务: assetId={}", assetId);

        // 1. 获取素材信息
        Result<Map<String, Object>> assetResult = assetFeignClient.getAsset(workspaceId, assetId);
        if (!assetResult.isSuccess()) {
            log.error("获取素材信息失败: {}", assetResult.getMessage());
            return EntityGenerationResponse.fail(assetId, "获取素材信息失败: " + assetResult.getMessage());
        }

        Map<String, Object> assetData = assetResult.getData();
        Map<String, Object> extraInfo = (Map<String, Object>) assetData.get("extraInfo");
        if (extraInfo == null) {
            log.error("素材无生成参数信息: assetId={}", assetId);
            return EntityGenerationResponse.fail(assetId, "素材无生成参数信息，无法重试");
        }

        // 检测 schema 版本漂移，版本不匹配时记录警告（不阻断，尽力兼容）
        Object schemaVersionObj = extraInfo.get("_schemaVersion");
        int storedVersion = schemaVersionObj instanceof Number ? ((Number) schemaVersionObj).intValue() : 1;
        if (storedVersion != EXTRA_INFO_SCHEMA_VERSION) {
            log.warn("extraInfo schema 版本不匹配: assetId={}, stored={}, current={}. " +
                    "如果重试参数解析异常，请检查 generationParams 结构是否变更。",
                    assetId, storedVersion, EXTRA_INFO_SCHEMA_VERSION);
        }

        Map<String, Object> originalParams = (Map<String, Object>) extraInfo.get("generationParams");
        if (originalParams == null) {
            log.error("素材无原始生成参数: assetId={}", assetId);
            return EntityGenerationResponse.fail(assetId, "素材无原始生成参数，无法重试");
        }

        // 2. 合并覆盖参数
        EntityGenerationRequest genRequest = mergeRetryParams(request, originalParams);

        // 3. 更新重试次数
        int retryCount = extraInfo.get("retryCount") != null ?
                ((Number) extraInfo.get("retryCount")).intValue() : 0;
        extraInfo.put("retryCount", retryCount + 1);

        // 4. 构建新的生成参数
        Map<String, Object> generationParams = buildGenerationParams(genRequest, workspaceId, userId);
        extraInfo.put("generationParams", generationParams);
        extraInfo.remove("errorMessage");
        extraInfo.remove("failedAt");

        // 5. 更新 Asset 状态为 GENERATING
        Result<Void> statusResult = assetFeignClient.updateGenerationStatus(workspaceId, assetId, "GENERATING");
        if (!statusResult.isSuccess()) {
            log.warn("更新素材状态失败: {}", statusResult.getMessage());
        }

        // 6. 更新 extraInfo
        Result<Void> extraInfoResult = assetFeignClient.updateAssetExtraInfo(workspaceId, assetId, extraInfo);
        if (!extraInfoResult.isSuccess()) {
            log.warn("更新素材扩展信息失败: {}", extraInfoResult.getMessage());
        }

        // 7. 提交 AI 生成任务
        Result<Map<String, Object>> taskResult = submitTask(assetId, genRequest, workspaceId, userId);
        if (!taskResult.isSuccess()) {
            log.error("重试提交生成任务失败: {}", taskResult.getMessage());
            markAssetFailed(assetId, "重试提交生成任务失败: " + taskResult.getMessage(), workspaceId);
            return EntityGenerationResponse.fail(assetId, "重试提交生成任务失败: " + taskResult.getMessage());
        }

        Map<String, Object> taskData = taskResult.getData();
        String taskId = (String) taskData.get("taskId");
        String taskStatus = (String) taskData.get("status");
        String providerId = (String) taskData.get("providerId");
        Long creditCost = taskData.get("creditCost") != null ?
                ((Number) taskData.get("creditCost")).longValue() : 0L;

        log.info("重试生成任务提交成功: assetId={}, taskId={}, retryCount={}", assetId, taskId, retryCount + 1);

        // 获取原关联ID（如果存在）
        String relationId = (String) originalParams.get("relationId");

        return EntityGenerationResponse.success(assetId, relationId, taskId, taskStatus,
                providerId, creditCost, generationParams);
    }

    @Override
    public Map<String, Object> getGenerationStatus(String assetId, String workspaceId) {
        log.debug("查询生成状态: assetId={}", assetId);

        Result<Map<String, Object>> assetResult = assetFeignClient.getAsset(workspaceId, assetId);
        if (!assetResult.isSuccess()) {
            return Map.of(
                    "success", false,
                    "message", "获取素材信息失败: " + assetResult.getMessage()
            );
        }

        Map<String, Object> assetData = assetResult.getData();
        String generationStatus = (String) assetData.get("generationStatus");
        String taskId = (String) assetData.get("taskId");

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("assetId", assetId);
        result.put("generationStatus", generationStatus);
        result.put("taskId", taskId);

        // 如果有任务ID，获取任务详情
        if (StringUtils.hasText(taskId)) {
            Result<Map<String, Object>> taskResult = taskFeignClient.getTaskResult(taskId);
            if (taskResult.isSuccess()) {
                Map<String, Object> taskData = taskResult.getData();
                result.put("taskStatus", taskData.get("status"));
                result.put("taskProgress", taskData.get("progress"));
                result.put("taskOutput", taskData.get("outputResult"));
            }
        }

        // 获取扩展信息
        @SuppressWarnings("unchecked")
        Map<String, Object> extraInfo = (Map<String, Object>) assetData.get("extraInfo");
        if (extraInfo != null) {
            result.put("retryCount", extraInfo.get("retryCount"));
            result.put("errorMessage", extraInfo.get("errorMessage"));
            result.put("failedAt", extraInfo.get("failedAt"));
        }

        return result;
    }

    // ==================== 私有方法 ====================

    private void validateRequest(EntityGenerationRequest request) {
        if (!StringUtils.hasText(request.getEntityType())) {
            throw new IllegalArgumentException("entityType 不能为空");
        }
        if (!StringUtils.hasText(request.getEntityId())) {
            throw new IllegalArgumentException("entityId 不能为空");
        }
        if (!StringUtils.hasText(request.getGenerationType())) {
            throw new IllegalArgumentException("generationType 不能为空");
        }
        if (request.getParams() == null) {
            throw new IllegalArgumentException("params 不能为空");
        }

        // 验证 entityType
        String entityType = request.getEntityType().toUpperCase();
        Set<String> validTypes = Set.of("SCRIPT", "EPISODE", "STORYBOARD", "CHARACTER", "SCENE", "PROP", "STYLE", "ASSET");
        if (!validTypes.contains(entityType)) {
            throw new IllegalArgumentException("不支持的 entityType: " + request.getEntityType());
        }

        // 验证 generationType
        String generationType = request.getGenerationType().toUpperCase();
        Set<String> validGenTypes = Set.of("IMAGE", "VIDEO", "AUDIO");
        if (!validGenTypes.contains(generationType)) {
            throw new IllegalArgumentException("不支持的 generationType: " + request.getGenerationType());
        }
    }

    private String buildLockKey(String entityType, String entityId) {
        return LOCK_PREFIX + entityType.toUpperCase() + ":" + entityId;
    }

    private Result<Map<String, Object>> createAsset(EntityGenerationRequest request,
                                                     String workspaceId, String userId) {
        Map<String, Object> assetRequest = new HashMap<>();
        assetRequest.put("name", buildAssetName(request));
        // 从 params 获取 prompt 用于描述，如果没有则使用默认描述
        Object prompt = request.getParams() != null ? request.getParams().get("prompt") : null;
        String description = prompt != null ? "AI生成素材 - " + prompt : "AI生成素材";
        assetRequest.put("description", description);
        assetRequest.put("assetType", request.getGenerationType().toUpperCase());
        assetRequest.put("source", "AI_GENERATED");
        assetRequest.put("generationStatus", "GENERATING");

        // 设置作用域：优先用 request 显式指定的 scriptId；缺失时按 entityType 反查实体所属剧本
        String resolvedScriptId = request.getScriptId();
        if (!StringUtils.hasText(resolvedScriptId)) {
            resolvedScriptId = resolveScriptIdFromEntity(
                    request.getEntityType(), request.getEntityId());
            if (StringUtils.hasText(resolvedScriptId)) {
                log.info("scriptId 兜底反推成功: entityType={}, entityId={}, scriptId={}",
                        request.getEntityType(), request.getEntityId(), resolvedScriptId);
                // 回填到 request，使下游也能感知（任务参数、关联记录等都依赖 request.scriptId）
                request.setScriptId(resolvedScriptId);
            }
        }
        if (StringUtils.hasText(resolvedScriptId)) {
            assetRequest.put("scope", "SCRIPT");
            assetRequest.put("scriptId", resolvedScriptId);
        } else {
            assetRequest.put("scope", "WORKSPACE");
        }

        return assetFeignClient.createAsset(workspaceId, userId, assetRequest);
    }

    /**
     * 当 request.scriptId 缺失时，按 entityType 调用对应的 ProjectFeignClient 获取该实体的所属剧本。
     * <p>支持的实体类型：STORYBOARD / EPISODE / CHARACTER / SCENE / PROP / STYLE。
     * 这些实体在 Project 服务侧的 detail 响应都带 {@code scriptId} 字段。
     * <p>对 SCRIPT / ASSET 不反查（前者本身就是剧本；后者非剧本子实体）。
     * 任何 RPC 错误一律静默返回 null，让 createAsset 退化为 WORKSPACE，避免阻塞主流程。
     *
     * @return 反查到的 scriptId；如无法确定返回 null
     */
    private String resolveScriptIdFromEntity(String entityType, String entityId) {
        if (!StringUtils.hasText(entityType) || !StringUtils.hasText(entityId)) {
            return null;
        }
        String upper = entityType.toUpperCase();
        try {
            Result<Map<String, Object>> result = switch (upper) {
                case "STORYBOARD" -> projectFeignClient.getStoryboard(entityId);
                case "EPISODE"    -> projectFeignClient.getEpisode(entityId);
                case "CHARACTER"  -> projectFeignClient.getCharacter(entityId);
                case "SCENE"      -> projectFeignClient.getScene(entityId);
                case "PROP"       -> projectFeignClient.getProp(entityId);
                case "STYLE"      -> projectFeignClient.getStyle(entityId);
                default -> null;
            };
            if (result == null || !result.isSuccess() || result.getData() == null) {
                return null;
            }
            Object value = result.getData().get("scriptId");
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            log.warn("scriptId 兜底反查失败，将退回 WORKSPACE 作用域: entityType={}, entityId={}, error={}",
                    entityType, entityId, e.getMessage());
            return null;
        }
    }

    private String buildAssetName(EntityGenerationRequest request) {
        if (StringUtils.hasText(request.getAssetName())) {
            return request.getAssetName();
        }
        // 自动生成名称
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String type = request.getGenerationType().toLowerCase();
        String entity = request.getEntityType().toLowerCase();
        return String.format("ai_%s_%s_%s", type, entity, timestamp);
    }

    private Result<Map<String, Object>> createRelation(String entityType, String entityId,
                                                        String assetId, String relationType,
                                                        String workspaceId, String userId) {
        Map<String, Object> relationRequest = new HashMap<>();
        relationRequest.put("entityType", entityType.toUpperCase());
        relationRequest.put("entityId", entityId);
        relationRequest.put("assetId", assetId);
        relationRequest.put("relationType", relationType != null ? relationType.toUpperCase() : "DRAFT");
        return assetFeignClient.createEntityAssetRelation(workspaceId, userId, relationRequest);
    }

    private Map<String, Object> buildGenerationParams(EntityGenerationRequest request,
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
        // 直接存储完整的 params Map
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
     * extraInfo schema 版本号，随 generationParams 结构变更时递增。
     * Retry 时若版本不匹配，应警告并降级（使用原参数中仍兼容的字段）。
     * 当前结构：{ generationParams: {...}, retryCount: 0, _schemaVersion: 2 }
     */
    private static final int EXTRA_INFO_SCHEMA_VERSION = 2;

    private Result<Void> updateAssetExtraInfo(String assetId, Map<String, Object> generationParams,
                                               String workspaceId) {
        Map<String, Object> extraInfo = new HashMap<>();
        extraInfo.put("generationParams", generationParams);
        extraInfo.put("retryCount", 0);
        // 版本号用于检测 extraInfo schema 漂移：若未来修改 generationParams 结构，
        // 递增 EXTRA_INFO_SCHEMA_VERSION，retryGeneration 时比对版本并给出告警。
        extraInfo.put("_schemaVersion", EXTRA_INFO_SCHEMA_VERSION);
        return assetFeignClient.updateAssetExtraInfo(workspaceId, assetId, extraInfo);
    }

    private Result<Map<String, Object>> submitTask(String assetId, EntityGenerationRequest request,
                                                    String workspaceId, String userId) {
        Map<String, Object> taskRequest = new HashMap<>();
        taskRequest.put("assetId", assetId);
        taskRequest.put("generationType", request.getGenerationType().toUpperCase());

        if (StringUtils.hasText(request.getProviderId())) {
            taskRequest.put("providerId", request.getProviderId());
        }
        // 直接传递完整的 params
        if (request.getParams() != null) {
            taskRequest.put("params", request.getParams());
        }
        if (request.getReferenceAssetIds() != null && !request.getReferenceAssetIds().isEmpty()) {
            taskRequest.put("referenceAssetIds", request.getReferenceAssetIds());
        }
        if (request.getPriority() != null) {
            taskRequest.put("priority", request.getPriority());
        }

        return taskFeignClient.submitAiGeneration(workspaceId, userId, taskRequest);
    }

    private void markAssetFailed(String assetId, String errorMessage, String workspaceId) {
        try {
            // 更新状态为 FAILED
            assetFeignClient.updateGenerationStatus(workspaceId, assetId, "FAILED");

            // 更新错误信息到 extraInfo
            Map<String, Object> extraInfo = new HashMap<>();
            extraInfo.put("errorMessage", errorMessage);
            extraInfo.put("failedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            assetFeignClient.updateAssetExtraInfo(workspaceId, assetId, extraInfo);

            log.info("素材已标记为失败: assetId={}, errorMessage={}", assetId, errorMessage);
        } catch (Exception e) {
            log.error("标记素材失败状态异常: assetId={}", assetId, e);
        }
    }

    @SuppressWarnings("unchecked")
    private EntityGenerationRequest mergeRetryParams(RetryGenerationRequest retryRequest,
                                                      Map<String, Object> originalParams) {
        EntityGenerationRequest request = new EntityGenerationRequest();

        // 从原参数恢复必填字段
        request.setEntityType((String) originalParams.get("entityType"));
        request.setEntityId((String) originalParams.get("entityId"));
        request.setGenerationType((String) originalParams.get("generationType"));

        // 恢复 providerId（可被覆盖）
        request.setProviderId(StringUtils.hasText(retryRequest.getProviderId()) ?
                retryRequest.getProviderId() : (String) originalParams.get("providerId"));
        request.setPriority(retryRequest.getPriority() != null ?
                retryRequest.getPriority() : (Integer) originalParams.get("priority"));

        // 合并 params：原参数 + 重试请求的覆盖参数
        Map<String, Object> mergedParams = new HashMap<>();
        Map<String, Object> originalGenParams = (Map<String, Object>) originalParams.get("params");
        if (originalGenParams != null) {
            mergedParams.putAll(originalGenParams);
        }
        if (retryRequest.getParams() != null) {
            mergedParams.putAll(retryRequest.getParams());
        }
        request.setParams(mergedParams);

        // 其他字段从原参数恢复
        request.setScriptId((String) originalParams.get("scriptId"));
        request.setRelationType((String) originalParams.get("relationType"));
        List<String> refAssetIds = (List<String>) originalParams.get("referenceAssetIds");
        if (refAssetIds != null) {
            request.setReferenceAssetIds(refAssetIds);
        }

        return request;
    }
}
