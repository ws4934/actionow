package com.actionow.agent.service.impl;

import com.actionow.agent.entity.AgentMission;
import com.actionow.agent.entity.AgentMissionEvent;
import com.actionow.agent.entity.AgentMissionTask;
import com.actionow.agent.entity.AgentMissionTrace;
import com.actionow.agent.mapper.AgentMissionEventMapper;
import com.actionow.agent.mapper.AgentMissionMapper;
import com.actionow.agent.mapper.AgentMissionTaskMapper;
import com.actionow.agent.mapper.AgentMissionTraceMapper;
import com.actionow.agent.service.MissionExecutionRecordService;
import com.actionow.common.core.id.UuidGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Mission 执行记录服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MissionExecutionRecordServiceImpl implements MissionExecutionRecordService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";

    private final AgentMissionMapper missionMapper;
    private final AgentMissionTaskMapper missionTaskMapper;
    private final AgentMissionEventMapper missionEventMapper;
    private final AgentMissionTraceMapper missionTraceMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentMissionTask registerTask(String missionId, String missionStepId, String taskKind,
                                         String externalTaskId, String batchJobId,
                                         String entityType, String entityId,
                                         Map<String, Object> requestPayload) {
        AgentMissionTask existing = externalTaskId != null ? missionTaskMapper.selectByExternalTaskId(externalTaskId) : null;
        if (existing != null) {
            return existing;
        }

        AgentMissionTask task = new AgentMissionTask();
        task.setId(UuidGenerator.generateUuidV7());
        task.setMissionId(missionId);
        task.setMissionStepId(missionStepId);
        task.setTaskKind(taskKind);
        task.setExternalTaskId(externalTaskId);
        task.setBatchJobId(batchJobId);
        task.setEntityType(entityType);
        task.setEntityId(entityId);
        task.setStatus(STATUS_PENDING);
        task.setRequestPayload(requestPayload);
        task.setStartedAt(LocalDateTime.now());
        missionTaskMapper.insert(task);
        return task;
    }

    @Override
    public AgentMissionTask findTaskByExternalTaskId(String externalTaskId) {
        return missionTaskMapper.selectByExternalTaskId(externalTaskId);
    }

    @Override
    public AgentMissionTask findTaskByBatchJobId(String batchJobId) {
        return missionTaskMapper.selectByBatchJobId(batchJobId);
    }

    @Override
    public AgentMission findMissionByTaskId(String externalTaskId) {
        AgentMissionTask task = findTaskByExternalTaskId(externalTaskId);
        return task != null ? missionMapper.selectById(task.getMissionId()) : null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markTaskCompleted(String externalTaskId, Map<String, Object> resultPayload) {
        AgentMissionTask task = requireTask(externalTaskId);
        task.setStatus(STATUS_COMPLETED);
        task.setResultPayload(resultPayload);
        task.setFailureCode(null);
        task.setFailureMessage(null);
        task.setCompletedAt(LocalDateTime.now());
        missionTaskMapper.updateById(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markTaskFailed(String externalTaskId, String failureCode, String failureMessage,
                               Map<String, Object> resultPayload) {
        AgentMissionTask task = requireTask(externalTaskId);
        task.setStatus(STATUS_FAILED);
        task.setResultPayload(resultPayload);
        task.setFailureCode(failureCode);
        task.setFailureMessage(failureMessage);
        task.setCompletedAt(LocalDateTime.now());
        missionTaskMapper.updateById(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordEvent(String missionId, String eventType, String message, Map<String, Object> payload) {
        AgentMissionEvent event = new AgentMissionEvent();
        event.setId(UuidGenerator.generateUuidV7());
        event.setMissionId(missionId);
        event.setEventType(eventType);
        event.setMessage(message);
        event.setPayload(payload);
        event.setCreatedAt(LocalDateTime.now());
        missionEventMapper.insert(event);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordTrace(String missionId, String missionStepId, String traceType, Map<String, Object> payload) {
        AgentMissionTrace trace = new AgentMissionTrace();
        trace.setId(UuidGenerator.generateUuidV7());
        trace.setMissionId(missionId);
        trace.setMissionStepId(missionStepId);
        trace.setTraceType(traceType);
        trace.setPayload(payload);
        trace.setCreatedAt(LocalDateTime.now());
        missionTraceMapper.insert(trace);
    }

    @Override
    public MissionTaskStats summarize(String missionId) {
        long pending = missionTaskMapper.countByMissionAndStatuses(missionId, List.of(STATUS_PENDING, "RUNNING"));
        long completed = missionTaskMapper.countByMissionAndStatuses(missionId, List.of(STATUS_COMPLETED));
        long failed = missionTaskMapper.countByMissionAndStatuses(missionId, List.of(STATUS_FAILED, "CANCELLED"));
        return new MissionTaskStats(pending, completed, failed);
    }

    @Override
    public List<AgentMissionTask> listTasks(String missionId) {
        return missionTaskMapper.selectByMissionId(missionId);
    }

    @Override
    public List<AgentMissionEvent> listEvents(String missionId) {
        return missionEventMapper.selectByMissionId(missionId);
    }

    @Override
    public List<AgentMissionTrace> listTraces(String missionId) {
        return missionTraceMapper.selectByMissionId(missionId);
    }

    private AgentMissionTask requireTask(String externalTaskId) {
        AgentMissionTask task = missionTaskMapper.selectByExternalTaskId(externalTaskId);
        if (task == null) {
            throw new IllegalStateException("Mission task not found: " + externalTaskId);
        }
        return task;
    }
}
