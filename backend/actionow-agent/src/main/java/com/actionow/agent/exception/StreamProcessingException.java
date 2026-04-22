package com.actionow.agent.exception;

/**
 * 流处理异常
 * 用于 SSE 流处理过程中的错误
 *
 * @author Actionow
 */
public class StreamProcessingException extends AgentException {

    private static final String ERROR_CODE = "STREAM_PROCESSING_ERROR";

    public StreamProcessingException(String message) {
        super(ERROR_CODE, message);
    }

    public StreamProcessingException(String message, String sessionId) {
        super(ERROR_CODE, message, sessionId);
    }

    public StreamProcessingException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }

    public StreamProcessingException(String message, String sessionId, Throwable cause) {
        super(ERROR_CODE, message, sessionId, cause);
    }

    /**
     * 创建流中断异常
     */
    public static StreamProcessingException interrupted(String sessionId) {
        return new StreamProcessingException("流处理被中断", sessionId);
    }

    /**
     * 创建流超时异常
     */
    public static StreamProcessingException timeout(String sessionId, long timeoutSeconds) {
        return new StreamProcessingException(
                String.format("流处理超时: %d秒", timeoutSeconds), sessionId);
    }

    /**
     * 创建事件转换异常
     */
    public static StreamProcessingException conversionFailed(String sessionId, String eventType, Throwable cause) {
        return new StreamProcessingException(
                String.format("事件转换失败: %s", eventType), sessionId, cause);
    }
}
