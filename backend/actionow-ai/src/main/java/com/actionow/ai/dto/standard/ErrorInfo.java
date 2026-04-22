package com.actionow.ai.dto.standard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 错误信息 DTO
 * 表示执行失败时的错误详情
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorInfo {

    /**
     * 错误码
     * 必填
     */
    private String code;

    /**
     * 错误信息
     * 必填
     */
    private String message;

    /**
     * 是否可重试
     */
    @Builder.Default
    private Boolean retryable = false;

    /**
     * 错误详情（可选，用于调试）
     */
    private String detail;

    // ==================== 常见错误码 ====================

    public static final String CODE_UNKNOWN = "UNKNOWN_ERROR";
    public static final String CODE_TIMEOUT = "TIMEOUT";
    public static final String CODE_RATE_LIMIT = "RATE_LIMIT_EXCEEDED";
    public static final String CODE_CONTENT_POLICY = "CONTENT_POLICY_VIOLATION";
    public static final String CODE_INVALID_REQUEST = "INVALID_REQUEST";
    public static final String CODE_AUTH_FAILED = "AUTHENTICATION_FAILED";
    public static final String CODE_QUOTA_EXCEEDED = "QUOTA_EXCEEDED";
    public static final String CODE_SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";
    public static final String CODE_INTERNAL_ERROR = "INTERNAL_ERROR";
    public static final String CODE_PROVIDER_ERROR = "PROVIDER_ERROR";

    // ==================== 工厂方法 ====================

    public static ErrorInfo unknown(String message) {
        return ErrorInfo.builder()
                .code(CODE_UNKNOWN)
                .message(message)
                .retryable(true)
                .build();
    }

    public static ErrorInfo timeout(String message) {
        return ErrorInfo.builder()
                .code(CODE_TIMEOUT)
                .message(message)
                .retryable(true)
                .build();
    }

    public static ErrorInfo rateLimit(String message) {
        return ErrorInfo.builder()
                .code(CODE_RATE_LIMIT)
                .message(message)
                .retryable(true)
                .build();
    }

    public static ErrorInfo contentPolicy(String message) {
        return ErrorInfo.builder()
                .code(CODE_CONTENT_POLICY)
                .message(message)
                .retryable(false)
                .build();
    }

    public static ErrorInfo invalidRequest(String message) {
        return ErrorInfo.builder()
                .code(CODE_INVALID_REQUEST)
                .message(message)
                .retryable(false)
                .build();
    }

    public static ErrorInfo providerError(String message) {
        return ErrorInfo.builder()
                .code(CODE_PROVIDER_ERROR)
                .message(message)
                .retryable(true)
                .build();
    }
}
