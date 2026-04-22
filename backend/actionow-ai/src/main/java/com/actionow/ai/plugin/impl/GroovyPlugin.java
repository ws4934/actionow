package com.actionow.ai.plugin.impl;

import com.actionow.ai.plugin.auth.AuthStrategyFactory;
import com.actionow.ai.plugin.groovy.GroovyRequestBuilder;
import com.actionow.ai.plugin.groovy.GroovyResponseMapper;
import com.actionow.ai.plugin.groovy.GroovyScriptContext;
import com.actionow.ai.plugin.groovy.GroovyScriptEngine;
import com.actionow.ai.plugin.groovy.binding.BindingFactory;
import com.actionow.ai.plugin.groovy.binding.RequestHelper;
import com.actionow.ai.plugin.groovy.binding.ResponseHelper;
import com.actionow.ai.plugin.http.PluginHttpClient;
import com.actionow.ai.plugin.model.*;
import com.actionow.common.core.context.UserContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Groovy脚本驱动的通用插件
 * 使用Groovy脚本处理请求构建、响应映射和自定义逻辑
 *
 * @author Actionow
 */
@Slf4j
public class GroovyPlugin extends AbstractAiModelPlugin {

    private static final String PLUGIN_ID = "groovy";
    private static final String PLUGIN_NAME = "Groovy Script Plugin";
    private static final String PLUGIN_VERSION = "1.0.0";

    private final GroovyScriptEngine scriptEngine;
    private final GroovyRequestBuilder requestBuilder;
    private final GroovyResponseMapper responseMapper;
    private final BindingFactory bindingFactory;

    public GroovyPlugin(AuthStrategyFactory authStrategyFactory,
                        PluginHttpClient httpClient,
                        GroovyScriptEngine scriptEngine,
                        GroovyRequestBuilder requestBuilder,
                        GroovyResponseMapper responseMapper,
                        BindingFactory bindingFactory) {
        super(authStrategyFactory, httpClient);
        this.scriptEngine = scriptEngine;
        this.requestBuilder = requestBuilder;
        this.responseMapper = responseMapper;
        this.bindingFactory = bindingFactory;
    }

    @Override
    public String getPluginId() {
        return PLUGIN_ID;
    }

    @Override
    public String getPluginName() {
        return PLUGIN_NAME;
    }

    @Override
    public String getVersion() {
        return PLUGIN_VERSION;
    }

    @Override
    public Set<ResponseMode> getSupportedModes() {
        return Set.of(
            ResponseMode.BLOCKING,
            ResponseMode.STREAMING,
            ResponseMode.CALLBACK,
            ResponseMode.POLLING
        );
    }

    @Override
    public Set<String> getSupportedTypes() {
        // Groovy插件支持所有生成类型
        return Set.of("IMAGE", "VIDEO", "AUDIO", "TEXT", "ALL");
    }

    @Override
    public boolean supportsMode(ResponseMode mode) {
        return getSupportedModes().contains(mode);
    }

