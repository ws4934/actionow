package com.actionow.agent.exception;

import lombok.Getter;

/**
 * 工具执行异常
 * 用于 Agent 工具执行过程中的错误
 *
 * @author Actionow
 */
@Getter
public class ToolExecutionException extends AgentException {

    private static final String ERROR_CODE = "TOOL_EXECUTION_ERROR";

    /**
     * 工具名称
     */
    private final String toolName;

    /**
     * 工具调用 ID
     */
    private final String toolCallId;

    public ToolExecutionException(String message, String toolName) {
        super(ERROR_CODE, message);
        this.toolName = toolName;
        this.toolCallId = null;
    }

    public ToolExecutionException(String message, String toolName, String toolCallId) {
        super(ERROR_CODE, message);
        this.toolName = toolName;
        this.toolCallId = toolCallId;
    }

    public ToolExecutionException(String message, String toolName, Throwable cause) {
        super(ERROR_CODE, message, cause);
        this.toolName = toolName;
        this.toolCallId = null;
    }

    public ToolExecutionException(String message, String toolName, String toolCallId, Throwable cause) {
        super(ERROR_CODE, message, cause);
        this.toolName = toolName;
        this.toolCallId = toolCallId;
    }

    public ToolExecutionException(String message, String toolName, String toolCallId, String sessionId, Throwable cause) {
        super(ERROR_CODE, message, sessionId, cause);
        this.toolName = toolName;
        this.toolCallId = toolCallId;
    }

    /**
     * 创建工具未找到异常
     */
    public static ToolExecutionException notFound(String toolName) {
        return new ToolExecutionException(
                String.format("工具未找到: %s", toolName), toolName);
    }

    /**
     * 创建参数验证失败异常
     */
    public static ToolExecutionException validationFailed(String toolName, String toolCallId, String reason) {
        return new ToolExecutionException(
                String.format("工具参数验证失败: %s", reason), toolName, toolCallId);
    }

    /**
     * 创建执行超时异常
     */
    public static ToolExecutionException timeout(String toolName, String toolCallId, long timeoutSeconds) {
        return new ToolExecutionException(
                String.format("工具执行超时: %d秒", timeoutSeconds), toolName, toolCallId);
    }

    /**
     * 创建权限不足异常
     */
    public static ToolExecutionException permissionDenied(String toolName, String toolCallId, String reason) {
        return new ToolExecutionException(
                String.format("工具执行权限不足: %s", reason), toolName, toolCallId);
    }

    /**
     * 创建 Feign 调用失败异常
     */
    public static ToolExecutionException feignCallFailed(String toolName, String toolCallId, Throwable cause) {
        return new ToolExecutionException(
                "工具远程调用失败", toolName, toolCallId, cause);
    }
}
