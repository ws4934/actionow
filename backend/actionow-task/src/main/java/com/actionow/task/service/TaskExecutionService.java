package com.actionow.task.service;

import com.actionow.common.api.ai.ProviderExecuteRequest;
import com.actionow.common.core.result.Result;
import com.actionow.common.mq.constant.MqConstants;
import com.actionow.common.mq.message.MessageWrapper;
import com.actionow.common.mq.outbox.TransactionalMessageProducer;
import com.actionow.task.config.TaskRuntimeConfigService;
import com.actionow.task.constant.TaskConstants;
import com.actionow.task.dto.AvailableProviderResponse;
import com.actionow.task.dto.ProviderExecutionResult;
import com.actionow.task.dto.TaskResponse;
import com.actionow.task.entity.Task;
import com.actionow.task.feign.AiFeignClient;
import com.actionow.task.mapper.TaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * AI 任务执行服务
 * 从 AiGenerationOrchestrator 抽取，负责：
 * - 任务执行（MQ 消费者调用）
 * - 乐观锁竞争处理
 * - 响应模式解析
 * - 异步挂起状态管理
 *
 * @author Actionow
 */
@Slf4j
@Service
public class TaskExecutionService {

    private static final int MAX_OL_RETRY = 3;
    private static final String OL_RETRY_KEY = "_olRetryCount";

    private final TaskMapper taskMapper;
    private final AiFeignClient aiFeignClient;
    private final TaskRuntimeConfigService runtimeConfig;
    private final TaskCompletionHandler completionHandler;
    private final TransactionalMessageProducer transactionalMessageProducer;

    public TaskExecutionService(TaskMapper taskMapper,
                                AiFeignClient aiFeignClient,
                                TaskRuntimeConfigService runtimeConfig,
                                @Lazy TaskCompletionHandler completionHandler,
                                TransactionalMessageProducer transactionalMessageProducer) {
        this.taskMapper = taskMapper;
        this.aiFeignClient = aiFeignClient;
        this.runtimeConfig = runtimeConfig;
        this.completionHandler = completionHandler;
        this.transactionalMessageProducer = transactionalMessageProducer;
    }

    @Value("${actionow.task.generation.callback-base-url:http://actionow-task:8087}")
    private String callbackBaseUrl;

