package com.actionow.agent.dto.response;

import com.actionow.agent.entity.AgentMissionEvent;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Mission 事件响应。
 */
@Data
@Builder
public class MissionEventResponse {

    private String id;
    private String missionId;
    private String eventType;
    private String message;
    private Map<String, Object> payload;
    private LocalDateTime createdAt;

    public static MissionEventResponse fromEntity(AgentMissionEvent entity) {
        return MissionEventResponse.builder()
                .id(entity.getId())
                .missionId(entity.getMissionId())
                .eventType(entity.getEventType())
                .message(entity.getMessage())
                .payload(entity.getPayload())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
