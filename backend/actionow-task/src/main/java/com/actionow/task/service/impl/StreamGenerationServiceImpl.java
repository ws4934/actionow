package com.actionow.task.service.impl;

import com.actionow.common.core.constant.CommonConstants;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.result.Result;
import com.actionow.common.core.security.InternalAuthProperties;
import com.actionow.common.core.security.InternalAuthUtils;
import com.actionow.task.dto.*;
import com.actionow.task.entity.Task;
import com.actionow.task.feign.AiFeignClient;
import com.actionow.task.feign.WalletFeignClient;
import com.actionow.task.mapper.TaskMapper;
import com.actionow.task.service.StreamGenerationService;
import com.actionow.task.service.TaskService;
import com.actionow.task.service.PointsTransactionManager;
import com.actionow.task.config.TaskRuntimeConfigService;
import com.actionow.task.constant.TaskConstants;
import com.actionow.common.core.id.UuidGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 流式生成服务实现
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamGenerationServiceImpl implements StreamGenerationService {

    private final AiFeignClient aiFeignClient;
    private final WalletFeignClient walletFeignClient;
    private final TaskMapper taskMapper;
    private final TaskService taskService;
    private final PointsTransactionManager pointsTransactionManager;
    private final WebClient.Builder webClientBuilder;
    private final InternalAuthProperties internalAuthProperties;
    private final TaskRuntimeConfigService taskRuntimeConfig;

    @Override
    public Flux<StreamGenerationEvent> streamGenerate(StreamGenerationRequest request,
                                                       String workspaceId, String userId) {
        log.info("开始流式生成: providerId={}, workspaceId={}, userId={}",
                request.getProviderId(), workspaceId, userId);

        // 1. 检查提供商是否支持流式
        if (!isStreamingSupported(request.getProviderId())) {
            return Flux.just(StreamGenerationEvent.error(null, null,
                    "STREAMING_NOT_SUPPORTED", "该提供商不支持流式响应"));
        }

        // 2. 获取提供商费用并冻结积分
        Result<AvailableProviderResponse> providerResult = aiFeignClient.getProviderDetail(request.getProviderId());
        if (!providerResult.isSuccess() || providerResult.getData() == null) {
            return Flux.just(StreamGenerationEvent.error(null, null,
                    "PROVIDER_NOT_FOUND", "模型提供商不存在"));
        }
        AvailableProviderResponse provider = providerResult.getData();
        long creditCost = estimateCreditCost(request.getProviderId(), request.getParams(), provider);

        // 3. 冻结积分
        String freezeBusinessId = StringUtils.hasText(request.getAssetId())
                ? request.getAssetId() : request.getProviderId();
        String transactionId = null;
        if (creditCost > 0) {
            FreezeRequest freezeReq = FreezeRequest.builder()
                    .workspaceId(workspaceId)
                    .operatorId(userId)
                    .amount(creditCost)
                    .businessType("AI_STREAM_GENERATION")
                    .businessId(freezeBusinessId)
                    .remark("流式生成任务")
                    .build();
            Result<FreezeResponse> freezeResult = walletFeignClient.freeze(workspaceId, freezeReq);
            if (!freezeResult.isSuccess() || freezeResult.getData() == null) {
                return Flux.just(StreamGenerationEvent.error(null, null,
                        "INSUFFICIENT_CREDITS", "积分不足"));
            }
            transactionId = freezeResult.getData().getTransactionId();
        }

        // 4. 创建任务记录
        String taskId = UuidGenerator.generateUuidV7();
        Task task = new Task();
        task.setId(taskId);
        task.setWorkspaceId(workspaceId);
        task.setType(TaskConstants.TaskType.IMAGE_GENERATION);
        task.setTitle("流式生成任务");
        task.setStatus(TaskConstants.TaskStatus.RUNNING);
        task.setProgress(0);
        task.setPriority(TaskConstants.Priority.NORMAL);
        task.setCreatorId(userId);
        if (StringUtils.hasText(request.getAssetId())) {
            task.setEntityId(request.getAssetId());
            task.setEntityType("ASSET");
        }

        Map<String, Object> inputParams = new HashMap<>();
        inputParams.put("providerId", request.getProviderId());
        inputParams.put("params", request.getParams());
        inputParams.put("transactionId", transactionId);
        inputParams.put("creditCost", creditCost);
        inputParams.put("freezeBusinessId", freezeBusinessId);
        task.setInputParams(inputParams);

        taskMapper.insert(task);

        // 5. 调用AI服务流式接口
        final String finalTransactionId = transactionId;
        final long finalCreditCost = creditCost;
        String executionId = UuidGenerator.generateUuidV7();

        return executeStreamRequest(request, taskId, executionId, provider)
                .doOnNext(event -> {
                    // 更新任务进度
                    if (event.getProgress() != null) {
                        taskService.updateProgress(taskId, event.getProgress());
                    }
                })
                .doOnComplete(() -> {
                    // 确认消费积分
                    if (finalTransactionId != null && finalCreditCost > 0) {
                        confirmConsume(workspaceId, userId, freezeBusinessId,
                                "AI_STREAM_GENERATION", finalCreditCost, taskId);
                    }
                    taskService.completeTask(taskId, Map.of("executionId", executionId));
                    log.info("流式生成完成: taskId={}", taskId);
                })
                .doOnError(e -> {
                    // 解冻积分
                    if (finalTransactionId != null) {
                        unfreezeCredits(workspaceId, userId, freezeBusinessId,
                                "AI_STREAM_GENERATION", taskId);
                    }
                    taskService.failTask(taskId, e.getMessage(), null);
                    log.error("流式生成失败: taskId={}, error={}", taskId, e.getMessage());
                })
                .onErrorResume(e -> Flux.just(
                        StreamGenerationEvent.error(taskId, executionId, "STREAM_ERROR", e.getMessage())
                ));
    }

    @Override
    public boolean isStreamingSupported(String providerId) {
        try {
            Result<AvailableProviderResponse> result = aiFeignClient.getProviderDetail(providerId);
            if (result.isSuccess() && result.getData() != null) {
                AvailableProviderResponse provider = result.getData();
                return Boolean.TRUE.equals(provider.getSupportsStreaming());
            }
            return false;
        } catch (Exception e) {
            log.warn("检查流式支持失败: providerId={}", providerId, e);
            return false;
        }
    }

    /**
     * 执行流式请求
     */
    private Flux<StreamGenerationEvent> executeStreamRequest(StreamGenerationRequest request,
                                                              String taskId, String executionId,
                                                              AvailableProviderResponse provider) {
        // 构建请求体 - 直接使用 params
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("providerId", request.getProviderId());
        requestBody.put("taskId", taskId);
        requestBody.put("params", request.getParams());
        requestBody.put("responseMode", "STREAMING");

        WebClient webClient = webClientBuilder.baseUrl(taskRuntimeConfig.getAiServiceUrl()).build();

        // 获取当前用户上下文，用于传递认证头
        String workspaceId = UserContextHolder.getWorkspaceId();
        String tenantSchema = UserContextHolder.getTenantSchema();
        String userId = UserContextHolder.getUserId();

        // 发送开始事件
        Flux<StreamGenerationEvent> startEvent = Flux.just(
                StreamGenerationEvent.started(taskId, executionId)
        );

        // 调用AI服务流式接口
        Flux<StreamGenerationEvent> aiEvents = webClient.post()
                .uri("/internal/ai/provider/execute/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    // 传递用户上下文头
                    if (workspaceId != null) headers.set(CommonConstants.HEADER_WORKSPACE_ID, workspaceId);
                    if (tenantSchema != null) headers.set(CommonConstants.HEADER_TENANT_SCHEMA, tenantSchema);
                    if (userId != null) headers.set(CommonConstants.HEADER_USER_ID, userId);
                    if (!internalAuthProperties.isConfigured()) {
                        throw new IllegalStateException("Internal auth secret must be configured for stream call");
                    }
                    String internalToken = InternalAuthUtils.generateInternalToken(
                            internalAuthProperties.getAuthSecret(),
                            userId,
                            workspaceId,
                            tenantSchema,
                            internalAuthProperties.getInternalTokenExpireSeconds()
                    );
                    headers.set(CommonConstants.HEADER_INTERNAL_AUTH_TOKEN, internalToken);
                })
                .bodyValue(requestBody)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<Map<String, Object>>>() {})
                .timeout(Duration.ofMinutes(10))
                .filter(sse -> sse.data() != null)
                .map(sse -> convertToStreamEvent(sse, taskId, executionId));

        return startEvent.concatWith(aiEvents);
    }

    /**
     * 转换SSE事件
     */
    private StreamGenerationEvent convertToStreamEvent(ServerSentEvent<Map<String, Object>> sse,
                                                        String taskId, String executionId) {
        Map<String, Object> data = sse.data();
        if (data == null) {
            return StreamGenerationEvent.progress(taskId, executionId, 0, "处理中...");
        }

        String eventType = (String) data.get("eventType");
        if (eventType == null) {
            eventType = sse.event();
        }

        return StreamGenerationEvent.builder()
                .eventType(eventType)
                .taskId(taskId)
                .executionId(executionId)
                .progress((Integer) data.get("progress"))
                .currentStep((String) data.get("currentStep"))
                .textDelta((String) data.get("textDelta"))
                .textAccumulated((String) data.get("textAccumulated"))
                .status((String) data.get("status"))
                .outputs(castToMap(data.get("outputs")))
                .errorCode((String) data.get("errorCode"))
                .errorMessage((String) data.get("errorMessage"))
                .timestamp((String) data.get("timestamp"))
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castToMap(Object obj) {
        if (obj instanceof Map) {
            return (Map<String, Object>) obj;
        }
        return null;
    }

    /**
     * 预估积分消耗（动态定价）
     * 调用 AI 服务的 estimate-cost 接口计算实际积分，失败时回退到静态值
     */
    private long estimateCreditCost(String providerId, Map<String, Object> params,
                                     AvailableProviderResponse provider) {
        try {
            Result<Map<String, Object>> estimateResult = aiFeignClient.estimateCost(providerId, params);
            if (estimateResult.isSuccess() && estimateResult.getData() != null) {
                Object finalCost = estimateResult.getData().get("finalCost");
                if (finalCost instanceof Number) {
                    long cost = ((Number) finalCost).longValue();
                    log.debug("动态积分计算结果: providerId={}, finalCost={}, source={}",
                            providerId, cost, estimateResult.getData().get("source"));
                    return cost;
                }
            }
            log.warn("动态积分计算返回无效结果，回退到静态值: providerId={}", providerId);
        } catch (Exception e) {
            log.warn("动态积分计算失败，回退到静态值: providerId={}, error={}", providerId, e.getMessage());
        }
        return provider.getCreditCost() != null ? provider.getCreditCost() : 0L;
    }

    /**
     * 确认消费积分（使用 PointsTransactionManager，失败时自动创建补偿任务）
     */
    private void confirmConsume(String workspaceId, String userId, String businessId,
                                 String businessType, long amount, String taskId) {
        pointsTransactionManager.confirmConsumeAsync(
                workspaceId, userId, businessId, businessType, amount, "流式生成任务完成: " + taskId);
    }

    /**
     * 解冻积分（使用 PointsTransactionManager，失败时自动创建补偿任务）
     */
    private void unfreezeCredits(String workspaceId, String userId, String businessId,
                                  String businessType, String taskId) {
        pointsTransactionManager.unfreezePointsAsync(
                workspaceId, userId, businessId, businessType, "流式生成任务失败: " + taskId,
                null);
    }
}
