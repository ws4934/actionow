package com.actionow.ai.plugin.impl;

import com.actionow.ai.plugin.AiModelPlugin;
import com.actionow.ai.plugin.auth.AuthConfig;
import com.actionow.ai.plugin.auth.AuthStrategyFactory;
import com.actionow.ai.plugin.auth.AuthenticationStrategy;
import com.actionow.ai.plugin.handler.ResponseHandler;
import com.actionow.ai.plugin.handler.impl.*;
import com.actionow.ai.plugin.http.PluginHttpClient;
import com.actionow.ai.plugin.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 抽象插件基类
 * 提供通用的执行逻辑和工具方法
 *
 * @author Actionow
 */
@Slf4j
public abstract class AbstractAiModelPlugin implements AiModelPlugin {

    protected final AuthStrategyFactory authStrategyFactory;
    protected final PluginHttpClient httpClient;

    // 响应处理器
    protected final BlockingResponseHandler blockingHandler = new BlockingResponseHandler();
    protected final StreamingResponseHandler streamingHandler = new StreamingResponseHandler();
    protected final CallbackResponseHandler callbackHandler = new CallbackResponseHandler();
    protected final PollingResponseHandler pollingHandler = new PollingResponseHandler();

    protected AbstractAiModelPlugin(AuthStrategyFactory authStrategyFactory, PluginHttpClient httpClient) {
        this.authStrategyFactory = authStrategyFactory;
        this.httpClient = httpClient;
    }

