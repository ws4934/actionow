package com.actionow.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 流式事件 DTO
 * 用于 SSE 流式响应
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamEventDTO {

    /**
     * 事件类型
     */
    private String eventType;

    /**
     * 执行 ID
     */
    private String executionId;

    /**
     * 外部任务 ID
     */
    private String externalTaskId;

    /**
     * 事件数据
     */
    private Map<String, Object> data;

    /**
     * 文本增量（用于 TEXT 类型的增量输出）
     */
    private String textDelta;

    /**
     * 累积文本（用于 TEXT 类型）
     */
    private String textAccumulated;

    /**
     * 进度（0-100）
     */
    private Integer progress;

    /**
     * 当前步骤
     */
    private String currentStep;

    /**
     * 错误码
     */
    private String errorCode;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 事件时间戳（ISO 8601 格式）
     */
    private String timestamp;

    /**
     * 创建错误事件
     *
     * @param executionId  执行 ID
     * @param errorCode    错误码
     * @param errorMessage 错误信息
     * @return 错误事件 DTO
     */
    public static StreamEventDTO error(String executionId, String errorCode, String errorMessage) {
        return StreamEventDTO.builder()
                .eventType("ERROR")
                .executionId(executionId)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .timestamp(java.time.LocalDateTime.now().toString())
                .build();
    }

    /**
     * 创建开始事件
     *
     * @param executionId    执行 ID
     * @param externalTaskId 外部任务 ID
     * @return 开始事件 DTO
     */
    public static StreamEventDTO started(String executionId, String externalTaskId) {
        return StreamEventDTO.builder()
                .eventType("WORKFLOW_STARTED")
                .executionId(executionId)
                .externalTaskId(externalTaskId)
                .progress(0)
                .timestamp(java.time.LocalDateTime.now().toString())
                .build();
    }

    /**
     * 创建进度事件
     *
     * @param executionId 执行 ID
     * @param progress    进度值
     * @param currentStep 当前步骤
     * @return 进度事件 DTO
     */
    public static StreamEventDTO progress(String executionId, int progress, String currentStep) {
        return StreamEventDTO.builder()
                .eventType("PROGRESS")
                .executionId(executionId)
                .progress(progress)
                .currentStep(currentStep)
                .timestamp(java.time.LocalDateTime.now().toString())
                .build();
    }

    /**
     * 创建完成事件
     *
     * @param executionId 执行 ID
     * @param data        结果数据
     * @return 完成事件 DTO
     */
    public static StreamEventDTO finished(String executionId, Map<String, Object> data) {
        return StreamEventDTO.builder()
                .eventType("WORKFLOW_FINISHED")
                .executionId(executionId)
                .data(data)
                .progress(100)
                .timestamp(java.time.LocalDateTime.now().toString())
                .build();
    }
}
