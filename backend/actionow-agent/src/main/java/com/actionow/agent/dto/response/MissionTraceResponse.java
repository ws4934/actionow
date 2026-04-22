package com.actionow.agent.dto.response;

import com.actionow.agent.entity.AgentMissionTrace;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Mission 轨迹响应。
 */
@Data
@Builder
public class MissionTraceResponse {

    private String id;
    private String missionId;
    private String missionStepId;
    private String traceType;
    private Map<String, Object> payload;
    private LocalDateTime createdAt;

    public static MissionTraceResponse fromEntity(AgentMissionTrace entity) {
        return MissionTraceResponse.builder()
                .id(entity.getId())
                .missionId(entity.getMissionId())
                .missionStepId(entity.getMissionStepId())
                .traceType(entity.getTraceType())
                .payload(entity.getPayload())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
