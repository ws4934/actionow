package com.actionow.agent.dto.response;

import com.actionow.agent.entity.AgentMissionStep;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Mission Step 响应
 *
 * @author Actionow
 */
@Data
@Builder
public class MissionStepResponse {

    private String id;
    private String missionId;
    private Integer stepNumber;
    private String stepType;
    private String inputSummary;
    private String outputSummary;
    private List<Map<String, Object>> toolCalls;
    private String status;
    private Long durationMs;
    private Long creditCost;
    private Long inputTokens;
    private Long outputTokens;
    private String modelName;
    private Map<String, Object> artifacts;
    private String decisionType;
    private Map<String, Object> decisionPayload;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;

    public static MissionStepResponse fromEntity(AgentMissionStep entity) {
        return MissionStepResponse.builder()
                .id(entity.getId())
                .missionId(entity.getMissionId())
                .stepNumber(entity.getStepNumber())
                .stepType(entity.getStepType())
                .inputSummary(entity.getInputSummary())
                .outputSummary(entity.getOutputSummary())
                .toolCalls(entity.getToolCalls())
                .status(entity.getStatus())
                .durationMs(entity.getDurationMs())
                .creditCost(entity.getCreditCost())
                .inputTokens(entity.getInputTokens())
                .outputTokens(entity.getOutputTokens())
                .modelName(entity.getModelName())
                .artifacts(entity.getArtifacts())
                .decisionType(entity.getDecisionType())
                .decisionPayload(entity.getDecisionPayload())
                .startedAt(entity.getStartedAt())
                .completedAt(entity.getCompletedAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