    @Override
    public PluginExecutionResult execute(PluginConfig config, PluginExecutionRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            // 构建请求体
            Object requestBody = buildRequestBody(config, request);

            // 执行HTTP请求
            Map<String, Object> rawResponse = httpClient.executeBlocking(config, requestBody);

            // 处理响应
            PluginExecutionResult result = blockingHandler.handleResponse(rawResponse, config);
            result.setExecutionId(request.getExecutionId());
            result.setProviderId(config.getProviderId());
            result.setResponseMode(ResponseMode.BLOCKING);
            result.setElapsedTimeMs(System.currentTimeMillis() - startTime);
            result.setStartedAt(LocalDateTime.now().minusNanos(result.getElapsedTimeMs() * 1_000_000));
            result.setCreditCost(config.getCreditCost());

            return result;

        } catch (Exception e) {
            log.error("Plugin execution failed: {}", e.getMessage(), e);
            return PluginExecutionResult.builder()
                .executionId(request.getExecutionId())
                .providerId(config.getProviderId())
                .status(PluginExecutionResult.ExecutionStatus.FAILED)
                .responseMode(ResponseMode.BLOCKING)
                .errorCode("EXECUTION_ERROR")
                .errorMessage(e.getMessage())
                .elapsedTimeMs(System.currentTimeMillis() - startTime)
                .completedAt(LocalDateTime.now())
                .build();
        }
    }

    @Override
    public Flux<PluginStreamEvent> executeStream(PluginConfig config, PluginExecutionRequest request) {
        if (!supportsMode(ResponseMode.STREAMING)) {
            return Flux.error(new UnsupportedOperationException(
                "Plugin does not support streaming mode: " + getPluginId()));
        }

        try {
            Object requestBody = buildStreamingRequestBody(config, request);

            Flux<String> eventFlux = httpClient.executeStreaming(config, requestBody);

            return streamingHandler.handleStreamResponse(eventFlux, config)
                .doOnNext(event -> {
                    event.setExecutionId(request.getExecutionId());
                });

        } catch (Exception e) {
            log.error("Streaming execution failed: {}", e.getMessage(), e);
            return Flux.just(PluginStreamEvent.error(request.getExecutionId(), "STREAM_ERROR", e.getMessage()));
        }
    }

    @Override
    public PluginExecutionResult submitAsync(PluginConfig config, PluginExecutionRequest request) {
        if (!supportsMode(ResponseMode.CALLBACK) && !supportsMode(ResponseMode.POLLING)) {
            throw new UnsupportedOperationException(
                "Plugin does not support async mode: " + getPluginId());
        }

        try {
            Object requestBody = buildAsyncRequestBody(config, request);

            Map<String, Object> submitResponse = httpClient.executeBlocking(config, requestBody);

            ResponseHandler.AsyncResponseHandler handler = request.getResponseMode() == ResponseMode.CALLBACK
                ? callbackHandler
                : pollingHandler;

            PluginExecutionResult result = handler.handleSubmitResponse(submitResponse, config);
            result.setExecutionId(request.getExecutionId());
            result.setProviderId(config.getProviderId());
            result.setResponseMode(request.getResponseMode());
            result.setSubmittedAt(LocalDateTime.now());

            return result;

        } catch (Exception e) {
            log.error("Async submit failed: {}", e.getMessage(), e);
            return PluginExecutionResult.failure(request.getExecutionId(), "SUBMIT_ERROR", e.getMessage());
        }
    }

    @Override
    public PluginExecutionResult pollStatus(PluginConfig config, String externalTaskId, String externalRunId) {
        if (!supportsMode(ResponseMode.POLLING)) {
            throw new UnsupportedOperationException(
                "Plugin does not support polling: " + getPluginId());
        }

        try {
            String pollEndpoint = buildPollEndpoint(config, externalTaskId, externalRunId);
            String pollMethod = resolvePollHttpMethod(config);
            Object pollBody = buildPollRequestBody(config, externalTaskId, externalRunId);

            Map<String, Object> statusResponse = httpClient.executePollRequest(
                config, pollEndpoint, pollMethod, pollBody);

            return pollingHandler.handleStatusResponse(statusResponse, config);

        } catch (Exception e) {
            log.error("Poll status failed: {}", e.getMessage(), e);

            // 判断是否为可重试错误（网络错误、超时等）
            if (isRetryableError(e)) {
                // 可重试错误返回 RUNNING 状态，让 PollingManager 继续轮询
                log.warn("Retryable error during polling, will continue: {}", e.getMessage());
                return PluginExecutionResult.builder()
                        .status(PluginExecutionResult.ExecutionStatus.RUNNING)
                        .errorCode("POLL_RETRY")
                        .errorMessage("轮询暂时失败，将重试: " + e.getMessage())
                        .build();
            }

            return PluginExecutionResult.failure(null, "POLL_ERROR", e.getMessage());
        }
    }

    /**
     * 判断是否为可重试错误
     * 网络超时、DNS解析失败、连接重置等都是可重试的
     */
    protected boolean isRetryableError(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            message = e.getClass().getSimpleName();
        }
        String lowerMessage = message.toLowerCase();

        // 网络相关的可重试错误
        return lowerMessage.contains("timeout") ||
               lowerMessage.contains("timed out") ||
               lowerMessage.contains("connection reset") ||
               lowerMessage.contains("connection refused") ||
               lowerMessage.contains("failed to resolve") ||
               lowerMessage.contains("dns") ||
               lowerMessage.contains("unreachable") ||
               lowerMessage.contains("temporarily unavailable") ||
               lowerMessage.contains("service unavailable") ||
               lowerMessage.contains("502") ||
               lowerMessage.contains("503") ||
               lowerMessage.contains("504") ||
               e instanceof java.net.SocketTimeoutException ||
               e instanceof java.net.UnknownHostException ||
               e instanceof java.net.ConnectException;
    }

    /**
     * 构建请求体
     * 子类可重写以实现特定的请求格式
     */
    protected Object buildRequestBody(PluginConfig config, PluginExecutionRequest request) {
        if (request.isUseRawBody() && request.getRawRequestBody() != null) {
            return request.getRawRequestBody();
        }

        return applyRequestMapping(config, request.getInputs());
    }

    /**
     * 构建流式请求体
     */
    protected Object buildStreamingRequestBody(PluginConfig config, PluginExecutionRequest request) {
        return buildRequestBody(config, request);
    }

    /**
     * 构建异步请求体
     */
    protected Object buildAsyncRequestBody(PluginConfig config, PluginExecutionRequest request) {
        Map<String, Object> body = new HashMap<>();
        if (buildRequestBody(config, request) instanceof Map) {
            body.putAll((Map<String, Object>) buildRequestBody(config, request));
        }

        // 添加回调URL
        if (request.getResponseMode() == ResponseMode.CALLBACK && request.getCallbackUrl() != null) {
            body.put("callback_url", request.getCallbackUrl());
            if (request.getCallbackMetadata() != null) {
                body.put("callback_metadata", request.getCallbackMetadata());
            }
        }

        return body;
    }

    /**
     * 构建轮询端点
     * 支持占位符: {taskId}, {task_id}, {runId}, {run_id}
     */
    protected String buildPollEndpoint(PluginConfig config, String externalTaskId, String externalRunId) {
        PluginConfig.PollingConfig pollingConfig = config.getPollingConfig();
        log.debug("[buildPollEndpoint] pollingConfig={}, endpoint={}",
                pollingConfig != null, pollingConfig != null ? pollingConfig.getEndpoint() : "null");

        if (pollingConfig != null && StringUtils.hasText(pollingConfig.getEndpoint())) {
            String endpoint = pollingConfig.getEndpoint();
            // 支持多种占位符格式
            endpoint = replacePollPlaceholders(endpoint, externalTaskId, externalRunId);
            log.info("[buildPollEndpoint] Using configured endpoint: {}", endpoint);
            return endpoint;
        }
        String fallbackEndpoint = "/tasks/" + externalTaskId;
        log.warn("[buildPollEndpoint] Using fallback endpoint (no pollingConfig.endpoint configured): {}", fallbackEndpoint);
        return fallbackEndpoint;
    }

    /**
     * 解析轮询HTTP方法
     * 默认GET，可在 pollingConfig.httpMethod 中配置为 POST
     */
    protected String resolvePollHttpMethod(PluginConfig config) {
        PluginConfig.PollingConfig pollingConfig = config.getPollingConfig();
        if (pollingConfig != null && StringUtils.hasText(pollingConfig.getHttpMethod())) {
            return pollingConfig.getHttpMethod().toUpperCase();
        }
        return "GET";
    }

    /**
     * 构建轮询请求体（仅 POST 轮询时使用）
     * 根据 pollingConfig.requestBodyTemplate 构建，支持占位符替换
     *
     * @return 请求体 Map，GET 方式返回 null
     */
    protected Object buildPollRequestBody(PluginConfig config, String externalTaskId, String externalRunId) {
        PluginConfig.PollingConfig pollingConfig = config.getPollingConfig();
        if (pollingConfig == null || !"POST".equalsIgnoreCase(pollingConfig.getHttpMethod())) {
            return null;
        }

        Map<String, Object> template = pollingConfig.getRequestBodyTemplate();
        if (template == null || template.isEmpty()) {
            // POST 但无模板时，使用默认请求体
            Map<String, Object> defaultBody = new HashMap<>();
            defaultBody.put("task_id", externalTaskId);
            if (externalRunId != null) {
                defaultBody.put("run_id", externalRunId);
            }
            return defaultBody;
        }

        // 深拷贝并替换占位符
        return replacePollPlaceholdersInMap(template, externalTaskId, externalRunId);
    }

    /**
     * 替换轮询占位符
     */
    protected String replacePollPlaceholders(String text, String externalTaskId, String externalRunId) {
        if (text == null) return null;
        return text
            .replace("{taskId}", externalTaskId != null ? externalTaskId : "")
            .replace("{task_id}", externalTaskId != null ? externalTaskId : "")
            .replace("{runId}", externalRunId != null ? externalRunId : "")
            .replace("{run_id}", externalRunId != null ? externalRunId : "");
    }

    /**
     * 递归替换 Map 中的占位符
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> replacePollPlaceholdersInMap(Map<String, Object> template,
                                                              String externalTaskId,
                                                              String externalRunId) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : template.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String str) {
                result.put(entry.getKey(), replacePollPlaceholders(str, externalTaskId, externalRunId));
            } else if (value instanceof Map) {
                result.put(entry.getKey(),
                    replacePollPlaceholdersInMap((Map<String, Object>) value, externalTaskId, externalRunId));
            } else {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }

    /**
     * 应用请求映射
     * 基础实现直接返回输入，子类可重写以实现自定义逻辑
     */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> applyRequestMapping(PluginConfig config, Map<String, Object> inputs) {
        // 基础实现：直接返回inputs，Groovy插件会重写此方法
        if (inputs == null) {
            return new HashMap<>();
        }
        return new HashMap<>(inputs);
    }

    /**
     * 获取响应处理器
     */
    protected ResponseHandler getHandler(ResponseMode mode) {
        return switch (mode) {
            case BLOCKING -> blockingHandler;
            case STREAMING -> streamingHandler;
            case CALLBACK -> callbackHandler;
            case POLLING -> pollingHandler;
        };
    }

    /**
     * 构建认证头
     */
    protected void applyAuth(HttpHeaders headers, PluginConfig config) {
        if (StringUtils.hasText(config.getAuthType()) && config.getAuthConfig() != null) {
            AuthenticationStrategy strategy = authStrategyFactory.getStrategy(config.getAuthType());
            AuthConfig authConfig = convertToAuthConfig(config.getAuthConfig(), config.getAuthType());
            strategy.applyAuth(headers, authConfig);
        }
    }

    @SuppressWarnings("unchecked")
    protected AuthConfig convertToAuthConfig(Map<String, Object> configMap, String authType) {
        AuthConfig.AuthConfigBuilder builder = AuthConfig.builder().authType(authType);

        if (configMap.containsKey("apiKey")) {
            builder.apiKey((String) configMap.get("apiKey"));
        }
        if (configMap.containsKey("accessKey")) {
            builder.accessKey((String) configMap.get("accessKey"));
        }
        if (configMap.containsKey("secretKey")) {
            builder.secretKey((String) configMap.get("secretKey"));
        }
        if (configMap.containsKey("bearerToken")) {
            builder.bearerToken((String) configMap.get("bearerToken"));
        }
        if (configMap.containsKey("customHeaders")) {
            builder.customHeaders((Map<String, String>) configMap.get("customHeaders"));
        }

        return builder.build();
    }
}