    /**
     * 执行 AI 任务（由 MQ 消费者调用）
     */
    @Transactional(rollbackFor = Exception.class)
    public void executeTask(Task task) {
        log.info("执行 AI 任务: taskId={}", task.getId());

        try {
            // 更新任务状态为运行中（乐观锁保证幂等，防止重复执行）
            LocalDateTime startedAt = LocalDateTime.now();
            task.setStatus(TaskConstants.TaskStatus.RUNNING);
            task.setStartedAt(startedAt);
            task.setTimeoutAt(calculateTimeoutAt(startedAt, task.getTimeoutSeconds()));
            int updated = taskMapper.updateById(task);
            if (updated == 0) {
                // 乐观锁冲突处理（带重试上限）
                Task freshTask = taskMapper.selectById(task.getId());
                if (freshTask != null && TaskConstants.TaskStatus.PENDING.equals(freshTask.getStatus())) {
                    int olRetryCount = getOlRetryCount(freshTask);
                    if (olRetryCount >= MAX_OL_RETRY) {
                        log.error("乐观锁冲突重试已达上限（{}次），任务标记为失败: taskId={}",
                                MAX_OL_RETRY, task.getId());
                        completionHandler.onFailure(freshTask, "OPTIMISTIC_LOCK_EXHAUSTED",
                                "乐观锁冲突重试超过 " + MAX_OL_RETRY + " 次",
                                Map.of("olRetryCount", olRetryCount));
                        return;
                    }
                    incrementOlRetryCount(freshTask);
                    log.warn("乐观锁冲突，重新入队（第{}次重试）: taskId={}", olRetryCount + 1, task.getId());
                    sendTaskToQueue(freshTask);
                } else {
                    log.warn("任务状态更新失败（已被其他线程处理），跳过执行: taskId={}", task.getId());
                }
                return;
            }

            // 从 inputParams 获取执行参数
            Map<String, Object> inputParams = task.getInputParams();
            if (inputParams == null) {
                completionHandler.onFailure(task, null, "任务输入参数为空（inputParams=null）",
                        Map.of("taskId", task.getId()));
                return;
            }
            String providerId = inputParams.get("providerId") instanceof String s ? s : null;
            if (!StringUtils.hasText(providerId)) {
                completionHandler.onFailure(task, null, "任务缺少 providerId 参数",
                        Map.of("taskId", task.getId(), "inputParamKeys", inputParams.keySet().toString()));
                return;
            }
            String requestedResponseMode = inputParams.get("responseMode") instanceof String s ? s : null;
            @SuppressWarnings("unchecked")
            Map<String, Object> params = inputParams.get("params") instanceof Map<?, ?> m
                    ? (Map<String, Object>) m : null;

            // 根据 Provider 实际支持的模式选择最优响应模式
            String responseMode = resolveResponseMode(providerId, requestedResponseMode);

            Map<String, Object> allParams = params != null ? new HashMap<>(params) : new HashMap<>();

            // 构建模型提供商执行请求
            ProviderExecuteRequest.ProviderExecuteRequestBuilder requestBuilder = ProviderExecuteRequest.builder()
                    .providerId(providerId)
                    .taskId(task.getId())
                    .params(allParams)
                    .responseMode(responseMode);

            if (("CALLBACK".equals(responseMode) || "POLLING".equals(responseMode))
                    && StringUtils.hasText(callbackBaseUrl)) {
                requestBuilder.callbackUrl(callbackBaseUrl + "/internal/task/" + task.getId() + "/callback");
            }

            ProviderExecuteRequest executeRequest = requestBuilder.build();

            // 调用 AI 服务执行模型提供商
            Result<ProviderExecutionResult> result = aiFeignClient.executeProvider(executeRequest);

            if (result.isSuccess() && result.getData() != null) {
                ProviderExecutionResult execResult = result.getData();

                if (execResult.isAsyncMode() && execResult.isPending()) {
                    handleAsyncPendingResult(task, execResult);
                } else if (!execResult.isSuccess() || "FAILED".equals(execResult.getStatus())) {
                    // 执行结果本身标识失败（HTTP 200 但业务失败）
                    completionHandler.onFailure(task, execResult.getErrorCode(),
                            execResult.getErrorMessage() != null ? execResult.getErrorMessage() : "AI 执行失败",
                            execResult.toMap());
                } else {
                    completionHandler.onSuccess(task, execResult);
                }
            } else {
                String errorMsg = result.getMessage() != null ? result.getMessage() : "AI 服务执行失败";
                completionHandler.onFailure(task, null, errorMsg, Map.of("code", result.getCode()));
            }

        } catch (Exception e) {
            log.error("AI 任务执行异常: taskId={}", task.getId(), e);
            completionHandler.onFailure(task, null, "任务执行异常: " + e.getMessage(),
                    Map.of("exception", e.getClass().getName(), "message", e.getMessage()));
        }
    }

    /**
     * 映射生成类型到任务类型
     */
    public String mapGenerationTypeToTaskType(String generationType) {
        if (generationType == null) {
            return TaskConstants.TaskType.IMAGE_GENERATION;
        }
        return switch (generationType.toUpperCase()) {
            case "IMAGE" -> TaskConstants.TaskType.IMAGE_GENERATION;
            case "VIDEO" -> TaskConstants.TaskType.VIDEO_GENERATION;
            case "TEXT" -> TaskConstants.TaskType.TEXT_GENERATION;
            case "AUDIO" -> TaskConstants.TaskType.AUDIO_GENERATION;
            case "TTS" -> TaskConstants.TaskType.TTS_GENERATION;
            default -> TaskConstants.TaskType.IMAGE_GENERATION;
        };
    }

