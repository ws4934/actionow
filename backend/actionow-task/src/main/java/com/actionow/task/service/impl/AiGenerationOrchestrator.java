package com.actionow.task.service.impl;

import com.actionow.common.core.constant.CommonConstants;
import com.actionow.common.core.constant.RedisKeyConstants;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.id.UuidGenerator;
import com.actionow.common.core.result.Result;
import com.actionow.common.core.result.ResultCode;
import com.actionow.common.mq.constant.MqConstants;
import com.actionow.common.mq.message.MessageWrapper;
import com.actionow.common.mq.outbox.TransactionalMessageProducer;
import com.actionow.common.mq.producer.MessageProducer;
import com.actionow.common.redis.lock.DistributedLockService;
import com.actionow.task.config.TaskRuntimeConfigService;
import com.actionow.task.constant.TaskConstants;
import com.actionow.task.dto.*;
import com.actionow.task.entity.Task;
import com.actionow.task.feign.AiFeignClient;
import com.actionow.task.feign.AssetFeignClient;
import com.actionow.task.mapper.TaskMapper;
import com.actionow.task.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * AI 生成任务编排器
 * 实现 AI 生成任务的完整编排流程，委托子服务处理各职责域：
 * - {@link GenerationCostService} 积分计算与提供商查询
 * - {@link AssetLifecycleService} 素材状态管理与实体生成辅助
 * - {@link TaskExecutionService} 任务执行与响应模式解析
 *
 * 本类保留编排逻辑、积分操作、消息发送。
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiGenerationOrchestrator implements AiGenerationFacade, TaskCompletionHandler {

    private final TaskMapper taskMapper;
    private final MessageProducer messageProducer;
    private final TransactionalMessageProducer transactionalMessageProducer;
    private final AiFeignClient aiFeignClient;
    private final AssetFeignClient assetFeignClient;
    private final DistributedLockService distributedLockService;
    private final PointsTransactionManager pointsTransactionManager;
    private final TaskPriorityQueueService taskPriorityQueueService;
    private final TaskRuntimeConfigService runtimeConfig;

    // 委托服务
    private final GenerationCostService costService;
    private final AssetLifecycleService assetService;
    private final TaskExecutionService executionService;

    private static final String ENTITY_LOCK_PREFIX = "entity_generation:";

    // ==================== 提交 API ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GenerationTaskResponse submitGeneration(SubmitGenerationRequest request,
                                                   String workspaceId, String userId) {
        boolean hasAsset = StringUtils.hasText(request.getAssetId());

        log.info("提交 AI 生成任务: assetId={}, providerId={}, generationType={}, workspaceId={}",
                request.getAssetId(), request.getProviderId(), request.getGenerationType(), workspaceId);

        if (hasAsset) {
            String lockKey = RedisKeyConstants.LOCK_ASSET_GENERATION + request.getAssetId();
            long leaseTime = runtimeConfig.getGenerationLockExpireSeconds();

            GenerationTaskResponse result = distributedLockService.executeWithLock(
                    lockKey, 10L, leaseTime, TimeUnit.SECONDS,
                    () -> doSubmitGeneration(request, workspaceId, userId)
            );

            if (result == null) {
                throw new BusinessException(ResultCode.CONCURRENT_OPERATION, "素材正在生成中，请稍后重试");
            }
            return result;
        } else {
            return doSubmitGeneration(request, workspaceId, userId);
        }
    }

    private GenerationTaskResponse doSubmitGeneration(SubmitGenerationRequest request,
                                                      String workspaceId, String userId) {
        boolean hasAsset = StringUtils.hasText(request.getAssetId());

        if (hasAsset) {
            assetService.checkAssetNotGenerating(request.getAssetId());
        }

        // 确定模型提供商
        String providerId = request.getProviderId();
        AvailableProviderResponse provider;
        if (StringUtils.hasText(providerId)) {
            Result<AvailableProviderResponse> providerResult = aiFeignClient.getProviderDetail(providerId);
            if (!providerResult.isSuccess() || providerResult.getData() == null) {
                throw new BusinessException(ResultCode.PARAM_INVALID,
                        "无法识别的模型提供商标识: '" + providerId + "'。支持 UUID、pluginId 或模型名称");
            }
            provider = providerResult.getData();
            providerId = provider.getId();
        } else {
            Result<List<AvailableProviderResponse>> providersResult =
                    aiFeignClient.getAvailableProviders(request.getGenerationType());
            if (!providersResult.isSuccess() || CollectionUtils.isEmpty(providersResult.getData())) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "未找到可用的模型提供商");
            }
            provider = providersResult.getData().get(0);
            providerId = provider.getId();
        }

        costService.validateReferencedAssets(request.getParams(), provider);

        Long costPoints = costService.estimateCreditCost(providerId, request.getParams(), provider);

        // 提前生成 taskId，用作无素材场景的 freezeBusinessId（避免多任务共用 providerId 导致锁竞争）
        String taskId = UuidGenerator.generateUuidV7();
        String freezeBusinessId = hasAsset ? request.getAssetId() : taskId;
        String transactionId = pointsTransactionManager.freezePoints(
                workspaceId, userId, costPoints,
                "AI_GENERATION", freezeBusinessId,
                "AI 生成: " + request.getGenerationType()
        );

        try {
            Task task = createTask(request, workspaceId, userId, providerId, provider.getName(), provider.getTimeout(), transactionId, costPoints, taskId);

            if (hasAsset) {
                assetService.updateAssetStatusGenerating(request.getAssetId(), task.getId(), providerId);
            }

            sendTaskToQueue(task);

            log.info("AI 生成任务创建成功: taskId={}, assetId={}, providerId={}",
                    task.getId(), request.getAssetId(), providerId);

            return GenerationTaskResponse.builder()
                    .taskId(task.getId())
                    .assetId(request.getAssetId())
                    .providerId(providerId)
                    .providerName(provider.getName())
                    .status(task.getStatus())
                    .creditCost(costPoints)
                    .build();

        } catch (Exception e) {
            log.error("创建 AI 任务失败，触发解冻: freezeBusinessId={}", freezeBusinessId, e);
            pointsTransactionManager.unfreezePointsAsync(
                    workspaceId, userId, freezeBusinessId, "AI_GENERATION", "任务创建失败，自动解冻",
                    costPoints
            );
            throw e;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchGenerationResponse submitBatchGeneration(BatchGenerationRequest request,
                                                          String workspaceId, String userId) {
        log.info("提交批量 AI 生成任务: count={}, sequential={}, workspaceId={}",
                request.getTasks().size(), request.isSequential(), workspaceId);

        String batchId = UuidGenerator.generateUuidV7();
        List<BatchGenerationResponse.TaskSubmitResult> results = new ArrayList<>();
        int totalFrozenCredits = 0;
        int submittedCount = 0;
        int failedCount = 0;

        for (int i = 0; i < request.getTasks().size(); i++) {
            SubmitGenerationRequest taskRequest = request.getTasks().get(i);

            if (taskRequest.getPriority() == null) {
                taskRequest.setPriority(TaskConstants.Priority.LOW);
            }

            try {
                taskRequest.setSource(TaskConstants.TaskSource.BATCH);
                GenerationTaskResponse taskResponse = submitGeneration(taskRequest, workspaceId, userId);

                results.add(BatchGenerationResponse.TaskSubmitResult.builder()
                        .index(i)
                        .success(true)
                        .taskId(taskResponse.getTaskId())
                        .assetId(taskResponse.getAssetId())
                        .frozenCredits(taskResponse.getCreditCost() != null ? taskResponse.getCreditCost().intValue() : 0)
                        .build());

                totalFrozenCredits += taskResponse.getCreditCost() != null ? taskResponse.getCreditCost().intValue() : 0;
                submittedCount++;

            } catch (Exception e) {
                log.warn("批量任务第 {} 个提交失败: {}", i, e.getMessage());

                results.add(BatchGenerationResponse.TaskSubmitResult.builder()
                        .index(i)
                        .success(false)
                        .errorMessage(e.getMessage())
                        .build());

                failedCount++;

                if (request.isStopOnError() && request.isSequential()) {
                    log.info("批量任务遇错停止: index={}", i);
                    break;
                }
            }
        }

        log.info("批量任务提交完成: batchId={}, submitted={}, failed={}",
                batchId, submittedCount, failedCount);

        return BatchGenerationResponse.builder()
                .batchId(batchId)
                .batchName(request.getBatchName())
                .totalCount(request.getTasks().size())
                .submittedCount(submittedCount)
                .failedCount(failedCount)
                .sequential(request.isSequential())
                .tasks(results)
                .totalFrozenCredits(totalFrozenCredits)
                .build();
    }

    // ==================== 实体生成 API ====================

    @Override
    public EntityGenerationResponse submitEntityGeneration(EntityGenerationRequest request,
                                                            String workspaceId, String userId) {
        assetService.validateEntityRequest(request);

        String lockKey = ENTITY_LOCK_PREFIX + request.getEntityType().toUpperCase() + ":" + request.getEntityId();
        log.info("提交实体生成任务: entityType={}, entityId={}, generationType={}, lockKey={}",
                request.getEntityType(), request.getEntityId(), request.getGenerationType(), lockKey);

        EntityGenerationResponse result = distributedLockService.executeWithLock(
                lockKey, 5L, 60L, TimeUnit.SECONDS,
                () -> doSubmitEntityGeneration(request, workspaceId, userId)
        );

        if (result == null) {
            log.warn("获取分布式锁失败，存在并发生成请求: {}", lockKey);
            return EntityGenerationResponse.fail(null, "存在并发生成请求，请稍后重试");
        }

        return result;
    }

    private EntityGenerationResponse doSubmitEntityGeneration(EntityGenerationRequest request,
                                                               String workspaceId, String userId) {
        String assetId = null;
        try {
            Result<Map<String, Object>> assetResult = assetService.createAssetForEntity(request, workspaceId, userId);
            if (!assetResult.isSuccess()) {
                log.error("创建素材失败: {}", assetResult.getMessage());
                return EntityGenerationResponse.fail(null, "创建素材失败: " + assetResult.getMessage());
            }
            assetId = (String) assetResult.getData().get("id");
            log.info("素材创建成功: assetId={}", assetId);

            String relationId = null;
            if (!"ASSET".equalsIgnoreCase(request.getEntityType())) {
                Result<Map<String, Object>> relationResult = assetService.createRelationForEntity(
                        request.getEntityType(), request.getEntityId(), assetId,
                        request.getRelationType(), workspaceId, userId);
                if (!relationResult.isSuccess()) {
                    log.error("创建实体素材关联失败: {}", relationResult.getMessage());
                    assetService.markAssetFailedStatus(assetId, "创建实体素材关联失败");
                    return EntityGenerationResponse.fail(assetId, "创建实体素材关联失败: " + relationResult.getMessage());
                }
                relationId = (String) relationResult.getData().get("id");
                log.info("实体素材关联创建成功: relationId={}", relationId);
            }

            Map<String, Object> generationParams = assetService.buildEntityGenerationParams(request, workspaceId, userId);

            Result<Void> extraInfoResult = assetService.updateAssetExtraInfoForEntity(assetId, generationParams, workspaceId);
            if (!extraInfoResult.isSuccess()) {
                log.warn("更新素材扩展信息失败，但继续提交任务: {}", extraInfoResult.getMessage());
            }

            SubmitGenerationRequest genRequest = SubmitGenerationRequest.builder()
                    .assetId(assetId)
                    .generationType(request.getGenerationType())
                    .providerId(request.getProviderId())
                    .params(request.getParams())
                    .priority(request.getPriority())
                    .responseMode(request.getResponseMode())
                    .entityType(request.getEntityType())
                    .entityId(request.getEntityId())
                    .entityName(request.getAssetName())
                    .scriptId(request.getScriptId())
                    .source(request.getSource())
                    .build();

            GenerationTaskResponse taskResponse = submitGeneration(genRequest, workspaceId, userId);

            log.info("实体生成任务提交成功: assetId={}, taskId={}, creditCost={}",
                    assetId, taskResponse.getTaskId(), taskResponse.getCreditCost());

            return EntityGenerationResponse.success(
                    assetId, relationId,
                    taskResponse.getTaskId(), taskResponse.getStatus(),
                    taskResponse.getProviderId(), taskResponse.getCreditCost(),
                    generationParams);

        } catch (Exception e) {
            log.error("提交实体生成任务异常: entityType={}, entityId={}",
                    request.getEntityType(), request.getEntityId(), e);
            if (assetId != null) {
                assetService.markAssetFailedStatus(assetId, "系统异常: " + e.getMessage());
            }
            return EntityGenerationResponse.fail(assetId, "系统异常: " + e.getMessage());
        }
    }

    @Override
    public BatchEntityGenerationResponse submitBatchEntityGeneration(BatchEntityGenerationRequest request,
                                                                      String workspaceId, String userId) {
        List<EntityGenerationRequest> requests = request.getRequests();
        if (requests == null || requests.isEmpty()) {
            return BatchEntityGenerationResponse.builder()
                    .batchId(UuidGenerator.generateUuidV7())
                    .totalCount(0)
                    .submittedCount(0)
                    .failedCount(0)
                    .results(Collections.emptyList())
                    .build();
        }

        String batchId = UuidGenerator.generateUuidV7();
        log.info("提交批量实体生成任务: batchId={}, count={}, parallel={}",
                batchId, requests.size(), request.getParallel());

        List<EntityGenerationResponse> results;
        if (Boolean.TRUE.equals(request.getParallel())) {
            List<CompletableFuture<EntityGenerationResponse>> futures = requests.stream()
                    .map(req -> {
                        req.setSource(TaskConstants.TaskSource.BATCH);
                        return CompletableFuture.supplyAsync(() ->
                                submitEntityGeneration(req, workspaceId, userId));
                    })
                    .toList();
            int timeoutSeconds = runtimeConfig.getDefaultTimeoutSeconds();
            Instant deadline = Instant.now().plusSeconds(timeoutSeconds);
            results = new ArrayList<>();
            for (CompletableFuture<EntityGenerationResponse> future : futures) {
                try {
                    long remainingMs = Duration.between(Instant.now(), deadline).toMillis();
                    if (remainingMs <= 0) {
                        log.warn("批量实体生成全局超时，剩余 {} 个 future 标记为超时: batchId={}",
                                futures.size() - results.size(), batchId);
                        results.add(EntityGenerationResponse.fail(null, "生成超时（全局截止时间已过）"));
                        continue;
                    }
                    results.add(future.get(remainingMs, TimeUnit.MILLISECONDS));
                } catch (java.util.concurrent.TimeoutException e) {
                    log.error("批量实体生成超时: batchId={}, timeout={}s", batchId, timeoutSeconds);
                    results.add(EntityGenerationResponse.fail(null, "生成超时"));
                } catch (Exception e) {
                    log.error("批量实体生成异常: batchId={}", batchId, e);
                    results.add(EntityGenerationResponse.fail(null,
                            e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
                }
            }
        } else {
            results = new ArrayList<>();
            for (EntityGenerationRequest req : requests) {
                req.setSource(TaskConstants.TaskSource.BATCH);
                EntityGenerationResponse resp = submitEntityGeneration(req, workspaceId, userId);
                results.add(resp);
                if (!resp.isSuccess() && Boolean.TRUE.equals(request.getStopOnError())) {
                    log.info("批量任务遇错停止: batchId={}", batchId);
                    break;
                }
            }
        }

        int submittedCount = (int) results.stream().filter(EntityGenerationResponse::isSuccess).count();
        int failedCount = results.size() - submittedCount;
        long totalFrozenCredits = results.stream()
                .filter(EntityGenerationResponse::isSuccess)
                .mapToLong(r -> r.getCreditCost() != null ? r.getCreditCost() : 0L)
                .sum();

        log.info("批量实体生成任务完成: batchId={}, submitted={}, failed={}",
                batchId, submittedCount, failedCount);

        return BatchEntityGenerationResponse.builder()
                .batchId(batchId)
                .batchName(request.getBatchName())
                .totalCount(requests.size())
                .submittedCount(submittedCount)
                .failedCount(failedCount)
                .parallel(Boolean.TRUE.equals(request.getParallel()))
                .totalFrozenCredits(totalFrozenCredits)
                .results(results)
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public EntityGenerationResponse retryGeneration(RetryGenerationRequest request,
                                                     String workspaceId, String userId) {
        String assetId = request.getAssetId();
        log.info("重试生成任务: assetId={}", assetId);

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

        Map<String, Object> originalParams = (Map<String, Object>) extraInfo.get("generationParams");
        if (originalParams == null) {
            log.error("素材无原始生成参数: assetId={}", assetId);
            return EntityGenerationResponse.fail(assetId, "素材无原始生成参数，无法重试");
        }

        EntityGenerationRequest genRequest = assetService.mergeRetryParameters(request, originalParams);

        int retryCount = extraInfo.get("retryCount") != null ?
                ((Number) extraInfo.get("retryCount")).intValue() : 0;
        extraInfo.put("retryCount", retryCount + 1);

        Map<String, Object> generationParams = assetService.buildEntityGenerationParams(genRequest, workspaceId, userId);
        extraInfo.put("generationParams", generationParams);
        extraInfo.remove("errorMessage");
        extraInfo.remove("failedAt");

        assetFeignClient.updateGenerationStatus(assetId, "GENERATING");
        assetFeignClient.updateAssetExtraInfo(workspaceId, assetId, extraInfo);

        try {
            SubmitGenerationRequest submitRequest = SubmitGenerationRequest.builder()
                    .assetId(assetId)
                    .generationType(genRequest.getGenerationType())
                    .providerId(genRequest.getProviderId())
                    .params(genRequest.getParams())
                    .priority(genRequest.getPriority())
                    .responseMode(genRequest.getResponseMode())
                    .entityType(genRequest.getEntityType())
                    .entityId(genRequest.getEntityId())
                    .scriptId(genRequest.getScriptId())
                    .source(TaskConstants.TaskSource.RETRY)
                    .build();

            GenerationTaskResponse taskResponse = submitGeneration(submitRequest, workspaceId, userId);

            log.info("重试生成任务提交成功: assetId={}, taskId={}, retryCount={}",
                    assetId, taskResponse.getTaskId(), retryCount + 1);

            String relationId = (String) originalParams.get("relationId");
            return EntityGenerationResponse.success(
                    assetId, relationId,
                    taskResponse.getTaskId(), taskResponse.getStatus(),
                    taskResponse.getProviderId(), taskResponse.getCreditCost(),
                    generationParams);

        } catch (Exception e) {
            log.error("重试提交生成任务失败: assetId={}", assetId, e);
            assetService.markAssetFailedStatus(assetId, "重试提交生成任务失败: " + e.getMessage());
            return EntityGenerationResponse.fail(assetId, "重试提交生成任务失败: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> getGenerationStatus(String assetId, String workspaceId) {
        log.debug("查询生成状态: assetId={}", assetId);

        Result<Map<String, Object>> assetResult = assetFeignClient.getAsset(workspaceId, assetId);
        if (!assetResult.isSuccess()) {
            return Map.of("success", false, "message", "获取素材信息失败: " + assetResult.getMessage());
        }

        Map<String, Object> assetData = assetResult.getData();
        String generationStatus = (String) assetData.get("generationStatus");
        String taskId = (String) assetData.get("taskId");

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("assetId", assetId);
        result.put("generationStatus", generationStatus);
        result.put("taskId", taskId);

        if (StringUtils.hasText(taskId)) {
            Task task = taskMapper.selectById(taskId);
            if (task != null) {
                result.put("taskStatus", task.getStatus());
                result.put("taskProgress", task.getProgress());
                result.put("taskOutput", task.getOutputResult());
            }
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> extraInfo = (Map<String, Object>) assetData.get("extraInfo");
        if (extraInfo != null) {
            result.put("retryCount", extraInfo.get("retryCount"));
            result.put("errorMessage", extraInfo.get("errorMessage"));
            result.put("failedAt", extraInfo.get("failedAt"));
        }

        return result;
    }

    // ==================== 任务执行（委托） ====================

    @Override
    public void executeTask(Task task) {
        executionService.executeTask(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleCompletion(String taskId, ProviderExecutionResult result) {
        log.info("处理任务完成回调: taskId={}, success={}", taskId, result.isSuccess());

        Task task = getTaskOrThrow(taskId);

        if (result.isSuccess()) {
            onSuccess(task, result);
        } else {
            onFailure(task, result.getErrorCode(), result.getErrorMessage(),
                    Map.of("difyError", result.getErrorMessage() != null ? result.getErrorMessage() : ""));
        }
    }

    // ==================== TaskCompletionHandler 实现 ====================

    @Override
    public void onSuccess(Task task, ProviderExecutionResult result) {
        // 1. 更新素材状态
        assetService.handleSuccessfulAssetUpdate(task, result);

        // 2. 标记任务完成 + 切换实体上下文
        Map<String, Object> outputResult = result.toMap();
        task.setStatus(TaskConstants.TaskStatus.COMPLETED);
        task.setProgress(100);
        task.setOutputResult(outputResult);
        task.setCompletedAt(LocalDateTime.now());
        if (StringUtils.hasText(result.getThumbnailUrl())) {
            task.setThumbnailUrl(result.getThumbnailUrl());
        }
        if (result.getCreditCost() != null) {
            task.setCreditCost(result.getCreditCost());
        }
        assetService.applySourceEntityContext(task);
        taskMapper.updateById(task);

        // 3. 异步确认消费积分
        confirmConsumePointsAsync(task);

        // 4. 发送消息
        sendTaskCompletionMessage(task);
        sendBatchJobTaskCallback(task);

        log.info("任务执行成功: taskId={}", task.getId());
        log.debug("任务执行成功: result={}", result.toString());
    }

    @Override
    public void onFailure(Task task, String errorCode, String errorMessage, Map<String, Object> errorDetail) {
        // 1. 更新素材状态
        assetService.handleFailedAssetUpdate(task, errorMessage);

        // 2. 标记任务失败 + 切换实体上下文
        task.setStatus(TaskConstants.TaskStatus.FAILED);
        task.setErrorCode(errorCode);
        task.setErrorMessage(errorMessage);
        task.setErrorDetail(new HashMap<>(errorDetail));
        task.setCompletedAt(LocalDateTime.now());
        assetService.applySourceEntityContext(task);
        taskMapper.updateById(task);

        // 3. 异步解冻积分
        unfreezePointsAsync(task);

        // 4. 发送消息
        sendTaskCompletionMessage(task);
        sendBatchJobTaskCallback(task);

        log.info("任务执行失败: taskId={}, error={}", task.getId(), errorMessage);
    }

    // ==================== 取消 & 查询 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelGeneration(String taskId, String workspaceId, String userId) {
        log.info("取消 AI 生成任务: taskId={}, userId={}", taskId, userId);

        Task task = getTaskOrThrow(taskId);

        if (!TaskConstants.TaskStatus.PENDING.equals(task.getStatus())) {
            throw new BusinessException(ResultCode.TASK_ALREADY_RUNNING);
        }

        try {
            assetFeignClient.updateGenerationStatus(task.getEntityId(), "DRAFT");
        } catch (Exception e) {
            log.error("更新素材状态失败: assetId={}", task.getEntityId(), e);
        }

        assetService.applySourceEntityContext(task);
        task.setStatus(TaskConstants.TaskStatus.CANCELLED);
        task.setCompletedAt(LocalDateTime.now());
        taskMapper.updateById(task);

        unfreezePointsAsync(task);

        log.info("AI 生成任务取消成功: taskId={}", taskId);
    }

    @Override
    public List<AvailableProviderResponse> getAvailableProviders(String providerType) {
        return costService.getAvailableProviders(providerType);
    }

    @Override
    public Map<String, Object> estimateCost(String providerId, Map<String, Object> params) {
        return costService.estimateCost(providerId, params);
    }

    @Override
    public void adjustTaskPriority(String taskId, String workspaceId, String userId, int priority) {
        log.info("调整任务优先级: taskId={}, newPriority={}, userId={}", taskId, priority, userId);

        if (priority < TaskPriorityQueueService.PRIORITY_HIGHEST
                || priority > TaskPriorityQueueService.PRIORITY_LOWEST) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "优先级必须在1-5之间");
        }

        Task task = getTaskOrThrow(taskId);

        if (!workspaceId.equals(task.getWorkspaceId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作此任务");
        }

        if (!TaskConstants.TaskStatus.PENDING.equals(task.getStatus())) {
            throw new BusinessException(ResultCode.TASK_ALREADY_RUNNING, "只有等待中的任务才能调整优先级");
        }

        boolean success = taskPriorityQueueService.adjustPriority(taskId, priority);
        if (!success) {
            task.setPriority(priority);
            taskMapper.updateById(task);
        }

        log.info("任务优先级调整成功: taskId={}, newPriority={}", taskId, priority);
    }

    @Override
    public int getQueuePosition(String taskId) {
        return taskPriorityQueueService.getPosition(taskId);
    }

    // ==================== 私有方法 ====================

    private Task createTask(SubmitGenerationRequest request, String workspaceId, String userId,
                            String providerId, String providerName, Integer providerTimeout,
                            String transactionId, Long costPoints, String taskId) {
        Task task = new Task();
        task.setId(taskId);
        task.setWorkspaceId(workspaceId);
        task.setType(executionService.mapGenerationTypeToTaskType(request.getGenerationType()));
        task.setTitle(request.getTitle() != null ? request.getTitle() : request.getGenerationType() + " 生成");
        task.setStatus(TaskConstants.TaskStatus.PENDING);
        task.setPriority(request.getPriority() != null ? request.getPriority() : TaskConstants.Priority.NORMAL);
        task.setProgress(0);
        if (StringUtils.hasText(request.getAssetId())) {
            task.setEntityId(request.getAssetId());
            task.setEntityType("ASSET");
        }
        if (StringUtils.hasText(request.getScriptId())) {
            task.setScriptId(request.getScriptId());
        }
        task.setProviderId(providerId);
        task.setGenerationType(request.getGenerationType());
        task.setSource(StringUtils.hasText(request.getSource())
                ? request.getSource() : TaskConstants.TaskSource.MANUAL);
        task.setCreditCost(0);
        task.setRetryCount(0);
        task.setMaxRetry(runtimeConfig.getDefaultMaxRetry());
        // 优先使用 provider 配置的超时，否则使用全局默认值
        int timeoutSeconds = runtimeConfig.getDefaultTimeoutSeconds();
        if (providerTimeout != null && providerTimeout > 0) {
            timeoutSeconds = providerTimeout / 1000; // provider.timeout 单位是毫秒
        }
        task.setTimeoutSeconds(timeoutSeconds);
        task.setCreatorId(userId);

        Map<String, Object> inputParams = new HashMap<>();
        inputParams.put("userId", userId);
        inputParams.put("providerId", providerId);
        inputParams.put("providerName", providerName);
        inputParams.put("generationType", request.getGenerationType());
        inputParams.put("transactionId", transactionId);
        inputParams.put("costPoints", costPoints);
        inputParams.put("responseMode", request.getResponseMode());
        if (StringUtils.hasText(request.getAssetId())) {
            inputParams.put("assetId", request.getAssetId());
        }
        inputParams.put("freezeBusinessId",
                StringUtils.hasText(request.getAssetId()) ? request.getAssetId() : taskId);
        if (request.getParams() != null) {
            inputParams.put("params", request.getParams());
        }
        if (StringUtils.hasText(request.getEntityType())
                && !"ASSET".equalsIgnoreCase(request.getEntityType())
                && StringUtils.hasText(request.getEntityId())) {
            inputParams.put("sourceEntityType", request.getEntityType());
            inputParams.put("sourceEntityId", request.getEntityId());
            if (StringUtils.hasText(request.getEntityName())) {
                inputParams.put("sourceEntityName", request.getEntityName());
            }
        }
        task.setInputParams(inputParams);

        taskMapper.insert(task);
        return task;
    }

    private void sendTaskToQueue(Task task) {
        MessageWrapper<TaskResponse> message = MessageWrapper.wrap(
                MqConstants.Task.MSG_CREATED,
                TaskResponse.fromEntity(task)
        );
        transactionalMessageProducer.sendInTransaction(
                MqConstants.EXCHANGE_DIRECT, MqConstants.Task.ROUTING_CREATED, message
        );
    }

    private void confirmConsumePointsAsync(Task task) {
        Map<String, Object> inputParams = task.getInputParams();
        String userId = (String) inputParams.get("userId");
        Long costPoints = inputParams.get("costPoints") != null ?
                ((Number) inputParams.get("costPoints")).longValue() : 10L;
        String businessId = (String) inputParams.get("freezeBusinessId");
        if (!StringUtils.hasText(businessId)) {
            businessId = (String) inputParams.get("assetId");
            if (!StringUtils.hasText(businessId)) {
                businessId = task.getEntityId();
            }
        }

        pointsTransactionManager.confirmConsumeAsync(
                task.getWorkspaceId(), userId, businessId,
                "AI_GENERATION", costPoints, "AI 生成完成"
        );
    }

    private void unfreezePointsAsync(Task task) {
        Map<String, Object> inputParams = task.getInputParams();
        if (inputParams == null) {
            return;
        }
        String userId = (String) inputParams.get("userId");
        String businessId = (String) inputParams.get("freezeBusinessId");
        if (!StringUtils.hasText(businessId)) {
            businessId = (String) inputParams.get("assetId");
            if (!StringUtils.hasText(businessId)) {
                businessId = task.getEntityId();
            }
        }
        Long costPoints = inputParams.get("costPoints") != null ?
                ((Number) inputParams.get("costPoints")).longValue() : null;

        pointsTransactionManager.unfreezePointsAsync(
                task.getWorkspaceId(), userId, businessId,
                "AI_GENERATION", "任务取消/失败，解冻积分",
                costPoints
        );
    }

    private void sendTaskCompletionMessage(Task task) {
        try {
            MessageWrapper<TaskResponse> message = MessageWrapper.wrap(
                    MqConstants.Task.MSG_STATUS_CHANGED,
                    TaskResponse.fromEntity(task)
            );
            messageProducer.send(MqConstants.EXCHANGE_DIRECT, MqConstants.Task.ROUTING_COMPLETED, message);
        } catch (Exception e) {
            log.warn("发送任务完成消息失败: {}", e.getMessage());
        }
    }

    private void sendBatchJobTaskCallback(Task task) {
        if (!StringUtils.hasText(task.getBatchJobId())) {
            return;
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("taskId", task.getId());
            payload.put("batchJobId", task.getBatchJobId());
            payload.put("batchItemId", task.getBatchItemId());
            payload.put("status", task.getStatus());
            payload.put("creditCost", task.getCreditCost() != null ? task.getCreditCost() : 0);
            payload.put("errorMessage", task.getErrorMessage());

            MessageWrapper<Map<String, Object>> message = MessageWrapper.<Map<String, Object>>builder()
                    .messageId(UuidGenerator.generateUuidV7())
                    .messageType(MqConstants.BatchJob.MSG_TASK_CALLBACK)
                    .payload(payload)
                    .workspaceId(task.getWorkspaceId())
                    .senderId(task.getCreatorId())
                    .build();
            messageProducer.sendDirect(MqConstants.BatchJob.ROUTING_TASK_CALLBACK, message);
        } catch (Exception e) {
            log.warn("发送批量作业任务回调失败: taskId={}, batchJobId={}", task.getId(), task.getBatchJobId(), e);
        }
    }

    private Task getTaskOrThrow(String taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null || task.getDeleted() == CommonConstants.DELETED) {
            throw new BusinessException(ResultCode.TASK_NOT_FOUND);
        }
        return task;
    }
}
