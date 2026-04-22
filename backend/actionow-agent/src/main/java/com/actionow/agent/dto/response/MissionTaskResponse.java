package com.actionow.agent.dto.response;

import com.actionow.agent.entity.AgentMissionTask;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Mission 任务响应。
 */
@Data
@Builder
public class MissionTaskResponse {

    private String id;
    private String missionId;
    private String missionStepId;
    private String taskKind;
    private String externalTaskId;
    private String batchJobId;
    private String entityType;
    private String entityId;
    private String status;
    private Map<String, Object> requestPayload;
    private Map<String, Object> resultPayload;
    private String failureCode;
    private String failureMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;

    public static MissionTaskResponse fromEntity(AgentMissionTask entity) {
        return MissionTaskResponse.builder()
                .id(entity.getId())
                .missionId(entity.getMissionId())
                .missionStepId(entity.getMissionStepId())
                .taskKind(entity.getTaskKind())
                .externalTaskId(entity.getExternalTaskId())
                .batchJobId(entity.getBatchJobId())
                .entityType(entity.getEntityType())
                .entityId(entity.getEntityId())
                .status(entity.getStatus())
                .requestPayload(entity.getRequestPayload())
                .resultPayload(entity.getResultPayload())
                .failureCode(entity.getFailureCode())
                .failureMessage(entity.getFailureMessage())
                .startedAt(entity.getStartedAt())
                .completedAt(entity.getCompletedAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
