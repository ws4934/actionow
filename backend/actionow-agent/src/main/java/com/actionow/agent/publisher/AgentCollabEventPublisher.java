package com.actionow.agent.publisher;

import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.mq.constant.MqConstants;
import com.actionow.common.mq.message.AgentActivityEvent;
import com.actionow.common.mq.producer.MessageProducer;
import com.actionow.agent.core.scope.AgentContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Agent 协作事件发布器
 * 向协作服务发送 Agent 活动事件
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentCollabEventPublisher {

    private final MessageProducer messageProducer;

    /**
     * 发布 Agent 开始执行事件
     */
    @Async
    public void publishAgentStarted(String sessionId, String agentType) {
        AgentActivityEvent event = buildBaseEvent(sessionId, agentType)
                .activityType(AgentActivityEvent.ActivityType.AGENT_STARTED)
                .build();

        sendEvent(event);
        log.debug("Published agent started: sessionId={}, agentType={}", sessionId, agentType);
    }

    /**
     * 发布 Agent 执行完成事件
     */
    @Async
    public void publishAgentCompleted(String sessionId, String agentType, Map<String, Object> extras) {
        AgentActivityEvent event = buildBaseEvent(sessionId, agentType)
                .activityType(AgentActivityEvent.ActivityType.AGENT_COMPLETED)
                .extras(extras)
                .build();

        sendEvent(event);
        log.debug("Published agent completed: sessionId={}", sessionId);
    }

    /**
     * 发布 Agent 执行失败事件
     */
    @Async
    public void publishAgentFailed(String sessionId, String agentType, String error) {
        AgentActivityEvent event = buildBaseEvent(sessionId, agentType)
                .activityType(AgentActivityEvent.ActivityType.AGENT_FAILED)
                .extras(Map.of("error", error != null ? error : "Unknown error"))
                .build();

        sendEvent(event);
        log.debug("Published agent failed: sessionId={}, error={}", sessionId, error);
    }

    /**
     * 发布工具调用事件
     */
    @Async
    public void publishToolCall(String sessionId, String agentType, String toolCallId,
                                 String toolName, Map<String, Object> toolArguments,
                                 String targetEntityType, String targetEntityId) {
        AgentActivityEvent event = buildBaseEvent(sessionId, agentType)
                .activityType(AgentActivityEvent.ActivityType.TOOL_CALL)
                .toolCallId(toolCallId)
                .toolName(toolName)
                .toolArguments(toolArguments)
                .targetEntityType(targetEntityType)
                .targetEntityId(targetEntityId)
                .build();

        sendEvent(event);
        log.debug("Published tool call: sessionId={}, tool={}, targetEntity={}:{}",
                sessionId, toolName, targetEntityType, targetEntityId);
    }

    /**
     * 发布工具执行结果事件
     */
    @Async
    public void publishToolResult(String sessionId, String agentType, String toolCallId,
                                   String toolName, Map<String, Object> toolResult,
                                   String targetEntityType, String targetEntityId) {
        AgentActivityEvent event = buildBaseEvent(sessionId, agentType)
                .activityType(AgentActivityEvent.ActivityType.TOOL_RESULT)
                .toolCallId(toolCallId)
                .toolName(toolName)
                .toolResult(toolResult)
                .targetEntityType(targetEntityType)
                .targetEntityId(targetEntityId)
                .build();

        sendEvent(event);
        log.debug("Published tool result: sessionId={}, tool={}", sessionId, toolName);
    }

    /**
     * 发布 Agent 思考中事件
     */
    @Async
    public void publishThinking(String sessionId, String agentType) {
        AgentActivityEvent event = buildBaseEvent(sessionId, agentType)
                .activityType(AgentActivityEvent.ActivityType.THINKING)
                .build();

        sendEvent(event);
        log.trace("Published agent thinking: sessionId={}", sessionId);
    }

    /**
     * 构建基础事件
     */
    private AgentActivityEvent.AgentActivityEventBuilder buildBaseEvent(String sessionId, String agentType) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        String scriptId = null;

        // 尝试从 AgentContext 获取 scriptId
        try {
            var context = AgentContextHolder.getContext();
            if (context != null) {
                scriptId = context.getScriptId();
            }
        } catch (Exception e) {
            log.trace("Failed to get AgentContext: {}", e.getMessage());
        }

        return AgentActivityEvent.builder()
                .sessionId(sessionId)
                .workspaceId(workspaceId)
                .scriptId(scriptId)
                .userId(userId)
                .agentType(agentType)
                .timestamp(LocalDateTime.now());
    }

    private void sendEvent(AgentActivityEvent event) {
        // 只有当 scriptId 存在时才发送事件（协作基于 script）
        if (event.getScriptId() == null) {
            log.trace("Skipping agent activity event: no scriptId, sessionId={}", event.getSessionId());
            return;
        }

        try {
            messageProducer.send(
                    MqConstants.EXCHANGE_COLLAB,
                    MqConstants.Collab.ROUTING_AGENT_ACTIVITY,
                    MqConstants.Collab.MSG_AGENT_ACTIVITY,
                    event
            );
        } catch (Exception e) {
            log.error("Failed to publish agent activity event: sessionId={}, error={}",
                    event.getSessionId(), e.getMessage(), e);
        }
    }
}
