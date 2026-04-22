package com.actionow.agent.exception;

import lombok.Getter;

/**
 * Agent 模块基础异常类
 * 所有 Agent 相关异常的父类，提供统一的错误码和上下文信息
 *
 * @author Actionow
 */
@Getter
public class AgentException extends RuntimeException {

    /**
     * 错误码
     */
    private final String errorCode;

    /**
     * 会话 ID（可选）
     */
    private final String sessionId;

    /**
     * 是否可重试
     */
    private final boolean retryable;

    public AgentException(String message) {
        super(message);
        this.errorCode = "AGENT_ERROR";
        this.sessionId = null;
        this.retryable = false;
    }

    public AgentException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.sessionId = null;
        this.retryable = false;
    }

    public AgentException(String errorCode, String message, String sessionId) {
        super(message);
        this.errorCode = errorCode;
        this.sessionId = sessionId;
        this.retryable = false;
    }

    public AgentException(String errorCode, String message, String sessionId, boolean retryable) {
        super(message);
        this.errorCode = errorCode;
        this.sessionId = sessionId;
        this.retryable = retryable;
    }

    public AgentException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.sessionId = null;
        this.retryable = false;
    }

    public AgentException(String errorCode, String message, String sessionId, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.sessionId = sessionId;
        this.retryable = false;
    }

    public AgentException(String errorCode, String message, String sessionId, boolean retryable, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.sessionId = sessionId;
        this.retryable = retryable;
    }
}