    @Override
    public PluginExecutionResult execute(PluginConfig config, PluginExecutionRequest request) {
        LocalDateTime startedAt = LocalDateTime.now();
        long startTime = System.currentTimeMillis();
        String executionId = request.getExecutionId();

        log.info("[GroovyPlugin] ========== 开始执行 ==========");
        log.info("[GroovyPlugin] executionId={}, providerId={}, providerName={}",
            executionId, config.getProviderId(), config.getProviderName());

        try {
            // 执行自定义前置逻辑（如果有）
            executeCustomLogic(config, request, "before");

            // TEXT 类型：脚本直接调用 LLM，不走 HTTP
            if ("TEXT".equalsIgnoreCase(config.getProviderType())) {
                return executeTextGeneration(config, request, startedAt, startTime);
            }

            // 使用RequestBuilder构建请求体
            Object requestBody = requestBuilder.buildRequestBody(config, request);

            // 执行HTTP请求
            Map<String, Object> rawResponse = httpClient.executeBlocking(config, requestBody);

            // 使用ResponseMapper映射响应
            Map<String, Object> mappedResponse = responseMapper.mapResponse(config, rawResponse, request);

            // 执行自定义后置逻辑（如果有）
            mappedResponse = executeCustomLogic(config, request, "after", mappedResponse);

            // 构建执行结果
            PluginExecutionResult result = responseMapper.buildExecutionResult(mappedResponse, config);
            result.setExecutionId(request.getExecutionId());
            result.setProviderId(config.getProviderId());
            result.setResponseMode(ResponseMode.BLOCKING);
            result.setElapsedTimeMs(System.currentTimeMillis() - startTime);
            result.setStartedAt(startedAt);
            result.setCompletedAt(LocalDateTime.now());
            result.setCreditCost(config.getCreditCost());

            log.info("[GroovyPlugin] ========== 执行完成 ==========");
            log.info("[GroovyPlugin] executionId={}, status={}, 总耗时={}ms",
                executionId, result.getStatus(), result.getElapsedTimeMs());

            return result;

        } catch (Exception e) {
            log.error("[GroovyPlugin] ========== 执行失败 ==========");
            log.error("[GroovyPlugin] executionId={}, error={}", executionId, e.getMessage(), e);
            return PluginExecutionResult.builder()
                .executionId(request.getExecutionId())
                .providerId(config.getProviderId())
                .status(PluginExecutionResult.ExecutionStatus.FAILED)
                .responseMode(ResponseMode.BLOCKING)
                .errorCode("GROOVY_EXECUTION_ERROR")
                .errorMessage(e.getMessage())
                .elapsedTimeMs(System.currentTimeMillis() - startTime)
                .startedAt(startedAt)
                .completedAt(LocalDateTime.now())
                .build();
        }
    }

    @Override
    public Flux<PluginStreamEvent> executeStream(PluginConfig config, PluginExecutionRequest request) {
        if (!supportsMode(ResponseMode.STREAMING)) {
            return Flux.error(new UnsupportedOperationException(
                "Groovy plugin does not support streaming mode"));
        }

        // TEXT 类型：使用完整绑定执行脚本，将结果转为流式事件
        if ("TEXT".equalsIgnoreCase(config.getProviderType())) {
            return executeTextStream(config, request);
        }

        try {
            // 使用RequestBuilder构建请求体
            Object requestBody = requestBuilder.buildRequestBody(config, request);

            // 执行流式HTTP请求
            Flux<String> eventFlux = httpClient.executeStreaming(config, requestBody);

            return streamingHandler.handleStreamResponse(eventFlux, config)
                .doOnNext(event -> event.setExecutionId(request.getExecutionId()));

        } catch (Exception e) {
            log.error("Groovy streaming execution failed: {}", e.getMessage(), e);
            return Flux.just(PluginStreamEvent.error(request.getExecutionId(), "STREAM_ERROR", e.getMessage()));
        }
    }

    /**
     * TEXT 类型的流式执行：使用完整绑定执行脚本，将结果封装为流式事件序列
     */
    @SuppressWarnings("unchecked")
    private Flux<PluginStreamEvent> executeTextStream(PluginConfig config, PluginExecutionRequest request) {
        String executionId = request.getExecutionId();
        return Flux.defer(() -> {
            try {
                // 使用 executeTextGeneration 的完整逻辑
                LocalDateTime startedAt = LocalDateTime.now();
                long startTime = System.currentTimeMillis();
                PluginExecutionResult result = executeTextGeneration(config, request, startedAt, startTime);

                // 将结果转为流式事件
                if (result.getStatus() == PluginExecutionResult.ExecutionStatus.SUCCEEDED) {
                    Map<String, Object> data = new HashMap<>();
                    if (result.getOutputs() != null) data.putAll(result.getOutputs());
                    data.put("status", "SUCCEEDED");
                    data.put("elapsedTimeMs", result.getElapsedTimeMs());

                    String textContent = result.getTextContent();
                    PluginStreamEvent finishEvent = PluginStreamEvent.finished(executionId, data);
                    if (textContent != null) {
                        finishEvent.setTextDelta(textContent);
                        finishEvent.setTextAccumulated(textContent);
                    }

                    return Flux.just(finishEvent);
                } else {
                    return Flux.just(PluginStreamEvent.error(
                            executionId,
                            result.getErrorCode() != null ? result.getErrorCode() : "TEXT_EXECUTION_FAILED",
                            result.getErrorMessage() != null ? result.getErrorMessage() : "TEXT 执行失败"
                    ));
                }
            } catch (Exception e) {
                log.error("[GroovyPlugin] TEXT stream execution failed: {}", e.getMessage(), e);
                return Flux.just(PluginStreamEvent.error(executionId, "TEXT_STREAM_ERROR", e.getMessage()));
            }
        });
    }