    /**
     * 根据 Provider 实际支持的模式选择最优响应模式（适用于 MQ 异步任务路径）
     * 优先级: BLOCKING > CALLBACK > POLLING
     * STREAMING 不适用于 MQ 异步执行路径
     */
    public String resolveResponseMode(String providerId, String requestedResponseMode) {
        AvailableProviderResponse provider = null;
        try {
            Result<AvailableProviderResponse> providerResult = aiFeignClient.getProviderDetail(providerId);
            if (providerResult.isSuccess() && providerResult.getData() != null) {
                provider = providerResult.getData();
            }
        } catch (Exception e) {
            log.warn("查询 Provider 详情失败，将使用请求指定的模式: providerId={}, error={}", providerId, e.getMessage());
        }

        if (provider == null) {
            return StringUtils.hasText(requestedResponseMode) ? requestedResponseMode : "POLLING";
        }

        if (StringUtils.hasText(requestedResponseMode)
                && !"STREAMING".equalsIgnoreCase(requestedResponseMode)
                && isResponseModeSupported(provider, requestedResponseMode)) {
            return requestedResponseMode;
        }

        if (Boolean.TRUE.equals(provider.getSupportsBlocking())) {
            return "BLOCKING";
        }
        if (Boolean.TRUE.equals(provider.getSupportsCallback())) {
            return "CALLBACK";
        }
        if (Boolean.TRUE.equals(provider.getSupportsPolling())) {
            return "POLLING";
        }

        log.warn("Provider {} 未声明支持的响应模式，使用 POLLING 兜底", providerId);
        return "POLLING";
    }

    // ==================== 私有方法 ====================

    private int getOlRetryCount(Task task) {
        Map<String, Object> params = task.getInputParams();
        if (params == null) return 0;
        Object count = params.get(OL_RETRY_KEY);
        return count instanceof Number n ? n.intValue() : 0;
    }

    private void incrementOlRetryCount(Task task) {
        int current = getOlRetryCount(task);
        taskMapper.patchInputParams(task.getId(),
                "{\"" + OL_RETRY_KEY + "\": " + (current + 1) + "}");
    }

    /**
     * 发送任务到队列（事务性 Outbox 模式）
     */
    void sendTaskToQueue(Task task) {
        MessageWrapper<TaskResponse> message = MessageWrapper.wrap(
                MqConstants.Task.MSG_CREATED,
                TaskResponse.fromEntity(task)
        );
        transactionalMessageProducer.sendInTransaction(
                MqConstants.EXCHANGE_DIRECT, MqConstants.Task.ROUTING_CREATED, message
        );
    }

    private void handleAsyncPendingResult(Task task, ProviderExecutionResult result) {
        log.info("异步任务已提交，等待后续回调/轮询: taskId={}, executionId={}, externalTaskId={}, mode={}",
                task.getId(), result.getExecutionId(), result.getExternalTaskId(), result.getResponseMode());

        Map<String, Object> inputParams = task.getInputParams();
        if (inputParams == null) {
            inputParams = new HashMap<>();
        }
        inputParams.put("executionId", result.getExecutionId());
        inputParams.put("externalTaskId", result.getExternalTaskId());
        inputParams.put("externalRunId", result.getExternalRunId());
        inputParams.put("actualResponseMode", result.getResponseMode());
        task.setInputParams(inputParams);

        taskMapper.updateById(task);
    }

    private LocalDateTime calculateTimeoutAt(LocalDateTime startedAt, Integer timeoutSeconds) {
        int timeout = timeoutSeconds != null && timeoutSeconds > 0
                ? timeoutSeconds
                : runtimeConfig.getDefaultTimeoutSeconds();
        return startedAt.plusSeconds(timeout);
    }

    private boolean isResponseModeSupported(AvailableProviderResponse provider, String responseMode) {
        return switch (responseMode.toUpperCase()) {
            case "BLOCKING" -> Boolean.TRUE.equals(provider.getSupportsBlocking());
            case "STREAMING" -> Boolean.TRUE.equals(provider.getSupportsStreaming());
            case "CALLBACK" -> Boolean.TRUE.equals(provider.getSupportsCallback());
            case "POLLING" -> Boolean.TRUE.equals(provider.getSupportsPolling());
            default -> false;
        };
    }
}
