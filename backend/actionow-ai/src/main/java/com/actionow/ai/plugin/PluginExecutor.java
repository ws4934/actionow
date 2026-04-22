package com.actionow.ai.plugin;

import com.actionow.ai.plugin.handler.ResponseHandler;
import com.actionow.ai.plugin.handler.impl.*;
import com.actionow.ai.plugin.http.PluginHttpClient;
import com.actionow.ai.plugin.model.*;
import com.actionow.ai.plugin.polling.PollingManager;
import com.actionow.common.core.context.UserContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * 插件统一执行器
 * 协调插件执行、响应处理、轮询调度等
 *
 * Phase 1 重构：
 * - 移除内联轮询逻辑（4个分散的Map、自建调度器、清理线程）
 * - 所有轮询职责委托给 PollingManager（单一数据源）
 * - 保持公开API不变，确保向后兼容
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PluginExecutor {

    private final PluginRegistry pluginRegistry;
    private final PluginHttpClient httpClient;
    private final PollingManager pollingManager;

    // 响应处理器
    private final BlockingResponseHandler blockingHandler = new BlockingResponseHandler();
    private final StreamingResponseHandler streamingHandler = new StreamingResponseHandler();
    private final CallbackResponseHandler callbackHandler = new CallbackResponseHandler();
    private final PollingResponseHandler pollingHandler = new PollingResponseHandler();

    /**
     * 执行插件
     * 根据请求的响应模式选择合适的执行方式
     *
     * @param pluginId 插件ID
     * @param config 插件配置
     * @param request 执行请求
     * @return 执行结果
     */
    public PluginExecutionResult execute(String pluginId, PluginConfig config, PluginExecutionRequest request) {
        AiModelPlugin plugin = pluginRegistry.getPlugin(pluginId);

        // 生成执行ID
        if (request.getExecutionId() == null) {
            request.setExecutionId(UUID.randomUUID().toString());
        }

        ResponseMode mode = request.getResponseMode();
        if (mode == null) {
            mode = ResponseMode.BLOCKING;
        }

        // 检查插件是否支持该模式
        if (!plugin.supportsMode(mode)) {
            return PluginExecutionResult.failure(
                request.getExecutionId(),
                "UNSUPPORTED_MODE",
                "插件 " + pluginId + " 不支持 " + mode + " 模式"
            );
        }

        log.info("Executing plugin: {}, mode: {}, executionId: {}",
            pluginId, mode, request.getExecutionId());

        try {
            return switch (mode) {
                case BLOCKING -> executeBlocking(plugin, config, request);
                case CALLBACK -> executeCallback(plugin, config, request);
                case POLLING -> executePolling(plugin, config, request);
                default -> PluginExecutionResult.failure(
                    request.getExecutionId(),
                    "INVALID_MODE",
                    "不支持的响应模式: " + mode
                );
            };
        } catch (Exception e) {
            log.error("Plugin execution failed: {}", e.getMessage(), e);
            return PluginExecutionResult.failure(
                request.getExecutionId(),
                "EXECUTION_ERROR",
                e.getMessage()
            );
        }
    }

    /**
     * 执行流式请求
     *
     * @param pluginId 插件ID
     * @param config 插件配置
     * @param request 执行请求
     * @return 事件流
     */
    public Flux<PluginStreamEvent> executeStream(String pluginId, PluginConfig config, PluginExecutionRequest request) {
        AiModelPlugin plugin = pluginRegistry.getPlugin(pluginId);

        if (request.getExecutionId() == null) {
            request.setExecutionId(UUID.randomUUID().toString());
        }

        if (!plugin.supportsMode(ResponseMode.STREAMING)) {
            return Flux.error(new UnsupportedOperationException(
                "插件 " + pluginId + " 不支持流式模式"));
        }

        log.info("Executing streaming plugin: {}, executionId: {}",
            pluginId, request.getExecutionId());

        return plugin.executeStream(config, request)
            .doOnSubscribe(s -> log.debug("Stream started for execution: {}", request.getExecutionId()))
            .doOnComplete(() -> log.debug("Stream completed for execution: {}", request.getExecutionId()))
            .doOnError(e -> log.error("Stream error for execution {}: {}", request.getExecutionId(), e.getMessage()));
    }

    /**
     * 阻塞执行
     */
    private PluginExecutionResult executeBlocking(AiModelPlugin plugin, PluginConfig config,
                                                   PluginExecutionRequest request) {
        long startTime = System.currentTimeMillis();

        PluginExecutionResult result = plugin.execute(config, request);

        // 补充执行信息
        result.setExecutionId(request.getExecutionId());
        result.setProviderId(config.getProviderId());
        result.setResponseMode(ResponseMode.BLOCKING);
        result.setElapsedTimeMs(System.currentTimeMillis() - startTime);

        if (result.getStartedAt() == null) {
            result.setStartedAt(LocalDateTime.now().minusNanos(result.getElapsedTimeMs() * 1_000_000));
        }

        return result;
    }

    /**
     * 回调执行
     */
    private PluginExecutionResult executeCallback(AiModelPlugin plugin, PluginConfig config,
                                                   PluginExecutionRequest request) {
        // 提交异步任务
        PluginExecutionResult submitResult = plugin.submitAsync(config, request);

        submitResult.setExecutionId(request.getExecutionId());
        submitResult.setProviderId(config.getProviderId());
        submitResult.setResponseMode(ResponseMode.CALLBACK);

        return submitResult;
    }

    /**
     * 轮询执行 — 委托给 PollingManager
     */
    private PluginExecutionResult executePolling(AiModelPlugin plugin, PluginConfig config,
                                                  PluginExecutionRequest request) {
        // 提交异步任务
        PluginExecutionResult submitResult = plugin.submitAsync(config, request);

        if (submitResult.getStatus() == PluginExecutionResult.ExecutionStatus.FAILED) {
            return submitResult;
        }

        submitResult.setExecutionId(request.getExecutionId());
        submitResult.setProviderId(config.getProviderId());
        submitResult.setResponseMode(ResponseMode.POLLING);

        // 构建回调逻辑：轮询完成后发送 HTTP 回调
        String callbackUrl = request.getCallbackUrl();
        java.util.function.Consumer<PluginExecutionResult> onComplete = null;
        if (callbackUrl != null && !callbackUrl.isBlank()) {
            final String callbackUserId = (request.getUserId() != null && !request.getUserId().isBlank())
                    ? request.getUserId()
                    : UserContextHolder.getUserId();
            final String callbackWorkspaceId = (request.getWorkspaceId() != null && !request.getWorkspaceId().isBlank())
                    ? request.getWorkspaceId()
                    : UserContextHolder.getWorkspaceId();
            final String callbackTenantSchema = UserContextHolder.getTenantSchema();
            onComplete = result -> sendPollingCallback(
                    callbackUrl, result, callbackUserId, callbackWorkspaceId, callbackTenantSchema);
        }

        // 委托给 PollingManager 启动轮询
        pollingManager.startPolling(
            request.getExecutionId(),
            submitResult.getExternalTaskId(),
            submitResult.getExternalRunId(),
            plugin,
            config,
            callbackUrl,
            onComplete
        );

        return submitResult;
    }

    /**
     * 发送轮询完成回调
     */
    private void sendPollingCallback(String callbackUrl, PluginExecutionResult result,
                                     String userId, String workspaceId, String tenantSchema) {
        // 异步发送回调，避免阻塞轮询线程
        CompletableFuture.runAsync(() -> {
            try {
                log.info("Sending polling callback: url={}, status={}, executionId={}",
                    callbackUrl, result.getStatus(), result.getExecutionId());
                httpClient.postCallback(callbackUrl, result.toCallbackPayload(),
                        userId, workspaceId, tenantSchema);
                log.info("Polling callback sent successfully: url={}, executionId={}",
                    callbackUrl, result.getExecutionId());
            } catch (Exception e) {
                log.error("Failed to send polling callback: url={}, executionId={}, error={}",
                    callbackUrl, result.getExecutionId(), e.getMessage(), e);
            }
        });
    }

    /**
     * 获取轮询结果（阻塞等待）
     *
     * @param executionId 执行ID
     * @param timeout 超时时间
     * @param unit 时间单位
     * @return 执行结果
     */
    public PluginExecutionResult awaitPollingResult(String executionId, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return pollingManager.awaitResult(executionId, timeout, unit);
    }

    /**
     * 检查轮询任务是否还在执行中
     *
     * @param executionId 执行ID
     * @return true 表示正在轮询中
     */
    public boolean isPollingActive(String executionId) {
        return pollingManager.isActive(executionId);
    }

    /**
     * 获取轮询结果（如果已完成）
     * 非阻塞方式查询轮询结果
     *
     * @param executionId 执行ID
     * @return 如果任务已完成则返回结果，否则返回 null
     */
    public PluginExecutionResult getPollingResultIfReady(String executionId) {
        return pollingManager.getResultIfReady(executionId);
    }

    /**
     * 取消执行
     */
    public boolean cancel(String pluginId, String executionId, String externalTaskId, String userId,
                          PluginConfig config) {
        // 停止轮询任务
        pollingManager.stopPolling(executionId);

        // 调用插件取消
        try {
            AiModelPlugin plugin = pluginRegistry.getPlugin(pluginId);
            return plugin.cancel(config, externalTaskId, userId);
        } catch (Exception e) {
            log.error("Cancel failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 处理回调
     */
    public PluginExecutionResult handleCallback(String executionId, Object callbackData, PluginConfig config) {
        return callbackHandler.handleStatusResponse(callbackData, config);
    }

    /**
     * 获取响应处理器
     */
    public ResponseHandler getHandler(ResponseMode mode) {
        return switch (mode) {
            case BLOCKING -> blockingHandler;
            case STREAMING -> streamingHandler;
            case CALLBACK -> callbackHandler;
            case POLLING -> pollingHandler;
        };
    }
}
