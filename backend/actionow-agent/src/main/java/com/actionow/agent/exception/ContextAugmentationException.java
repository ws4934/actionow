package com.actionow.agent.exception;

/**
 * 上下文增强异常
 * 用于 RAG、历史加载等上下文增强过程中的错误
 *
 * @author Actionow
 */
public class ContextAugmentationException extends AgentException {

    private static final String ERROR_CODE = "CONTEXT_AUGMENTATION_ERROR";

    public ContextAugmentationException(String message) {
        super(ERROR_CODE, message, null, true); // 上下文增强失败通常可重试
    }

    public ContextAugmentationException(String message, String sessionId) {
        super(ERROR_CODE, message, sessionId, true);
    }

    public ContextAugmentationException(String message, Throwable cause) {
        super(ERROR_CODE, message, null, true, cause);
    }

    public ContextAugmentationException(String message, String sessionId, Throwable cause) {
        super(ERROR_CODE, message, sessionId, true, cause);
    }

    /**
     * 创建 RAG 检索失败异常
     */
    public static ContextAugmentationException ragFailed(String sessionId, Throwable cause) {
        return new ContextAugmentationException("RAG 检索失败", sessionId, cause);
    }

    /**
     * 创建历史加载失败异常
     */
    public static ContextAugmentationException historyLoadFailed(String sessionId, Throwable cause) {
        return new ContextAugmentationException("会话历史加载失败", sessionId, cause);
    }

    /**
     * 创建上下文构建失败异常
     */
    public static ContextAugmentationException contextBuildFailed(String sessionId, String reason) {
        return new ContextAugmentationException("上下文构建失败: " + reason, sessionId);
    }
}