    @Override
    public PluginExecutionResult submitAsync(PluginConfig config, PluginExecutionRequest request) {
        if (!supportsMode(ResponseMode.CALLBACK) && !supportsMode(ResponseMode.POLLING)) {
            throw new UnsupportedOperationException("Groovy plugin does not support async mode");
        }

        try {
            // 使用RequestBuilder构建异步请求体（含回调信息）
            Object requestBody = requestBuilder.buildAsyncRequestBody(config, request);

            // 执行提交请求
            Map<String, Object> submitResponse = httpClient.executeBlocking(config, requestBody);

            // 使用ResponseMapper映射响应
            Map<String, Object> mappedResponse = responseMapper.mapResponse(config, submitResponse, request);

            // 构建执行结果
            PluginExecutionResult result = responseMapper.buildAsyncSubmitResult(mappedResponse, config);
            result.setExecutionId(request.getExecutionId());
            result.setProviderId(config.getProviderId());
            result.setResponseMode(request.getResponseMode());
            result.setSubmittedAt(LocalDateTime.now());

            return result;

        } catch (Exception e) {
            log.error("Groovy async submit failed: {}", e.getMessage(), e);
            return PluginExecutionResult.failure(request.getExecutionId(), "SUBMIT_ERROR", e.getMessage());
        }
    }

    @Override
    public PluginExecutionResult pollStatus(PluginConfig config, String externalTaskId, String externalRunId) {
        if (!supportsMode(ResponseMode.POLLING)) {
            throw new UnsupportedOperationException("Groovy plugin does not support polling");
        }

        try {
            String pollEndpoint = buildPollEndpoint(config, externalTaskId, externalRunId);
            String pollMethod = resolvePollHttpMethod(config);
            Object pollBody = buildPollRequestBody(config, externalTaskId, externalRunId);

            Map<String, Object> statusResponse = httpClient.executePollRequest(
                config, pollEndpoint, pollMethod, pollBody);

            // 使用ResponseMapper映射轮询响应
            Map<String, Object> mappedResponse = responseMapper.mapPollResponse(
                config, statusResponse, externalTaskId, externalRunId);

            PluginExecutionResult result = responseMapper.buildPollResult(mappedResponse, config);

            return result;

        } catch (Exception e) {
            log.error("Groovy poll status failed: providerId={}, externalTaskId={}, error={}",
                    config.getProviderId(), externalTaskId, e.getMessage(), e);

            // 判断是否为可重试错误（网络错误、超时等）
            // 使用父类的 isRetryableError 方法
            if (isRetryableError(e)) {
                // 可重试错误返回 RUNNING 状态，让 PollingManager 继续轮询
                log.warn("Retryable error during polling, will continue: {}", e.getMessage());
                return PluginExecutionResult.builder()
                        .status(PluginExecutionResult.ExecutionStatus.RUNNING)
                        .errorCode("POLL_RETRY")
                        .errorMessage("轮询暂时失败，将重试: " + e.getMessage())
                        .build();
            }

            // 不可重试错误返回 FAILED 状态
            return PluginExecutionResult.failure(null, "POLL_ERROR", e.getMessage());
        }
    }

