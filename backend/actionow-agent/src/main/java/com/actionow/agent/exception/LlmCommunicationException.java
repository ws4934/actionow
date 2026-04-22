package com.actionow.agent.exception;

import lombok.Getter;

/**
 * LLM 通信异常
 * 用于与 LLM 服务通信过程中的错误
 *
 * @author Actionow
 */
@Getter
public class LlmCommunicationException extends AgentException {

    private static final String ERROR_CODE = "LLM_COMMUNICATION_ERROR";

    /**
     * HTTP 状态码（如果适用）
     */
    private final Integer httpStatus;

    /**
     * LLM 提供商
     */
    private final String provider;

    public LlmCommunicationException(String message) {
        super(ERROR_CODE, message, null, true); // LLM 通信失败通常可重试
        this.httpStatus = null;
        this.provider = null;
    }

    public LlmCommunicationException(String message, String sessionId) {
        super(ERROR_CODE, message, sessionId, true);
        this.httpStatus = null;
        this.provider = null;
    }

    public LlmCommunicationException(String message, Throwable cause) {
        super(ERROR_CODE, message, null, true, cause);
        this.httpStatus = null;
        this.provider = null;
    }

    public LlmCommunicationException(String message, String sessionId, Throwable cause) {
        super(ERROR_CODE, message, sessionId, true, cause);
        this.httpStatus = null;
        this.provider = null;
    }

    public LlmCommunicationException(String message, String sessionId, Integer httpStatus, String provider) {
        super(ERROR_CODE, message, sessionId, isRetryableStatus(httpStatus));
        this.httpStatus = httpStatus;
        this.provider = provider;
    }

    public LlmCommunicationException(String message, String sessionId, Integer httpStatus, String provider, Throwable cause) {
        super(ERROR_CODE, message, sessionId, isRetryableStatus(httpStatus), cause);
        this.httpStatus = httpStatus;
        this.provider = provider;
    }

    /**
     * 判断 HTTP 状态码是否可重试
     */
    private static boolean isRetryableStatus(Integer httpStatus) {
        if (httpStatus == null) return true;
        // 429 (Rate Limit), 500, 502, 503, 504 可重试
        return httpStatus == 429 || httpStatus >= 500;
    }

    /**
     * 创建连接超时异常
     */
    public static LlmCommunicationException connectionTimeout(String sessionId, String provider) {
        return new LlmCommunicationException(
                String.format("LLM 连接超时: %s", provider), sessionId, null, provider);
    }

    /**
     * 创建速率限制异常
     */
    public static LlmCommunicationException rateLimited(String sessionId, String provider) {
        return new LlmCommunicationException(
                String.format("LLM 速率限制: %s", provider), sessionId, 429, provider);
    }

    /**
     * 创建服务不可用异常
     */
    public static LlmCommunicationException serviceUnavailable(String sessionId, String provider) {
        return new LlmCommunicationException(
                String.format("LLM 服务不可用: %s", provider), sessionId, 503, provider);
    }

    /**
     * 创建认证失败异常（不可重试）
     */
    public static LlmCommunicationException authenticationFailed(String sessionId, String provider) {
        return new LlmCommunicationException(
                String.format("LLM 认证失败: %s", provider), sessionId, 401, provider);
    }
}
