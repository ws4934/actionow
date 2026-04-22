package com.actionow.agent.service;

import com.actionow.agent.config.AgentRuntimeConfigService;
import com.actionow.agent.dto.response.MissionSseEvent;
import com.actionow.common.web.sse.SseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Mission SSE 连接管理服务
 * 管理 Mission 进度的实时推送连接
 *
 * @author Actionow
 */
@Slf4j
@Service
public class MissionSseService {

    private final SseService sseService;

    public MissionSseService(AgentRuntimeConfigService agentRuntimeConfig) {
        long sseTimeout = agentRuntimeConfig.getMissionSseTimeoutMs();
        this.sseService = new SseService(sseTimeout);
        log.info("MissionSseService initialized: sseTimeout={}ms", sseTimeout);
    }

    /**
     * 创建 SSE 连接
     *
     * @param missionId   Mission ID
     * @param userId      用户 ID
     * @param workspaceId 工作空间 ID
     * @return SseEmitter
     */
    public SseEmitter createConnection(String missionId, String userId, String workspaceId) {
        String connectionId = buildConnectionId(missionId);
        return sseService.createConnection(connectionId, userId, workspaceId);
    }

    /**
     * 推送步骤开始事件
     */
    public void pushStepStarted(String missionId, int stepNumber, String stepType) {
        MissionSseEvent event = MissionSseEvent.builder()
                .missionId(missionId)
                .eventType("step_started")
                .status("EXECUTING")
                .message("开始执行第 " + stepNumber + " 步 (" + stepType + ")")
                .currentStep(stepNumber)
                .timestamp(LocalDateTime.now())
                .build();
        sendEvent(missionId, "step_started", event);
    }

    /**
     * 推送步骤完成事件
     */
    public void pushStepCompleted(String missionId, int stepNumber, String outputSummary) {
        MissionSseEvent event = MissionSseEvent.builder()
                .missionId(missionId)
                .eventType("step_completed")
                .status("EXECUTING")
                .message("第 " + stepNumber + " 步执行完成")
                .currentStep(stepNumber)
                .data(outputSummary != null ? Map.of("summary", outputSummary) : null)
                .timestamp(LocalDateTime.now())
                .build();
        sendEvent(missionId, "step_completed", event);
    }

    /**
     * 推送任务进度事件
     */
    public void pushTaskProgress(String missionId, int completed, int failed, int total, int progress) {
        MissionSseEvent event = MissionSseEvent.builder()
                .missionId(missionId)
                .eventType("task_progress")
                .status("WAITING")
                .message("任务进度: " + (completed + failed) + "/" + total)
                .progress(progress)
                .data(Map.of(
                        "completed", completed,
                        "failed", failed,
                        "total", total
                ))
                .timestamp(LocalDateTime.now())
                .build();
        sendEvent(missionId, "task_progress", event);
    }

    /**
     * 推送 Mission 完成事件
     */
    public void pushMissionCompleted(String missionId, int totalSteps) {
        MissionSseEvent event = MissionSseEvent.builder()
                .missionId(missionId)
                .eventType("mission_completed")
                .status("COMPLETED")
                .message("Mission 执行完成")
                .progress(100)
                .currentStep(totalSteps)
                .timestamp(LocalDateTime.now())
                .build();
        sendEvent(missionId, "mission_completed", event);

        // 终态事件发送后关闭连接
        completeConnection(missionId);
    }

    /**
     * 推送 Mission 失败事件
     */
    public void pushMissionFailed(String missionId, String errorMessage) {
        MissionSseEvent event = MissionSseEvent.builder()
                .missionId(missionId)
                .eventType("mission_failed")
                .status("FAILED")
                .message("Mission 执行失败: " + (errorMessage != null ? errorMessage : "未知错误"))
                .timestamp(LocalDateTime.now())
                .build();
        sendEvent(missionId, "mission_failed", event);

        // 终态事件发送后关闭连接
        completeConnection(missionId);
    }

    /**
     * 推送 Mission 取消事件
     */
    public void pushMissionCancelled(String missionId) {
        MissionSseEvent event = MissionSseEvent.builder()
                .missionId(missionId)
                .eventType("mission_cancelled")
                .status("CANCELLED")
                .message("Mission 已取消")
                .timestamp(LocalDateTime.now())
                .build();
        sendEvent(missionId, "mission_cancelled", event);

        // 终态事件发送后关闭连接
        completeConnection(missionId);
    }

    /**
     * 定时发送心跳
     */
    @Scheduled(fixedRate = 20000)
    public void heartbeat() {
        sseService.sendHeartbeat();
    }

    private String buildConnectionId(String missionId) {
        return "mission:" + missionId;
    }

    private void sendEvent(String missionId, String eventName, MissionSseEvent event) {
        String connectionId = buildConnectionId(missionId);
        if (!sseService.hasConnection(connectionId)) {
            return;
        }
        boolean sent = sseService.sendEvent(connectionId, eventName, event);
        if (!sent) {
            log.debug("Mission SSE 事件发送失败（连接可能已关闭）: missionId={}, event={}", missionId, eventName);
        }
    }

    private void completeConnection(String missionId) {
        String connectionId = buildConnectionId(missionId);
        if (sseService.hasConnection(connectionId)) {
            sseService.complete(connectionId);
        }
    }
}
