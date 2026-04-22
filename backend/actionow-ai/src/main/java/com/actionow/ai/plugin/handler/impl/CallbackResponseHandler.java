package com.actionow.ai.plugin.handler.impl;

import com.actionow.ai.plugin.handler.ResponseHandler;
import com.actionow.ai.plugin.model.PluginConfig;
import com.actionow.ai.plugin.model.PluginExecutionResult;
import com.actionow.ai.plugin.model.ResponseMode;
import com.jayway.jsonpath.JsonPath;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 回调响应处理器
 * 处理异步回调模式的响应
 *
 * @author Actionow
 */
@Slf4j
public class CallbackResponseHandler implements ResponseHandler, ResponseHandler.AsyncResponseHandler {

    private final BlockingResponseHandler blockingHandler;

    public CallbackResponseHandler() {
        this.blockingHandler = new BlockingResponseHandler();
    }

    @Override
    public ResponseMode getResponseMode() {
        return ResponseMode.CALLBACK;
    }

    @Override
    public PluginExecutionResult handleResponse(Object rawResponse, PluginConfig config) {
        // 回调模式的完整响应处理
        return handleStatusResponse(rawResponse, config);
    }

    @Override
    public Map<String, Object> applyResponseMapping(Object rawResponse, Map<String, Object> responseMapping) {
        return blockingHandler.applyResponseMapping(rawResponse, responseMapping);
    }

    @Override
    @SuppressWarnings("unchecked")
    public PluginExecutionResult handleSubmitResponse(Object submitResponse, PluginConfig config) {
        if (submitResponse == null) {
            return PluginExecutionResult.failure(null, "EMPTY_RESPONSE", "提交响应为空");
        }

        try {
            String externalTaskId = null;
            String externalRunId = null;

            if (submitResponse instanceof Map<?, ?> map) {
                // 尝试从常见字段提取任务ID
                externalTaskId = extractTaskId(map);
                externalRunId = (String) map.get("run_id");
                if (externalRunId == null) {
                    externalRunId = (String) map.get("runId");
                }
            }

            if (externalTaskId == null) {
                return PluginExecutionResult.failure(null, "NO_TASK_ID", "未能获取任务ID");
            }

            return PluginExecutionResult.builder()
                .externalTaskId(externalTaskId)
                .externalRunId(externalRunId)
                .status(PluginExecutionResult.ExecutionStatus.PENDING)
                .responseMode(ResponseMode.CALLBACK)
                .submittedAt(LocalDateTime.now())
                .rawResponse(submitResponse)
                .build();

        } catch (Exception e) {
            log.error("Failed to handle submit response: {}", e.getMessage(), e);
            return PluginExecutionResult.failure(null, "PARSE_ERROR", "提交响应解析失败: " + e.getMessage());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public PluginExecutionResult handleStatusResponse(Object statusResponse, PluginConfig config) {
        if (statusResponse == null) {
            return PluginExecutionResult.failure(null, "EMPTY_RESPONSE", "状态响应为空");
        }

        try {
            PluginConfig.CallbackConfig callbackConfig = config.getCallbackConfig();
            String statusPath = callbackConfig != null && callbackConfig.getStatusPath() != null
                ? callbackConfig.getStatusPath()
                : "$.status";
            String resultPath = callbackConfig != null && callbackConfig.getResultPath() != null
                ? callbackConfig.getResultPath()
                : "$.data";

            // 提取状态
            Object statusObj = JsonPath.read(statusResponse, statusPath);
            String status = statusObj != null ? statusObj.toString().toLowerCase() : "unknown";

            // 判断执行状态
            PluginExecutionResult.ExecutionStatus executionStatus = mapStatus(status);

            if (executionStatus.isTerminal()) {
                // 任务已完成
                Object resultData = null;
                try {
                    resultData = JsonPath.read(statusResponse, resultPath);
                } catch (Exception ignored) {
                    // 结果可能在根级别
                    resultData = statusResponse;
                }

                Map<String, Object> outputs = applyResponseMapping(resultData, null);

                if (executionStatus == PluginExecutionResult.ExecutionStatus.FAILED) {
                    String errorCode = extractErrorCode(statusResponse);
                    String errorMessage = extractErrorMessage(statusResponse);
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

            // 任务仍在进行中
            return PluginExecutionResult.builder()
                .status(executionStatus)
                .rawResponse(statusResponse)
                .build();

        } catch (Exception e) {
            log.error("Failed to handle status response: {}", e.getMessage(), e);
            return PluginExecutionResult.failure(null, "PARSE_ERROR", "状态响应解析失败: " + e.getMessage());
        }
    }

    private String extractTaskId(Map<?, ?> map) {
        String[] possibleKeys = {"task_id", "taskId", "id", "job_id", "jobId", "request_id", "requestId"};
        for (String key : possibleKeys) {
            Object value = map.get(key);
            if (value instanceof String && !((String) value).isEmpty()) {
                return (String) value;
            }
        }
        return null;
    }

    private PluginExecutionResult.ExecutionStatus mapStatus(String status) {
        return switch (status) {
            case "pending", "queued", "submitted" -> PluginExecutionResult.ExecutionStatus.PENDING;
            case "running", "processing", "in_progress" -> PluginExecutionResult.ExecutionStatus.RUNNING;
            case "succeeded", "success", "completed", "done", "finished" -> PluginExecutionResult.ExecutionStatus.SUCCEEDED;
            case "failed", "error", "failure" -> PluginExecutionResult.ExecutionStatus.FAILED;
            case "cancelled", "canceled", "stopped" -> PluginExecutionResult.ExecutionStatus.CANCELLED;
            case "timeout", "timed_out" -> PluginExecutionResult.ExecutionStatus.TIMEOUT;
            default -> PluginExecutionResult.ExecutionStatus.RUNNING;
        };
    }

    @SuppressWarnings("unchecked")
    private String extractErrorCode(Object response) {
        if (response instanceof Map<?, ?> map) {
            Object code = map.get("error_code");
            if (code == null) code = map.get("errorCode");
            if (code == null) code = map.get("code");
            return code != null ? code.toString() : null;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String extractErrorMessage(Object response) {
        if (response instanceof Map<?, ?> map) {
            Object message = map.get("error_message");
            if (message == null) message = map.get("errorMessage");
            if (message == null) message = map.get("message");
            if (message == null) message = map.get("error");
            return message != null ? message.toString() : null;
        }
        return null;
    }
}
