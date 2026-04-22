package com.actionow.agent.exception;

import com.actionow.common.core.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Agent 模块全局异常处理器
 * 统一处理 Agent 相关异常，返回结构化错误响应
 *
 * @author Actionow
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.actionow.agent")
@Order(1) // 优先于全局异常处理器
public class AgentExceptionHandler {

    /**
     * 处理流处理异常
     */
    @ExceptionHandler(StreamProcessingException.class)
    public ResponseEntity<Result<Void>> handleStreamProcessingException(StreamProcessingException e) {
        log.error("流处理异常 [sessionId={}]: {}", e.getSessionId(), e.getMessage(), e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(e.getErrorCode(), e.getMessage()));
    }

    /**
     * 处理上下文增强异常
     */
    @ExceptionHandler(ContextAugmentationException.class)
    public ResponseEntity<Result<Void>> handleContextAugmentationException(ContextAugmentationException e) {
        log.warn("上下文增强异常 [sessionId={}, retryable={}]: {}",
                e.getSessionId(), e.isRetryable(), e.getMessage(), e);
        HttpStatus status = e.isRetryable()
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity
                .status(status)
                .body(Result.fail(e.getErrorCode(), e.getMessage()));
    }

    /**
     * 处理 LLM 通信异常
     */
    @ExceptionHandler(LlmCommunicationException.class)
    public ResponseEntity<Result<Void>> handleLlmCommunicationException(LlmCommunicationException e) {
        log.error("LLM 通信异常 [sessionId={}, provider={}, httpStatus={}, retryable={}]: {}",
                e.getSessionId(), e.getProvider(), e.getHttpStatus(), e.isRetryable(), e.getMessage(), e);

        HttpStatus status = mapLlmHttpStatus(e.getHttpStatus(), e.isRetryable());
        return ResponseEntity
                .status(status)
                .body(Result.fail(e.getErrorCode(), e.getMessage()));
    }

    /**
     * 处理工具执行异常
     */
    @ExceptionHandler(ToolExecutionException.class)
    public ResponseEntity<Result<Void>> handleToolExecutionException(ToolExecutionException e) {
        log.error("工具执行异常 [sessionId={}, toolName={}, toolCallId={}]: {}",
                e.getSessionId(), e.getToolName(), e.getToolCallId(), e.getMessage(), e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(e.getErrorCode(), e.getMessage()));
    }

    /**
     * 处理 Agent 基础异常
     */
    @ExceptionHandler(AgentException.class)
    public ResponseEntity<Result<Void>> handleAgentException(AgentException e) {
        log.error("Agent 异常 [sessionId={}, errorCode={}, retryable={}]: {}",
                e.getSessionId(), e.getErrorCode(), e.isRetryable(), e.getMessage(), e);

        HttpStatus status = e.isRetryable()
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity
                .status(status)
                .body(Result.fail(e.getErrorCode(), e.getMessage()));
    }

    /**
     * 映射 LLM HTTP 状态码到响应状态
     */
    private HttpStatus mapLlmHttpStatus(Integer httpStatus, boolean retryable) {
        if (httpStatus == null) {
            return retryable ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return switch (httpStatus) {
            case 401, 403 -> HttpStatus.UNAUTHORIZED;
            case 429 -> HttpStatus.TOO_MANY_REQUESTS;
            case 503, 504 -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
