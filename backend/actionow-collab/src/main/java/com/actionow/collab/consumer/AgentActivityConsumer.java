package com.actionow.collab.consumer;

import com.actionow.collab.constant.CollabConstants;
import com.actionow.collab.dto.message.OutboundMessage;
import com.actionow.collab.websocket.CollaborationHub;
import com.actionow.common.mq.message.AgentActivityEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent 活动消息消费者
 * 监听来自 actionow-agent 的 Agent 活动事件，广播给相关用户
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentActivityConsumer {

    private final CollaborationHub collaborationHub;
    private final ObjectMapper objectMapper;

    /**
     * 从 MessageWrapper 中提取 AgentActivityEvent
     * 消息格式: { "messageId": "...", "payload": { ...实际数据... }, ... }
     */
    private AgentActivityEvent extractEvent(Message message) throws Exception {
        // 先解析为通用 Map 获取 payload
        Map<String, Object> wrapper = objectMapper.readValue(message.getBody(),
                new TypeReference<Map<String, Object>>() {});

        Object payload = wrapper.get("payload");
        if (payload == null) {
            // 如果没有 payload 字段，尝试直接解析（兼容直接发送的情况）
            return objectMapper.readValue(message.getBody(), AgentActivityEvent.class);
        }

        // 将 payload 转换为 AgentActivityEvent
        return objectMapper.convertValue(payload, AgentActivityEvent.class);
    }

    /**
     * 监听 Agent 活动事件
     */
    @RabbitListener(queues = CollabConstants.MqQueue.AGENT_ACTIVITY)
    public void handleAgentActivity(Message message, Channel channel,
                                    @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            AgentActivityEvent event = extractEvent(message);
            log.info("Agent activity: type={}, sessionId={}, tool={}, scriptId={}",
                    event.getActivityType(), event.getSessionId(),
                    event.getToolName(), event.getScriptId());

            if (event.getScriptId() != null) {
                Map<String, Object> data = buildActivityData(event);
                collaborationHub.sendToScript(event.getScriptId(), OutboundMessage.of(
                        CollabConstants.MessageType.AGENT_ACTIVITY,
                        data
                ));
            }
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("Failed to handle agent activity event", e);
            try {
                channel.basicNack(deliveryTag, false, false);
            } catch (Exception nackEx) {
                log.error("Failed to nack agent activity message", nackEx);
            }
        }
    }

    /**
     * 构建活动数据
     */
    private Map<String, Object> buildActivityData(AgentActivityEvent event) {
        Map<String, Object> data = new HashMap<>();
        data.put("activityType", event.getActivityType());
        data.put("sessionId", event.getSessionId());
        data.put("agentType", event.getAgentType());
        data.put("userId", event.getUserId() != null ? event.getUserId() : "");
        data.put("timestamp", event.getTimestamp() != null ? event.getTimestamp().toString() : "");

        // 工具相关信息
        if (event.getToolName() != null) {
            data.put("toolName", event.getToolName());
        }
        if (event.getToolCallId() != null) {
            data.put("toolCallId", event.getToolCallId());
        }
        if (event.getToolArguments() != null) {
            data.put("toolArguments", event.getToolArguments());
        }
        if (event.getToolResult() != null) {
            data.put("toolResult", event.getToolResult());
        }

        // 目标实体信息
        if (event.getTargetEntityType() != null) {
            data.put("targetEntityType", event.getTargetEntityType());
        }
        if (event.getTargetEntityId() != null) {
            data.put("targetEntityId", event.getTargetEntityId());
        }

        // 额外数据
        if (event.getExtras() != null) {
            data.put("extras", event.getExtras());
        }

        return data;
    }
}
