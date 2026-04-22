package com.actionow.agent.service.impl;

import com.actionow.agent.constant.AgentConstants;
import com.actionow.agent.entity.AgentMission;
import com.actionow.agent.saa.session.SaaSessionService;
import com.actionow.agent.service.MissionEventProjector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Mission 终态投影器。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MissionEventProjectorImpl implements MissionEventProjector {

    private final SaaSessionService sessionService;

    @Override
    public void projectTerminalState(AgentMission mission, String status, String summary, Map<String, Object> payload) {
        if (mission == null || mission.getRuntimeSessionId() == null || mission.getRuntimeSessionId().isBlank()) {
            return;
        }

        try {
            var runtimeSession = sessionService.getSessionEntity(mission.getRuntimeSessionId());
            if (runtimeSession == null || runtimeSession.getExtras() == null) {
                return;
            }

            Object sourceSessionId = runtimeSession.getExtras().get("sourceSessionId");
            if (sourceSessionId == null || sourceSessionId.toString().isBlank()) {
                return;
            }

            Map<String, Object> extras = new HashMap<>();
            extras.put("messageKind", "MISSION_CARD");
            extras.put("missionId", mission.getId());
            extras.put("status", status);
            extras.put("title", mission.getTitle());
            extras.put("resultSummary", summary);
            if (payload != null && !payload.isEmpty()) {
                extras.put("missionPayload", payload);
            }

            String content = switch (status) {
                case "COMPLETED" -> "Mission 已完成: " + (summary != null ? summary : mission.getTitle());
                case "FAILED" -> "Mission 执行失败: " + (summary != null ? summary : mission.getTitle());
                case "CANCELLED" -> "Mission 已取消: " + mission.getTitle();
                default -> "Mission 状态更新: " + status;
            };

            sessionService.saveAssistantMessageWithExtras(
                    sourceSessionId.toString(),
                    content,
                    null,
                    extras
            );
        } catch (Exception e) {
            log.warn("Mission 终态投影失败: missionId={}, error={}", mission.getId(), e.getMessage());
        }
    }
}
