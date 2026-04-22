package com.actionow.ai.plugin.handler.impl;

import com.actionow.ai.plugin.handler.ResponseHandler;
import com.actionow.ai.plugin.model.PluginConfig;
import com.actionow.ai.plugin.model.PluginExecutionResult;
import com.actionow.ai.plugin.model.ResponseMode;
import com.jayway.jsonpath.JsonPath;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * 轮询响应处理器
 * 处理异步轮询模式的响应
 *
 * @author Actionow
 */
@Slf4j
public class PollingResponseHandler implements ResponseHandler, ResponseHandler.AsyncResponseHandler {

    private final BlockingResponseHandler blockingHandler;
    private final CallbackResponseHandler callbackHandler;

    public PollingResponseHandler() {
        this.blockingHandler = new BlockingResponseHandler();
        this.callbackHandler = new CallbackResponseHandler();
    }

    @Override
    public ResponseMode getResponseMode() {
        return ResponseMode.POLLING;
    }

    @Override
    public PluginExecutionResult handleResponse(Object rawResponse, PluginConfig config) {
        return handleStatusResponse(rawResponse, config);
    }

    @Override
    public Map<String, Object> applyResponseMapping(Object rawResponse, Map<String, Object> responseMapping) {
        return blockingHandler.applyResponseMapping(rawResponse, responseMapping);
    }

    @Override
    @SuppressWarnings("unchecked")
    public PluginExecutionResult handleSubmitResponse(Object submitResponse, PluginConfig config) {
        // 提交响应与回调模式类似
        return callbackHandler.handleSubmitResponse(submitResponse, config);
    }

    @Override
    @SuppressWarnings("unchecked")
    public PluginExecutionResult handleStatusResponse(Object statusResponse, PluginConfig config) {
        if (statusResponse == null) {
            return PluginExecutionResult.failure(null, "EMPTY_RESPONSE", "状态响应为空");
        }

        try {
            PluginConfig.PollingConfig pollingConfig = config.getPollingConfig();

            String statusPath = pollingConfig != null && pollingConfig.getStatusPath() != null
                ? pollingConfig.getStatusPath()
                : "$.status";
            String resultPath = pollingConfig != null && pollingConfig.getResultPath() != null
                ? pollingConfig.getResultPath()
                : "$.data";
            Set<String> successStatuses = pollingConfig != null && pollingConfig.getSuccessStatuses() != null
                ? pollingConfig.getSuccessStatuses()
                : Collections.singleton("succeeded");
            Set<String> failedStatuses = pollingConfig != null && pollingConfig.getFailedStatuses() != null
                ? pollingConfig.getFailedStatuses()
                : Collections.singleton("failed");

            // 提取状态
            Object statusObj = JsonPath.read(statusResponse, statusPath);
            String status = statusObj != null ? statusObj.toString().toLowerCase() : "unknown";

            // 判断执行状态
            PluginExecutionResult.ExecutionStatus executionStatus = mapStatus(status, successStatuses, failedStatuses);

            if (executionStatus.isTerminal()) {
                // 任务已完成
                Object resultData = null;
                try {
                    resultData = JsonPath.read(statusResponse, resultPath);
                } catch (Exception ignored) {
                    resultData = statusResponse;
                }

                Map<String, Object> outputs = applyResponseMapping(resultData, null);

                if (executionStatus == PluginExecutionResult.ExecutionStatus.FAILED) {
                    String errorCode = extractField(statusResponse, "error_code", "errorCode", "code");
                    String errorMessage = extractField(statusResponse, "error_message", "errorMessage", "message", "error");
                    return PluginExecutionResult.builder()
                        .status(executionStatus)
                        .errorCode(errorCode)
                        .errorMessage(errorMessage)
                        .outputs(outputs)
                        .rawResponse(statusResponse)
                        .completedAt(LocalDateTime.now())
                        .build();
                }

                return PluginExecutionResult.builder()
                    .status(executionStatus)
                    .outputs(outputs)
                    .assets(blockingHandler.extractAssets(outputs, config))
                    .rawResponse(statusResponse)
                    .completedAt(LocalDateTime.now())
                    .build();
            }

            // 任务仍在进行中，返回当前状态
            Integer progress = extractProgress(statusResponse);

            return PluginExecutionResult.builder()
                .status(executionStatus)
                .rawResponse(statusResponse)
                .build();

        } catch (Exception e) {
            log.error("Failed to handle polling status response: {}", e.getMessage(), e);
            return PluginExecutionResult.failure(null, "PARSE_ERROR", "状态响应解析失败: " + e.getMessage());
        }
    }

    private PluginExecutionResult.ExecutionStatus mapStatus(String status, Set<String> successStatuses, Set<String> failedStatuses) {
        String normalizedStatus = status.toLowerCase();

        // 检查是否匹配配置的成功状态
        boolean isSuccess = successStatuses.stream()
            .anyMatch(s -> normalizedStatus.equals(s.toLowerCase()));
        // 检查是否匹配配置的失败状态
        boolean isFailed = failedStatuses.stream()
            .anyMatch(s -> normalizedStatus.equals(s.toLowerCase()));

        if (isSuccess ||
            normalizedStatus.equals("success") ||
            normalizedStatus.equals("completed") ||
            normalizedStatus.equals("done")) {
            return PluginExecutionResult.ExecutionStatus.SUCCEEDED;
        }

        if (isFailed ||
            normalizedStatus.equals("error") ||
            normalizedStatus.equals("failure")) {
            return PluginExecutionResult.ExecutionStatus.FAILED;
        }

        if (normalizedStatus.equals("cancelled") || normalizedStatus.equals("canceled")) {
            return PluginExecutionResult.ExecutionStatus.CANCELLED;
        }

        if (normalizedStatus.equals("timeout") || normalizedStatus.equals("timed_out")) {
            return PluginExecutionResult.ExecutionStatus.TIMEOUT;
        }

        if (normalizedStatus.equals("pending") || normalizedStatus.equals("queued")) {
            return PluginExecutionResult.ExecutionStatus.PENDING;
        }

        // 默认为运行中
        return PluginExecutionResult.ExecutionStatus.RUNNING;
    }

    @SuppressWarnings("unchecked")
    private String extractField(Object response, String... keys) {
        if (response instanceof Map<?, ?> map) {
            for (String key : keys) {
                Object value = map.get(key);
                if (value instanceof String && !((String) value).isEmpty()) {
                    return (String) value;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Integer extractProgress(Object response) {
        if (response instanceof Map<?, ?> map) {
            Object progress = map.get("progress");
            if (progress == null) progress = map.get("percent");
            if (progress instanceof Number) {
                return ((Number) progress).intValue();
            }
            if (progress instanceof String) {
                try {
                    return Integer.parseInt((String) progress);
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }
}
