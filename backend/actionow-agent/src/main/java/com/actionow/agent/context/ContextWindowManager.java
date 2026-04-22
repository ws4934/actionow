package com.actionow.agent.context;

import com.actionow.agent.billing.service.TokenCountingService;
import com.actionow.agent.context.memory.WorkingMemoryStore;
import com.actionow.agent.entity.AgentMessage;
import com.actionow.agent.metrics.AgentMetrics;
import com.actionow.agent.saa.factory.SaaChatModelFactory;
import com.actionow.agent.saa.session.AgentMessageCollapser;
import com.actionow.agent.saa.session.SaaSessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 上下文窗口管理器
 * 核心协调器：加载历史 → Layer 1 (micro-compact) → Layer 2 (auto-compact) → 转换为 Spring AI Messages。
 * 阈值基于 contextWindow * usageRatio（token 驱动，不使用消息数限制）。
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContextWindowManager {

    private final SaaSessionService sessionService;
    private final SaaChatModelFactory chatModelFactory;
    private final TokenCountingService tokenCountingService;
    private final MicroCompactor microCompactor;
    private final AutoCompactor autoCompactor;
    private final WorkingMemoryStore workingMemoryStore;
    private final ObjectMapper objectMapper;
    private final AgentMetrics agentMetrics;

    @Value("${actionow.context.context-window-usage-ratio:0.8}")
    private double usageRatio;

    /**
     * 是否在喂给 LLM 前合并相邻 assistant_segment 行。默认开启 —— 写入侧分段后，若不合并，
     * 模型回顾自己的历史会看到"一气呵成的一段话被切成 5 个 assistant turn"，对 few-shot
     * 风格敏感的模型（Gemini / Claude）容易误以为是多轮独立发言。
     */
    @Value("${actionow.agent.message.collapse-adjacent.enabled:true}")
    private boolean collapseAdjacentEnabled;

    /**
     * 构建完整的历史消息列表，适配模型窗口。
     *
     * @param sessionId     会话 ID
     * @param currentInput  当前用户输入（已增强后的文本）
     * @param llmProviderId LLM Provider ID（用于获取 contextWindow）
     * @return 可直接传给 supervisor.streamMessages(List) 的消息列表
     */
    public List<Message> buildContextMessages(String sessionId, String currentInput,
                                               String llmProviderId) {
        return buildContextMessages(sessionId, currentInput, llmProviderId, null);
    }

    /**
     * 构建完整的历史消息列表，适配模型窗口，并注入上下文系统提示词。
     *
     * @param sessionId            会话 ID
     * @param currentInput         当前用户输入（已增强后的文本）
     * @param llmProviderId        LLM Provider ID（用于获取 contextWindow）
     * @param contextSystemPrompt  上下文系统提示词（作用域/剧本/章节等信息），null 则不注入
     * @return 可直接传给 supervisor.streamMessages(List) 的消息列表
     */
    public List<Message> buildContextMessages(String sessionId, String currentInput,
                                               String llmProviderId, String contextSystemPrompt) {
        int contextWindow = chatModelFactory.getContextWindow(llmProviderId);
        int tokenBudget = (int) (contextWindow * usageRatio);

        // 1. 从 DB 加载全部历史（按 sequence 升序）
        List<AgentMessage> rawHistory = sessionService.getMessages(sessionId);

        if (rawHistory == null || rawHistory.isEmpty()) {
            List<Message> messages = new ArrayList<>();
            if (contextSystemPrompt != null) {
                messages.add(new SystemMessage(contextSystemPrompt));
            }
            messages.add(new UserMessage(currentInput));
            return messages;
        }

        // 过滤掉 generating/cancelled 等非完成态消息
        List<AgentMessage> completedHistory = rawHistory.stream()
                .filter(msg -> msg.getStatus() == null
                        || "completed".equals(msg.getStatus()))
                .toList();

        // 2. Layer 1: micro-compact（压缩旧 tool_result，READ/SEARCH 结果自动存入 Working Memory）
        List<AgentMessage> compacted = microCompactor.compact(sessionId, completedHistory);

        // 2.5 合并相邻 assistant_segment 行（每条 message 事件独立落行后，需要把"同一轮 ReAct
        //     里连续 assistant 段落"重新缝合成一条 turn 再喂给 LLM；tool/user 行天然打断合并组）
        if (collapseAdjacentEnabled) {
            try {
                compacted = AgentMessageCollapser.collapseEntities(
                        compacted, size -> agentMetrics.recordCollapseGroupSize(size, "llm"));
            } catch (Exception e) {
                log.warn("collapse adjacent assistant segments failed, fallback to raw list: sessionId={}, err={}",
                        sessionId, e.getMessage());
            }
        }

        // 3. 计算 token 并判断是否需要 Layer 2
        int currentInputTokens = tokenCountingService.countTokens(currentInput);
        int contextPromptTokens = contextSystemPrompt != null
                ? tokenCountingService.countTokens(contextSystemPrompt) : 0;
        int historyTokens = sumTokens(compacted);

        int totalTokens = historyTokens + currentInputTokens + contextPromptTokens;
        if (totalTokens > tokenBudget) {
            log.info("上下文超出窗口阈值: history={}+input={}+ctx={}={} > budget={}, 触发 auto-compact",
                    historyTokens, currentInputTokens, contextPromptTokens, totalTokens, tokenBudget);
            compacted = autoCompactor.compact(sessionId, compacted,
                    tokenBudget - currentInputTokens - contextPromptTokens, llmProviderId);
        }

        // 4. 转换为 Spring AI Message 列表
        List<Message> messages = convertToSpringMessages(compacted);

        // 4.5 注入上下文系统提示词（作用域、剧本/章节概要等，每轮动态生成不持久化）
        if (contextSystemPrompt != null) {
            injectContextSystemPrompt(contextSystemPrompt, messages);
        }

        // 4.6 注入 Working Memory 索引（如有缓存数据，提示 Agent 可用 recall_from_memory 取回）
        injectWorkingMemoryIndex(sessionId, messages);

        // 5. 追加当前用户输入
        messages.add(new UserMessage(currentInput));

        // 6. 合并所有 SystemMessage 为唯一一条（Gemini 只允许一条 system message；
        //    历史中的 CHECKPOINT + 当轮 contextSystemPrompt 可能会累计出多条）
        messages = coalesceSystemMessages(messages);

        log.debug("Context 构建完成: sessionId={}, historyMessages={}, totalTokens≈{}",
                sessionId, messages.size() - 1, sumTokens(compacted) + currentInputTokens + contextPromptTokens);

        return messages;
    }

    /**
     * 把列表中所有 {@link SystemMessage} 合并成唯一一条，放到列表最前面。
     * 保留原先的相对顺序（先出现的在前），多条之间用空行分隔。
     * <p>动机：Google Gemini 的 {@code GoogleGenAiChatModel.createGeminiRequest} 会通过
     * {@code Assert.isTrue} 强制"只允许一条 system message"；历史 CHECKPOINT（role=system）
     * + 当轮 {@code contextSystemPrompt} + SAA 框架自身的 systemPrompt 容易触发。
     * 对 OpenAI / Anthropic 也是安全的——多 system message 对这些模型只是语义上的合并即可。
     */
    private List<Message> coalesceSystemMessages(List<Message> messages) {
        StringBuilder merged = null;
        List<Message> nonSystem = new ArrayList<>(messages.size());
        for (Message msg : messages) {
            if (msg instanceof SystemMessage sys) {
                String text = sys.getText();
                if (text == null || text.isBlank()) continue;
                if (merged == null) {
                    merged = new StringBuilder(text);
                } else {
                    merged.append("\n\n").append(text);
                }
            } else {
                nonSystem.add(msg);
            }
        }
        if (merged == null) {
            return nonSystem;
        }
        List<Message> result = new ArrayList<>(nonSystem.size() + 1);
        result.add(new SystemMessage(merged.toString()));
        result.addAll(nonSystem);
        return result;
    }

    /**
     * 强制压缩（供 compact_context 工具调用）。
     *
     * @return [beforeTokens, afterTokens]
     */
    public int[] forceCompact(String sessionId, String llmProviderId) {
        int contextWindow = chatModelFactory.getContextWindow(llmProviderId);
        int tokenBudget = (int) (contextWindow * usageRatio);

        List<AgentMessage> rawHistory = sessionService.getMessages(sessionId);
        if (rawHistory == null || rawHistory.isEmpty()) {
            return new int[]{0, 0};
        }

        List<AgentMessage> completedHistory = rawHistory.stream()
                .filter(msg -> msg.getStatus() == null || "completed".equals(msg.getStatus()))
                .toList();

        List<AgentMessage> compacted = microCompactor.compact(sessionId, completedHistory);
        if (collapseAdjacentEnabled) {
            try {
                compacted = AgentMessageCollapser.collapseEntities(
                        compacted, size -> agentMetrics.recordCollapseGroupSize(size, "llm"));
            } catch (Exception e) {
                log.warn("collapse adjacent assistant segments failed (forceCompact): sessionId={}, err={}",
                        sessionId, e.getMessage());
            }
        }
        return autoCompactor.forceCompact(sessionId, compacted, tokenBudget, llmProviderId);
    }

    /**
     * 统计消息列表的 token 总数（包括 tool 消息，因为它们现在会被发送给模型）。
     */
    private int sumTokens(List<AgentMessage> messages) {
        int total = 0;
        for (AgentMessage msg : messages) {
            if (msg.getTokenCount() != null && msg.getTokenCount() > 0) {
                total += msg.getTokenCount();
            } else if (msg.getContent() != null) {
                total += tokenCountingService.countTokens(msg.getContent());
            }
        }
        return total;
    }

    /**
     * 将 AgentMessage 列表转换为 Spring AI Message 列表。
     * <p>
     * 重建 tool 消息的完整配对：从连续的 role=tool 消息中提取 tool call 和 tool result，
     * 构建合法的 AssistantMessage(toolCalls) + ToolResponseMessage 序列。
     */
    private List<Message> convertToSpringMessages(List<AgentMessage> agentMessages) {
        List<Message> messages = new ArrayList<>();
        int i = 0;

        while (i < agentMessages.size()) {
            AgentMessage msg = agentMessages.get(i);
            String role = msg.getRole() != null ? msg.getRole() : "user";

            if ("tool".equals(role)) {
                // 收集连续的 tool 消息块，重建 AssistantMessage(toolCalls) + ToolResponseMessage
                List<AgentMessage> toolBlock = new ArrayList<>();
                while (i < agentMessages.size() && "tool".equals(
                        agentMessages.get(i).getRole() != null ? agentMessages.get(i).getRole() : "")) {
                    toolBlock.add(agentMessages.get(i));
                    i++;
                }
                rebuildToolMessages(toolBlock, messages);
                continue;
            }

            if (msg.getContent() != null && !msg.getContent().isBlank()) {
                switch (role) {
                    case "user" -> messages.add(new UserMessage(msg.getContent()));
                    case "assistant" -> messages.add(new AssistantMessage(msg.getContent()));
                    case "system" -> messages.add(new SystemMessage(msg.getContent()));
                    default -> log.debug("跳过 role={} 消息: sessionId={}", role, msg.getSessionId());
                }
            }
            i++;
        }

        return messages;
    }

    /**
     * 从 tool 消息块中重建 AssistantMessage(toolCalls) + ToolResponseMessage 配对。
     * <p>
     * tool call 消息: toolArguments 非空, toolResult 为空
     * tool result 消息: toolResult 非空
     */
    private void rebuildToolMessages(List<AgentMessage> toolBlock, List<Message> output) {
        // 分离 tool calls 和 tool results
        List<AgentMessage> calls = new ArrayList<>();
        Map<String, AgentMessage> resultsByCallId = new LinkedHashMap<>();

        for (AgentMessage msg : toolBlock) {
            if (msg.getToolResult() != null && !msg.getToolResult().isEmpty()) {
                if (msg.getToolCallId() != null) {
                    resultsByCallId.put(msg.getToolCallId(), msg);
                }
            } else if (msg.getToolCallId() != null) {
                calls.add(msg);
            }
        }

        if (calls.isEmpty()) {
            return;
        }

        // 构建合成的 AssistantMessage(toolCalls)
        List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
        for (AgentMessage call : calls) {
            String arguments = "{}";
            if (call.getToolArguments() != null) {
                try {
                    arguments = objectMapper.writeValueAsString(call.getToolArguments());
                } catch (Exception e) {
                    log.debug("Failed to serialize tool arguments: {}", e.getMessage());
                }
            }
            toolCalls.add(new AssistantMessage.ToolCall(
                    call.getToolCallId(), "function",
                    call.getToolName() != null ? call.getToolName() : "unknown",
                    arguments));
        }
        output.add(AssistantMessage.builder().content("").toolCalls(toolCalls).build());

        // 构建 ToolResponseMessage（按 call 顺序匹配结果）
        List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
        for (AgentMessage call : calls) {
            AgentMessage result = resultsByCallId.get(call.getToolCallId());
            // Gemini API 要求 tool response 必须是有效 JSON，非 JSON 内容需要包装
            String responseData;
            if (result != null && result.getContent() != null) {
                responseData = ensureJson(result.getContent());
            } else {
                responseData = "{\"result\": \"no result available\"}";
            }
            responses.add(new ToolResponseMessage.ToolResponse(
                    call.getToolCallId(),
                    call.getToolName() != null ? call.getToolName() : "unknown",
                    responseData));
        }
        if (!responses.isEmpty()) {
            output.add(ToolResponseMessage.builder().responses(responses).build());
        }
    }

    /**
     * 注入上下文系统提示词（作用域、剧本/章节概要等）。
     * 合并到已有 SystemMessage 末尾，或在消息列表开头插入新的 SystemMessage。
     * 与 injectWorkingMemoryIndex 共享合并策略，避免多 SystemMessage 兼容性问题。
     */
    private void injectContextSystemPrompt(String contextSystemPrompt, List<Message> messages) {
        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            if (msg instanceof SystemMessage sysMsg) {
                messages.set(i, new SystemMessage(sysMsg.getText() + "\n\n" + contextSystemPrompt));
                return;
            }
        }
        messages.add(0, new SystemMessage(contextSystemPrompt));
    }

    /**
     * 如果 Working Memory 中有缓存数据，将索引摘要追加到已有的 SystemMessage 中。
     * 优先合并到第一个 SystemMessage 末尾，避免多 SystemMessage 的模型兼容性问题。
     * 如果不存在 SystemMessage，则作为 UserMessage 以 [系统提示] 前缀注入。
     */
    private void injectWorkingMemoryIndex(String sessionId, List<Message> messages) {
        String index = workingMemoryStore.buildIndexSummary(sessionId);
        if (index == null) {
            return;
        }

        // 寻找第一个 SystemMessage 并追加
        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            if (msg instanceof SystemMessage sysMsg) {
                String enriched = sysMsg.getText() + "\n\n" + index;
                messages.set(i, new SystemMessage(enriched));
                log.debug("Injected working memory index into existing SystemMessage: sessionId={}, entries={}",
                        sessionId, workingMemoryStore.size(sessionId));
                return;
            }
        }

        // 没有 SystemMessage，在消息列表末尾追加为 UserMessage（对所有模型兼容）
        messages.add(new UserMessage("[系统提示] " + index));
        log.debug("Injected working memory index as UserMessage: sessionId={}, entries={}",
                sessionId, workingMemoryStore.size(sessionId));
    }

    /**
     * 确保字符串是有效 JSON。Gemini API 的 tool response 必须为 JSON 格式，
     * 纯文本内容会导致 GoogleGenAiChatModel.parseJsonToMap 抛出 JsonParseException。
     */
    private String ensureJson(String content) {
        if (content == null || content.isBlank()) {
            return "{\"result\": \"empty\"}";
        }
        String trimmed = content.trim();
        if ((trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            return content;
        }
        // 纯文本内容包装为 JSON
        try {
            return objectMapper.writeValueAsString(Map.of("result", content));
        } catch (Exception e) {
            return "{\"result\": \"parse error\"}";
        }
    }
}
