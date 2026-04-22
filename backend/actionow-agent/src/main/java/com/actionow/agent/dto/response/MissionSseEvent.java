package com.actionow.agent.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Mission SSE 事件 DTO
 * 用于实时推送 Mission 执行进度
 *
 * @author Actionow
 */
@Data
@Builder
public class MissionSseEvent {

    /**
     * Mission ID
     */
    private String missionId;

    /**
     * 事件类型:
     * step_started, step_completed, task_progress,
     * mission_completed, mission_failed, mission_cancelled
     */
    private String eventType;

    /**
     * Mission 当前状态
     */
    private String status;

    /**
     * 事件描述消息
     */
    private String message;

    /**
     * 进度百分比 (0-100)
     */
    private Integer progress;

    /**
     * 当前步骤编号
     */
    private Integer currentStep;

    /**
     * 附加数据
     */
    private Map<String, Object> data;

    /**
     * 事件时间
     */
    private LocalDateTime timestamp;
}
