package com.actionow.agent.service;

import com.actionow.agent.entity.AgentMission;
import com.actionow.agent.entity.AgentMissionTask;
import com.actionow.agent.entity.AgentMissionEvent;
import com.actionow.agent.entity.AgentMissionTrace;

import java.util.Map;
import java.util.List;

/**
 * Mission 执行记录服务。
 */
public interface MissionExecutionRecordService {

    AgentMissionTask registerTask(String missionId, String missionStepId, String taskKind,
                                  String externalTaskId, String batchJobId,
                                  String entityType, String entityId,
                                  Map<String, Object> requestPayload);

    AgentMissionTask findTaskByExternalTaskId(String externalTaskId);

    AgentMissionTask findTaskByBatchJobId(String batchJobId);

    AgentMission findMissionByTaskId(String externalTaskId);

    void markTaskCompleted(String externalTaskId, Map<String, Object> resultPayload);

    void markTaskFailed(String externalTaskId, String failureCode, String failureMessage,
                        Map<String, Object> resultPayload);

    void recordEvent(String missionId, String eventType, String message, Map<String, Object> payload);

    void recordTrace(String missionId, String missionStepId, String traceType, Map<String, Object> payload);

    MissionTaskStats summarize(String missionId);

    List<AgentMissionTask> listTasks(String missionId);

    List<AgentMissionEvent> listEvents(String missionId);

    List<AgentMissionTrace> listTraces(String missionId);

    record MissionTaskStats(long pending, long completed, long failed) {
        public long total() {
            return pending + completed + failed;
        }
    }
}
