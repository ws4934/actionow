package com.actionow.ai.plugin.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 插件流式事件
 * 用于流式响应模式下的事件传递
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginStreamEvent {

    /**
     * 事件类型
     */
    private EventType eventType;

    /**
     * 执行ID
     */
    private String executionId;

    /**
     * 外部任务ID
     */
    private String externalTaskId;

    /**
     * 事件数据
     */
    private Map<String, Object> data;

    /**
     * 文本内容（用于TEXT类型的增量输出）
     */
    private String textDelta;

    /**
     * 累积文本（用于TEXT类型）
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
     * 事件时间戳
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * 原始事件数据
     */
    private Object rawEvent;

    /**
     * 事件类型枚举
     */
    public enum EventType {
        /**
         * 工作流开始
         */
        WORKFLOW_STARTED,

        /**
         * 节点开始执行
         */
        NODE_STARTED,

        /**
         * 节点执行完成
         */
        NODE_FINISHED,

        /**
         * 文本输出（增量）
         */
        TEXT_CHUNK,

        /**
         * 进度更新
         */
        PROGRESS,

        /**
         * 工作流完成
         */
        WORKFLOW_FINISHED,

        /**
         * 错误事件
         */
        ERROR,

        /**
         * 心跳（保持连接）
         */
        PING,

        /**
         * 未知事件
         */
        UNKNOWN
    }

    /**
     * 创建开始事件
     */
    public static PluginStreamEvent started(String executionId, String externalTaskId) {
        return PluginStreamEvent.builder()
                .eventType(EventType.WORKFLOW_STARTED)
                .executionId(executionId)
                .externalTaskId(externalTaskId)
                .progress(0)
                .build();
    }

    /**
     * 创建进度事件
     */
    public static PluginStreamEvent progress(String executionId, int progress, String currentStep) {
        return PluginStreamEvent.builder()
                .eventType(EventType.PROGRESS)
                .executionId(executionId)
                .progress(progress)
                .currentStep(currentStep)
                .build();
    }

    /**
     * 创建文本块事件
     */
    public static PluginStreamEvent textChunk(String executionId, String textDelta, String accumulated) {
        return PluginStreamEvent.builder()
                .eventType(EventType.TEXT_CHUNK)
                .executionId(executionId)
                .textDelta(textDelta)
                .textAccumulated(accumulated)
                .build();
    }

    /**
     * 创建完成事件
     */
    public static PluginStreamEvent finished(String executionId, Map<String, Object> data) {
        return PluginStreamEvent.builder()
                .eventType(EventType.WORKFLOW_FINISHED)
                .executionId(executionId)
                .data(data)
                .progress(100)
                .build();
    }

    /**
     * 创建错误事件
     */
    public static PluginStreamEvent error(String executionId, String errorCode, String errorMessage) {
        return PluginStreamEvent.builder()
                .eventType(EventType.ERROR)
                .executionId(executionId)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * 是否为终态事件
     */
    public boolean isTerminal() {
        return eventType == EventType.WORKFLOW_FINISHED || eventType == EventType.ERROR;
    }

    /**
     * 是否为心跳事件
     */
    public boolean isPing() {
        return eventType == EventType.PING;
    }
}
