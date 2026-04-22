package com.actionow.agent.saa.stream;

import com.actionow.agent.constant.AgentConstants;
import com.actionow.agent.core.agent.AgentResponse;
import com.actionow.agent.core.agent.AgentStreamEvent;
import com.actionow.agent.scriptwriting.tools.StructuredOutputTools;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SAA 流处理器
 * 替代 v1 的 StreamEventProcessor（ADK Event → AgentStreamEvent）
 * 将 Spring AI {@link ChatResponse} 转换为 {@link AgentStreamEvent}
 *
 * 职责：
 * 1. 事件转换 - ChatResponse → AgentStreamEvent
 * 2. Token 统计 - 提取和累计 token 使用信息
 * 3. 工具调用跟踪 - 管理 toolCallId 映射
 * 4. 内容去重 - 防止重复内容输出
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SaaStreamProcessor {

    private final ObjectMapper objectMapper;

    /**
     * 从 ChatResponse 中提取 token 使用信息
     */
    public AgentStreamEvent.TokenUsage extractTokenUsage(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return null;
        }

        try {
            Usage usage = response.getMetadata().getUsage();
            if (usage == null) {
                return null;
            }

            Long promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens().longValue() : null;
            Long completionTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens().longValue() : null;
            Long totalTokens = usage.getTotalTokens() != null ? usage.getTotalTokens().longValue() : null;

            if (promptTokens == null && completionTokens == null && totalTokens == null) {
                return null;
            }

            log.debug("Extracted token usage: prompt={}, completion={}, total={}",
                    promptTokens, completionTokens, totalTokens);

            return AgentStreamEvent.TokenUsage.builder()
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(totalTokens)
                    .build();
        } catch (Exception e) {
            log.debug("Failed to extract token usage: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 合并两个 TokenUsage（累加语义）
     * 多 Agent 场景下各自独立调用 LLM，需累加而非替换
     */
    public AgentStreamEvent.TokenUsage mergeTokenUsage(AgentStreamEvent.TokenUsage existing,
                                                        AgentStreamEvent.TokenUsage incoming) {
        if (existing == null) return incoming;
        if (incoming == null) return existing;

        return AgentStreamEvent.TokenUsage.builder()
                .promptTokens(addNullable(existing.getPromptTokens(), incoming.getPromptTokens()))
                .completionTokens(addNullable(existing.getCompletionTokens(), incoming.getCompletionTokens()))
                .cachedTokens(addNullable(existing.getCachedTokens(), incoming.getCachedTokens()))
                .thoughtTokens(addNullable(existing.getThoughtTokens(), incoming.getThoughtTokens()))
                .toolUsePromptTokens(addNullable(existing.getToolUsePromptTokens(), incoming.getToolUsePromptTokens()))
                .totalTokens(addNullable(existing.getTotalTokens(), incoming.getTotalTokens()))
                .build();
    }

    /**
     * 将 Spring AI Message 转换为 AgentStreamEvent（供 streamMessages() 回调使用）
     *
     * @param message            来自 supervisor.streamMessages() 的消息
     * @param iterationCount     迭代计数器
     * @param collectedToolEvents 收集的工具事件列表（用于后续持久化）
     * @return 转换后的流事件，如果无相关内容则返回 null
     */
    public List<AgentStreamEvent> convertMessageToEvents(Message message,
                                                          AtomicInteger iterationCount,
                                                          List<AgentStreamEvent> collectedToolEvents,
                                                          Map<String, String> toolSkillMapping) {
        List<AgentStreamEvent> events = new ArrayList<>();

        // Handle tool results (ToolResponseMessage from SAA tool execution)
        if (message instanceof ToolResponseMessage toolResponse) {
            List<ToolResponseMessage.ToolResponse> responses = toolResponse.getResponses();
            if (responses != null && !responses.isEmpty()) {
                ToolResponseMessage.ToolResponse resp = responses.get(0);
                String respSkillName = toolSkillMapping != null ? toolSkillMapping.get(resp.name()) : null;
                log.debug("Tool result received: toolCallId={}, toolName={}, skillName={}", resp.id(), resp.name(), respSkillName);
                AgentStreamEvent event = AgentStreamEvent.toolResult(
                        resp.id(), resp.name(), true, resp.responseData(), iterationCount.get(), respSkillName);
                collectedToolEvents.add(event);
                events.add(event);
            }
            return events;
        }

        if (!(message instanceof AssistantMessage assistantMessage)) {
            return events;
        }

        // 1. 提取工具调用
        List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();
        if (toolCalls != null && !toolCalls.isEmpty()) {
            for (AssistantMessage.ToolCall toolCall : toolCalls) {
                String toolName = toolCall.name();
                Map<String, Object> args = parseToolArguments(toolCall.arguments());
                String toolCallId = UUID.randomUUID().toString();

                String tcSkillName = toolSkillMapping != null ? toolSkillMapping.get(toolName) : null;
                log.debug("Tool call detected from Message: toolName={}, toolCallId={}, skillName={}", toolName, toolCallId, tcSkillName);
                AgentStreamEvent event = AgentStreamEvent.toolCall(toolCallId, toolName, args, iterationCount.get(), tcSkillName);
                collectedToolEvents.add(event);
                events.add(event);
            }
        }

        // 2. 同时保留文本内容，避免同一条 AssistantMessage 里 toolCalls 把最终总结吞掉
        String text = assistantMessage.getText();
        if (text != null && !text.isBlank()) {
            iterationCount.incrementAndGet();
            events.add(AgentStreamEvent.message(text));
        }

        return events;
    }

    /**
     * 组装最终 assistant 文本。
     * 优先级：
     * 1. 模型实际输出的自然语言
     * 2. output_structured_result 缓存的结构化结果
     * 3. 基于工具结果生成兜底总结
     */
    public String resolveFinalContent(String streamedContent, String sessionId, List<AgentStreamEvent> toolEvents) {
        if (streamedContent != null && !streamedContent.isBlank()) {
            return streamedContent;
        }

        Map<String, Object> structuredResult = StructuredOutputTools.getAndRemoveResult(sessionId);
        String structuredContent = extractStructuredResultContent(structuredResult);
        if (structuredContent != null && !structuredContent.isBlank()) {
            return structuredContent;
        }

        return buildToolSummary(toolEvents);
    }

    /**
     * 从 OverAllState 中提取文本内容（供 invoke() 同步调用结果使用）
     *
     * @param state supervisor.invoke() 返回的 OverAllState 对象
     * @return 最终响应文本，无内容时返回空字符串
     */
    public String extractContentFromState(Object state) {
        if (state instanceof com.alibaba.cloud.ai.graph.OverAllState overAllState) {
            Map<String, Object> data = overAllState.data();
            // SAA 在 "messages" 键下存储消息列表
            Object messages = data.get("messages");
            if (messages instanceof List<?> msgList && !msgList.isEmpty()) {
                Object last = msgList.get(msgList.size() - 1);
                if (last instanceof AssistantMessage am) {
                    String text = am.getText();
                    return text != null ? text : "";
                }
                if (last instanceof Message m) {
                    String text = m.getText();
                    return text != null ? text : "";
                }
            }
            // fallback: "output" / "result" 键
            Object output = data.get("output");
            if (output instanceof String s) return s;
            Object result = data.get("result");
            if (result instanceof String s) return s;
        }
        return "";
    }

    private String extractStructuredResultContent(Map<String, Object> structuredResult) {
        if (structuredResult == null || structuredResult.isEmpty()) {
            return null;
        }

        for (String key : List.of("message", "summary", "content", "text")) {
            Object value = structuredResult.get(key);
            if (value instanceof String text && !text.isBlank()) {
                return text;
            }
        }

        try {
            return objectMapper.writeValueAsString(structuredResult);
        } catch (Exception e) {
            log.debug("Failed to serialize structured result: {}", e.getMessage());
            return structuredResult.toString();
        }
    }

    private String buildToolSummary(List<AgentStreamEvent> toolEvents) {
        if (toolEvents == null || toolEvents.isEmpty()) {
            return "";
        }

        List<String> finishedTools = toolEvents.stream()
                .filter(event -> AgentConstants.EVENT_TOOL_RESULT.equals(event.getType()))
                .map(AgentStreamEvent::getToolName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();

        if (!finishedTools.isEmpty()) {
            return finishedTools.size() == 1
                    ? "已完成操作：" + finishedTools.get(0)
                    : "已完成 " + finishedTools.size() + " 个操作：" + String.join("、", finishedTools);
        }

        List<String> calledTools = toolEvents.stream()
                .filter(event -> AgentConstants.EVENT_TOOL_CALL.equals(event.getType()))
                .map(AgentStreamEvent::getToolName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();

        if (!calledTools.isEmpty()) {
            return calledTools.size() == 1
                    ? "已执行工具调用：" + calledTools.get(0)
                    : "已执行 " + calledTools.size() + " 个工具调用：" + String.join("、", calledTools);
        }

        return "";
    }

    // ==================== 工具分析方法（与 v1 相同） ====================

    public boolean isCreateTool(String toolName) {
        if (toolName == null || toolName.isBlank()) return false;
        String lowerName = toolName.toLowerCase();
        return lowerName.contains("create") ||
               lowerName.contains("batch_create") ||
               lowerName.contains("batchcreate");
    }

    public String extractTargetEntityType(String toolName) {
        if (toolName == null || toolName.isBlank()) return null;
        String lowerName = toolName.toLowerCase();
        Set<String> entityTypes = Set.of(
                "script", "episode", "storyboard", "character", "scene", "prop", "style", "asset");
        for (String entityType : entityTypes) {
            if (lowerName.contains(entityType)) return entityType.toUpperCase();
        }
        return null;
    }

    public String extractTargetEntityId(Map<String, Object> toolArguments) {
        if (toolArguments == null || toolArguments.isEmpty()) return null;
        String[] idFields = {
                "scriptId", "script_id", "episodeId", "episode_id",
                "storyboardId", "storyboard_id", "characterId", "character_id",
                "sceneId", "scene_id", "propId", "prop_id", "styleId", "style_id",
                "assetId", "asset_id", "id"
        };
        for (String field : idFields) {
            Object value = toolArguments.get(field);
            if (value != null && !value.toString().isBlank()) return value.toString();
        }
        return null;
    }

    public Map<String, Object> parseToolResultAsMap(String content) {
        if (content == null || content.isBlank()) return Map.of();
        try {
            if (content.startsWith("{")) {
                return objectMapper.readValue(content, new TypeReference<>() {});
            }
        } catch (Exception e) {
            log.debug("Failed to parse tool result as map: {}", e.getMessage());
        }
        return Map.of();
    }

    public List<AgentResponse.ToolCallInfo> buildToolCallInfos(List<AgentStreamEvent> toolEvents) {
        if (toolEvents == null || toolEvents.isEmpty()) {
            return List.of();
        }

        List<AgentResponse.ToolCallInfo> infos = new ArrayList<>();
        Map<String, Integer> latestIndexByToolName = new HashMap<>();

        for (AgentStreamEvent event : toolEvents) {
            if (AgentConstants.EVENT_TOOL_CALL.equals(event.getType())) {
                AgentResponse.ToolCallInfo info = AgentResponse.ToolCallInfo.builder()
                        .toolName(event.getToolName())
                        .arguments(event.getToolArguments())
                        .success(false)
                        .build();
                infos.add(info);
                if (event.getToolName() != null) {
                    latestIndexByToolName.put(event.getToolName(), infos.size() - 1);
                }
                continue;
            }

            if (!AgentConstants.EVENT_TOOL_RESULT.equals(event.getType())) {
                continue;
            }

            Map<String, Object> parsedResult = new LinkedHashMap<>(parseToolResultAsMap(event.getContent()));
            boolean success = !(parsedResult.get("success") instanceof Boolean value) || value;
            Integer index = event.getToolName() != null ? latestIndexByToolName.get(event.getToolName()) : null;

            AgentResponse.ToolCallInfo info = AgentResponse.ToolCallInfo.builder()
                    .toolName(event.getToolName())
                    .success(success)
                    .result(parsedResult.isEmpty() ? event.getContent() : parsedResult)
                    .build();

            if (index != null && index >= 0 && index < infos.size()) {
                AgentResponse.ToolCallInfo existing = infos.get(index);
                existing.setSuccess(success);
                existing.setResult(parsedResult.isEmpty() ? event.getContent() : parsedResult);
            } else {
                infos.add(info);
            }
        }

        return infos;
    }

    // ==================== 私有方法 ====================

    private Long addNullable(Long a, Long b) {
        if (a == null) return b;
        if (b == null) return a;
        return a + b;
    }

    private Map<String, Object> parseToolArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(arguments, new TypeReference<>() {});
        } catch (Exception e) {
            log.debug("Failed to parse tool arguments: {}", e.getMessage());
            return Map.of();
        }
    }
}
