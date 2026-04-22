package com.actionow.agent.mission;

import com.actionow.agent.constant.MissionStatus;
import com.actionow.agent.constant.MissionStepStatus;
import com.actionow.agent.constant.MissionStepType;
import com.actionow.agent.entity.AgentMission;
import com.actionow.agent.entity.AgentMissionStep;
import com.actionow.agent.service.MissionExecutionRecordService;
import com.actionow.agent.service.MissionEventProjector;
import com.actionow.agent.service.MissionService;
import com.actionow.agent.service.MissionSseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Mission 状态推进器
 * 根据 {@link MissionDecision} 推进 Mission/Step 的持久化状态和 SSE 通知。
 * 从 MissionExecutor.processAgentDecision 抽取，单一职责：状态变更 + 事件发布。
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MissionReducer {

    private final MissionService missionService;
    private final MissionSseService missionSseService;
    private final MissionExecutionRecordService missionExecutionRecordService;
    private final MissionEventProjector missionEventProjector;

    /**
     * 根据决策推进 Mission 状态
     *
     * @param mission  当前 Mission 实体（可能会被更新）
     * @param step     当前执行步骤
     * @param decision 解析出的控制决策
     */
    public void apply(AgentMission mission, AgentMissionStep step, MissionDecision decision) {
        step.setDecisionType(decisionType(decision));
        step.setDecisionPayload(decisionPayload(decision));
        missionService.updateStep(step);
        switch (decision) {
            case MissionDecision.Complete complete -> applyComplete(mission, step, complete.summary());
            case MissionDecision.Fail fail -> applyFail(mission, step, fail.code(), fail.reason());
            case MissionDecision.Wait wait -> applyWait(mission, step, wait);
            case MissionDecision.Continue cont -> applyContinue(mission, step, cont.summary());
        }
    }

    /**
     * 标记 Mission 失败（用于内部异常路径，如冲突决策、连续失败等）
     */
    public void applyFatalFail(AgentMission mission, String reason) {
        mission.setStatus(MissionStatus.FAILED.getCode());
        mission.setErrorMessage(reason);
        mission.setFailureCode("SYSTEM");
        mission.setCompletedAt(LocalDateTime.now());
        missionService.save(mission);
        log.error("Mission 失败: missionId={}, reason={}", mission.getId(), reason);
        missionSseService.pushMissionFailed(mission.getId(), reason);
        missionExecutionRecordService.recordEvent(
                mission.getId(),
                "MISSION_FAILED",
                "Mission 执行失败",
                Map.of("reason", reason)
        );
        missionEventProjector.projectTerminalState(mission, MissionStatus.FAILED.getCode(), reason,
                Map.of("failureCode", "SYSTEM"));
    }

    // ─── private ───────────────────────────────────────────────────────────────

    private void applyComplete(AgentMission mission, AgentMissionStep step, String summary) {
        mission.setStatus(MissionStatus.COMPLETED.getCode());
        mission.setProgress(100);
        mission.setCompletedAt(LocalDateTime.now());
        mission.setCurrentStep(step.getStepNumber());
        mission.setTotalSteps(step.getStepNumber());
        mission.setErrorMessage(null);
        mission.setFailureCode(null);
        mission.setResultSummary(summary);
        mission.setResultPayload(Map.of(
                "completedTasks", missionExecutionRecordService.summarize(mission.getId()).completed(),
                "failedTasks", missionExecutionRecordService.summarize(mission.getId()).failed()
        ));
        missionService.save(mission);
        log.info("Mission 完成: missionId={}", mission.getId());
        missionSseService.pushMissionCompleted(mission.getId(), step.getStepNumber());
        missionExecutionRecordService.recordEvent(
                mission.getId(), "MISSION_COMPLETED", "Mission 执行完成",
                Map.of("stepNumber", step.getStepNumber(),
                        "summary", summary != null ? summary : ""));
        missionEventProjector.projectTerminalState(
                mission,
                MissionStatus.COMPLETED.getCode(),
                summary,
                mission.getResultPayload()
        );
    }

    private void applyFail(AgentMission mission, AgentMissionStep step, String code, String reason) {
        mission.setStatus(MissionStatus.FAILED.getCode());
        mission.setErrorMessage(reason);
        mission.setFailureCode(code != null && !code.isBlank() ? code : "AGENT_DECIDED");
        mission.setCompletedAt(LocalDateTime.now());
        mission.setCurrentStep(step.getStepNumber());
        missionService.save(mission);
        log.info("Mission 失败 (Agent 决定): missionId={}", mission.getId());
        missionSseService.pushMissionFailed(mission.getId(), reason);
        missionExecutionRecordService.recordEvent(
                mission.getId(), "MISSION_FAILED", "Agent 决定终止 Mission",
                Map.of("stepNumber", step.getStepNumber(),
                        "reason", reason != null ? reason : ""));
        missionEventProjector.projectTerminalState(
                mission,
                MissionStatus.FAILED.getCode(),
                reason,
                Map.of("failureCode", mission.getFailureCode())
        );
    }

    private void applyWait(AgentMission mission, AgentMissionStep step, MissionDecision.Wait wait) {
        // 重新加载避免脏写
        mission = missionService.getEntityById(mission.getId());
        mission.setStatus(MissionStatus.WAITING.getCode());
        mission.setCurrentStep(step.getStepNumber());
        missionService.save(mission);

        AgentMissionStep waitStep = missionService.createStep(
                mission.getId(), step.getStepNumber() + 1, MissionStepType.WAIT_TASKS.getCode());
        waitStep.setStatus(MissionStepStatus.RUNNING.getCode());
        waitStep.setStartedAt(LocalDateTime.now());
        long pendingTasks = missionExecutionRecordService.summarize(mission.getId()).pending();
        waitStep.setInputSummary("等待 " + pendingTasks + " 个委派任务完成");
        missionService.updateStep(waitStep);

        log.info("Mission 进入等待状态: missionId={}, pendingTasks={}", mission.getId(), pendingTasks);
        missionExecutionRecordService.recordEvent(
                mission.getId(), "MISSION_WAITING", "Mission 等待委派任务完成",
                Map.of("stepNumber", step.getStepNumber(),
                        "pendingTasks", pendingTasks,
                        "delegatedToolNames", wait.delegatedToolNames()));
    }

    private void applyContinue(AgentMission mission, AgentMissionStep step, String summary) {
        missionExecutionRecordService.recordEvent(
                mission.getId(), "MISSION_CONTINUE", "Mission 继续下一步",
                Map.of("stepNumber", step.getStepNumber(), "summary", summary != null ? summary : "")
        );
        mission.setCurrentStep(mission.getCurrentStep() + 1);
        missionService.save(mission);
        missionService.publishMissionStepEvent(mission.getId());
    }

    private String decisionType(MissionDecision decision) {
        return switch (decision) {
            case MissionDecision.Continue ignored -> "CONTINUE";
            case MissionDecision.Complete ignored -> "COMPLETE";
            case MissionDecision.Fail ignored -> "FAIL";
            case MissionDecision.Wait ignored -> "WAIT";
        };
    }

    private Map<String, Object> decisionPayload(MissionDecision decision) {
        return switch (decision) {
            case MissionDecision.Continue cont -> Map.of("summary", cont.summary() != null ? cont.summary() : "");
            case MissionDecision.Complete complete -> Map.of("summary", complete.summary() != null ? complete.summary() : "");
            case MissionDecision.Fail fail -> Map.of(
                    "code", fail.code() != null ? fail.code() : "",
                    "reason", fail.reason() != null ? fail.reason() : "");
            case MissionDecision.Wait wait -> Map.of(
                    "summary", wait.summary() != null ? wait.summary() : "",
                    "delegatedToolNames", wait.delegatedToolNames());
        };
    }
}
