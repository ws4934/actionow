package com.actionow.agent.dto.response;

import com.actionow.agent.entity.AgentMission;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Mission 详情响应
 *
 * @author Actionow
 */
@Data
@Builder
public class MissionResponse {

    private String id;
    private String workspaceId;
    private String runtimeSessionId;
    private String creatorId;
    private String title;
    private String goal;
    private Map<String, Object> plan;
    private String status;
    private Integer currentStep;
    private Integer progress;
    private Integer totalSteps;
    private Long totalCreditCost;
    private String errorMessage;
    private String resultSummary;
    private Map<String, Object> resultPayload;
    private String failureCode;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static MissionResponse fromEntity(AgentMission entity) {
        return MissionResponse.builder()
                .id(entity.getId())
                .workspaceId(entity.getWorkspaceId())
                .runtimeSessionId(entity.getRuntimeSessionId())
                .creatorId(entity.getCreatorId())
                .title(entity.getTitle())
                .goal(entity.getGoal())
                .plan(entity.getPlan())
                .status(entity.getStatus())
                .currentStep(entity.getCurrentStep())
                .progress(entity.getProgress())
                .totalSteps(entity.getTotalSteps())
                .totalCreditCost(entity.getTotalCreditCost())
                .errorMessage(entity.getErrorMessage())
                .resultSummary(entity.getResultSummary())
                .resultPayload(entity.getResultPayload())
                .failureCode(entity.getFailureCode())
                .startedAt(entity.getStartedAt())
                .completedAt(entity.getCompletedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
