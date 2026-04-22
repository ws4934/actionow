package com.actionow.task.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 流式生成事件DTO
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamGenerationEvent {

    /**
     * 事件类型
     */
    private String eventType;

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 执行ID
     */
    private String executionId;

    /**
     * 进度 (0-100)
     */
    private Integer progress;

    /**
     * 当前步骤描述
     */
    private String currentStep;

    /**
     * 文本增量（用于文本生成）
     */
    private String textDelta;

    /**
     * 累积文本
     */
    private String textAccumulated;

    /**
     * 状态
     */
    private String status;

    /**
     * 输出数据
     */
    private Map<String, Object> outputs;

    /**
     * 错误码
     */
    private String errorCode;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 时间戳
     */
    private String timestamp;

    /**
     * 创建开始事件
     */
    public static StreamGenerationEvent started(String taskId, String executionId) {
        return StreamGenerationEvent.builder()
                .eventType("STARTED")
                .taskId(taskId)
                .executionId(executionId)
                .status("RUNNING")
                .progress(0)
                .timestamp(LocalDateTime.now().toString())
                .build();
    }

    /**
     * 创建进度事件
     */
    public static StreamGenerationEvent progress(String taskId, String executionId, int progress, String step) {
        return StreamGenerationEvent.builder()
                .eventType("PROGRESS")
                .taskId(taskId)
                .executionId(executionId)
                .status("RUNNING")
                .progress(progress)
                .currentStep(step)
                .timestamp(LocalDateTime.now().toString())
                .build();
    }

    /**
     * 创建文本事件
     */
    public static StreamGenerationEvent text(String taskId, String executionId, String delta, String accumulated) {
        return StreamGenerationEvent.builder()
                .eventType("TEXT")
                .taskId(taskId)
                .executionId(executionId)
                .status("RUNNING")
                .textDelta(delta)
                .textAccumulated(accumulated)
                .timestamp(LocalDateTime.now().toString())
                .build();
    }

    /**
     * 创建完成事件
     */
    public static StreamGenerationEvent completed(String taskId, String executionId, Map<String, Object> outputs) {
        return StreamGenerationEvent.builder()
                .eventType("COMPLETED")
                .taskId(taskId)
                .executionId(executionId)
                .status("SUCCEEDED")
                .progress(100)
                .outputs(outputs)
                .timestamp(LocalDateTime.now().toString())
                .build();
    }

    /**
     * 创建错误事件
     */
    public static StreamGenerationEvent error(String taskId, String executionId, String errorCode, String errorMessage) {
        return StreamGenerationEvent.builder()
                .eventType("ERROR")
                .taskId(taskId)
                .executionId(executionId)
                .status("FAILED")
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .timestamp(LocalDateTime.now().toString())
                .build();
    }
}