    /**
     * TEXT 类型执行：脚本内部通过 llm.chat() 调用 LLM，不走 HTTP
     */
    @SuppressWarnings("unchecked")
    private PluginExecutionResult executeTextGeneration(PluginConfig config,
                                                        PluginExecutionRequest request,
                                                        LocalDateTime startedAt, long startTime) {
        String executionId = request.getExecutionId();
        log.info("[GroovyPlugin] TEXT mode: executionId={}, llmProviderId={}",
                executionId, config.getLlmProviderId());

        // 1. 创建完整的绑定上下文（含 llm, db, asset 等）
        BindingFactory.BindingContext bindingContext = BindingFactory.BindingContext.builder()
                .workspaceId(request.getWorkspaceId())
                .userId(request.getUserId())
                .executionId(request.getExecutionId())
                .providerId(config.getProviderId())
                .tenantSchema(UserContextHolder.getTenantSchema())
                .defaultLlmProviderId(config.getLlmProviderId())
                .defaultSystemPrompt(config.getSystemPrompt())
                .defaultResponseSchema(config.getResponseSchema())
                .requiresDbAccess(true)
                .requiresOss(true)
                .requiresNotify(true)
                .build();

        BindingFactory.BindingHolder holder = bindingFactory.createBindings(bindingContext);

        // 2. 创建脚本上下文，注入全量绑定
        GroovyScriptContext context = GroovyScriptContext.forExecution(
                request.getInputs(), config.toMap(),
                request.getExecutionId(), request.getWorkspaceId(),
                request.getUserId(), UserContextHolder.getTenantSchema()
        );
        context.withBindings(holder);

        // 注入辅助工具
        context.setResp(new ResponseHelper(context.getJson(), context.getOss()));
        context.setReq(new RequestHelper(config.getInputSchema(), request.getInputs()));

        // 3. 执行 requestBuilderScript（脚本内部通过 llm.chat() 调用 LLM）
        String script = config.getRequestBuilderScript();
        Object scriptResult = scriptEngine.executeRequestBuilder(script, context);

        // 4. 转换为 Map
        Map<String, Object> mappedResponse;
        if (scriptResult instanceof Map) {
            mappedResponse = (Map<String, Object>) scriptResult;
        } else {
            mappedResponse = new HashMap<>();
            mappedResponse.put("result", scriptResult);
        }

        // 5. 可选：运行 responseMapperScript 做后处理
        if (StringUtils.hasText(config.getResponseMapperScript())) {
            mappedResponse = responseMapper.mapResponse(config, mappedResponse, request);
        }

        // 6. 运行 customLogic "after"
        mappedResponse = executeCustomLogic(config, request, "after", mappedResponse);

        // 7. 构建执行结果
        PluginExecutionResult result = responseMapper.buildExecutionResult(mappedResponse, config);
        result.setExecutionId(request.getExecutionId());
        result.setProviderId(config.getProviderId());
        result.setResponseMode(ResponseMode.BLOCKING);
        result.setElapsedTimeMs(System.currentTimeMillis() - startTime);
        result.setStartedAt(startedAt);
        result.setCompletedAt(LocalDateTime.now());
        result.setCreditCost(config.getCreditCost());

        log.info("[GroovyPlugin] TEXT mode completed: executionId={}, status={}, elapsed={}ms",
                executionId, result.getStatus(), result.getElapsedTimeMs());

        return result;
    }

    /**
     * 执行自定义逻辑脚本（前置）
     */
    @SuppressWarnings("unchecked")
    private void executeCustomLogic(PluginConfig config, PluginExecutionRequest request, String phase) {
        String script = config.getCustomLogicScript();
        if (!StringUtils.hasText(script)) {
            return;
        }

        GroovyScriptContext context = GroovyScriptContext.forRequestBuilder(
            request.getInputs(),
            config.toMap()
        );
        context.getExtras().put("phase", phase);
        context.getExtras().put("executionId", request.getExecutionId());
        context.setReq(new RequestHelper(config.getInputSchema(), request.getInputs()));

        scriptEngine.executeCustomLogic(script, context);
    }

    /**
     * 执行自定义后置逻辑
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> executeCustomLogic(PluginConfig config,
                                                   PluginExecutionRequest request,
                                                   String phase,
                                                   Map<String, Object> response) {
        String script = config.getCustomLogicScript();
        if (!StringUtils.hasText(script)) {
            return response;
        }

        GroovyScriptContext context = GroovyScriptContext.forResponseMapper(
            request.getInputs(),
            config.toMap(),
            response
        );
        context.getExtras().put("phase", phase);
        context.getExtras().put("executionId", request.getExecutionId());
        context.setResp(new ResponseHelper(context.getJson(), null));
        context.setReq(new RequestHelper(config.getInputSchema(), request.getInputs()));

        Object result = scriptEngine.executeCustomLogic(script, context);

        if (result instanceof Map) {
            return (Map<String, Object>) result;
        }
        return response;
    }
}
