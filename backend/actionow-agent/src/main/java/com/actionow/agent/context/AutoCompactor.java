package com.actionow.agent.context;

import com.actionow.agent.billing.service.TokenCountingService;
import com.actionow.agent.entity.AgentMessage;
import com.actionow.agent.saa.factory.SaaChatModelFactory;
import com.actionow.agent.saa.session.SaaSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Layer 2: auto-compact
 * 当活跃消息 token 超过阈值时，将旧消息压缩为 checkpoint 摘要。
 * Checkpoint 以 role=system, extras.type=CHECKPOINT 持久化到 t_agent_message。
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoCompactor {

    private final SaaChatModelFactory chatModelFactory;
    private final SaaSessionService sessionService;
    private final TokenCountingService tokenCountingService;

    @Value("${actionow.context.auto-compact-recent-rounds:4}")
    private int recentRoundsToKeep;

    /** 用于生成摘要的低成本 LLM Provider ID。为空时回退到调用方传入的 llmProviderId。 */
    @Value("${actionow.context.summary-llm-provider-id:}")
    private String summaryLlmProviderId;

    /** 摘要 LLM 调用超时（秒）。超时后立即走 fallbackTruncate，避免阻塞用户 turn。 */
    @Value("${actionow.context.summary-timeout-seconds:20}")
    private int summaryTimeoutSeconds;

    private static final String CHECKPOINT_TYPE = "CHECKPOINT";

    private static final String CHECKPOINT_PROMPT = """
            请将以下对话历史总结为简洁的上下文摘要，保留：
            1. 用户的核心意图和偏好
            2. 已完成的关键操作及其结果（包括实体 ID）
            3. 未完成的待办事项
            4. 重要的上下文信息（当前操作的剧本、角色等）
            5. 如果历史中有 "已缓存到工作记忆 key=..." 的提示，请在摘要中保留这些 key 信息，
               标注为"可通过 recall_from_memory(key=\"xxx\") 取回完整数据"
            不要遗漏任何实体 ID 或操作结果。用中文输出。

            对话历史：
            %s
            """;

    /**
     * 自动压缩：生成 checkpoint + 保留最近 N 轮。
     *
     * @param sessionId     会话 ID
     * @param messages      Layer 1 压缩后的消息列表
     * @param tokenBudget   历史消息可用的 token 预算（已扣除当前输入）
     * @param llmProviderId 用于生成摘要的 LLM
     * @return 压缩后的消息列表
     */
    public List<AgentMessage> compact(String sessionId, List<AgentMessage> messages,
                                       int tokenBudget, String llmProviderId) {
        int recentBoundary = MicroCompactor.findRecentBoundary(messages, recentRoundsToKeep);

        List<AgentMessage> oldMessages = messages.stream()
                .filter(m -> m.getSequence() != null && m.getSequence() < recentBoundary)
                .toList();
        List<AgentMessage> recentMessages = messages.stream()
                .filter(m -> m.getSequence() == null || m.getSequence() >= recentBoundary)
                .toList();

        if (oldMessages.isEmpty()) {
            return messages;
        }

        // 检查是否已有 checkpoint
        AgentMessage existingCheckpoint = findLatestCheckpoint(messages);

        // 只压缩 checkpoint 之后的旧消息
        List<AgentMessage> toSummarize;
        if (existingCheckpoint != null) {
            int cpSequence = existingCheckpoint.getSequence() != null ? existingCheckpoint.getSequence() : 0;
            toSummarize = oldMessages.stream()
                    .filter(m -> m.getSequence() != null && m.getSequence() > cpSequence)
                    .filter(m -> !isCheckpoint(m))
                    .toList();
        } else {
            toSummarize = oldMessages.stream()
                    .filter(m -> !isCheckpoint(m))
                    .toList();
        }

        if (toSummarize.isEmpty()) {
            // 已有 checkpoint 且没有新的旧消息
            List<AgentMessage> result = new ArrayList<>();
            if (existingCheckpoint != null) result.add(existingCheckpoint);
            result.addAll(recentMessages);
            return result;
        }

        // 生成 checkpoint 摘要
        try {
            String historyText = formatMessagesForSummary(toSummarize);
            String previousCheckpointText = existingCheckpoint != null
                    ? existingCheckpoint.getContent() : "";
            String combinedInput = previousCheckpointText.isEmpty()
                    ? historyText
                    : "【前一次摘要】\n" + previousCheckpointText + "\n\n【新增对话】\n" + historyText;

            String summary = generateSummary(combinedInput, llmProviderId);
            int lastSequence = toSummarize.getLast().getSequence() != null
                    ? toSummarize.getLast().getSequence() : 0;

            // 持久化 checkpoint
            String checkpointContent = "【对话上下文摘要】\n" + summary;
            AgentMessage checkpoint = sessionService.saveMessage(
                    sessionId, "system", checkpointContent,
                    tokenCountingService.countTokens(checkpointContent));
            // 设置 extras 标记
            Map<String, Object> extras = new LinkedHashMap<>();
            extras.put("type", CHECKPOINT_TYPE);
            extras.put("compactedSequenceEnd", lastSequence);
            checkpoint.setExtras(extras);
            sessionService.updateMessageExtras(sessionId, checkpoint.getId(), extras);

            log.info("Auto-compact 生成 checkpoint: sessionId={}, compactedMessages={}, checkpointTokens={}",
                    sessionId, toSummarize.size(), checkpoint.getTokenCount());

            List<AgentMessage> result = new ArrayList<>();
            result.add(checkpoint);
            result.addAll(recentMessages);
            return result;

        } catch (Exception e) {
            log.warn("Auto-compact 失败，回退到截断模式: sessionId={}, error={}", sessionId, e.getMessage());
            return fallbackTruncate(messages, recentMessages, tokenBudget);
        }
    }

    /**
     * 强制压缩（manual compact 调用）。不受阈值限制。
     *
     * @return 压缩前后 token 统计 [beforeTokens, afterTokens]
     */
    public int[] forceCompact(String sessionId, List<AgentMessage> messages,
                               int tokenBudget, String llmProviderId) {
        int beforeTokens = messages.stream()
                .mapToInt(m -> m.getTokenCount() != null ? m.getTokenCount() : 0)
                .sum();

        List<AgentMessage> result = compact(sessionId, messages, tokenBudget, llmProviderId);

        int afterTokens = result.stream()
                .mapToInt(m -> m.getTokenCount() != null ? m.getTokenCount() : 0)
                .sum();

        return new int[]{beforeTokens, afterTokens};
    }

    /**
     * 调用摘要 LLM，带硬超时保护：超过 summaryTimeoutSeconds 直接抛出以让调用方走 fallback。
     * <p>通过 Reactor {@code stream().timeout().block()} 链路获得**真可取消**的超时语义：
     * Reactor 在 timeout 触发时会沿流向上发 cancel，WebClient/Reactor-Netty 传输层会真正关闭底层 HTTP 连接，
     * 不会像 {@code Future.cancel(true)} 那样留下仍在阻塞的工作线程。
     * <p>若 ChatModel 不是 StreamingChatModel（比如 {@code UnavailableChatModel} 的阻塞实现），
     * 回退到阻塞 call() 但仍包一层 Mono 超时做防护。
     */
    private String generateSummary(String input, String llmProviderId) {
        String effectiveProviderId = (summaryLlmProviderId != null && !summaryLlmProviderId.isBlank())
                ? summaryLlmProviderId
                : llmProviderId;
        ChatModel model = chatModelFactory.createModel(effectiveProviderId);
        String promptText = String.format(CHECKPOINT_PROMPT, input);
        Prompt prompt = new Prompt(new UserMessage(promptText));
        Duration timeout = Duration.ofSeconds(summaryTimeoutSeconds);

        try {
            if (model instanceof StreamingChatModel streamingModel) {
                return streamingModel.stream(prompt)
                        .map(AutoCompactor::extractText)
                        .filter(s -> s != null && !s.isEmpty())
                        .collect(StringBuilder::new, StringBuilder::append)
                        .map(StringBuilder::toString)
                        .timeout(timeout)
                        .block(timeout);
            }
            return reactor.core.publisher.Mono.fromCallable(() -> model.call(prompt))
                    .map(resp -> extractText(resp) != null ? extractText(resp) : "")
                    .timeout(timeout)
                    .block(timeout);
        } catch (Exception e) {
            Throwable root = e.getCause() != null ? e.getCause() : e;
            throw new RuntimeException("Summary LLM 调用失败或超时 (>" + summaryTimeoutSeconds + "s): " + root.getMessage(), root);
        }
    }

    private static String extractText(ChatResponse resp) {
        if (resp == null || resp.getResult() == null || resp.getResult().getOutput() == null) {
            return null;
        }
        return resp.getResult().getOutput().getText();
    }

    @org.springframework.lang.Nullable
    private AgentMessage findLatestCheckpoint(List<AgentMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (isCheckpoint(messages.get(i))) {
                return messages.get(i);
            }
        }
        return null;
    }

    static boolean isCheckpoint(AgentMessage msg) {
        return "system".equals(msg.getRole())
                && msg.getExtras() != null
                && CHECKPOINT_TYPE.equals(msg.getExtras().get("type"));
    }

    private String formatMessagesForSummary(List<AgentMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (AgentMessage msg : messages) {
            String role = msg.getRole() != null ? msg.getRole() : "unknown";
            String content = msg.getContent() != null ? msg.getContent() : "";

            if ("tool".equals(role) && msg.getToolName() != null) {
                sb.append("[tool:").append(msg.getToolName()).append("] ").append(content).append("\n");
            } else {
                sb.append("[").append(role).append("] ").append(content).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 回退策略：LLM 摘要失败时，直接按 token 从旧到新截断。
     */
    private List<AgentMessage> fallbackTruncate(List<AgentMessage> allMessages,
                                                  List<AgentMessage> recentMessages,
                                                  int tokenBudget) {
        int recentTokens = recentMessages.stream()
                .mapToInt(m -> m.getTokenCount() != null ? m.getTokenCount() : 0)
                .sum();
        int remaining = tokenBudget - recentTokens;

        List<AgentMessage> result = new ArrayList<>();
        // 从最新的旧消息开始，倒序加入直到 budget 耗尽
        List<AgentMessage> oldMessages = allMessages.stream()
                .filter(m -> !recentMessages.contains(m))
                .toList();

        for (int i = oldMessages.size() - 1; i >= 0 && remaining > 0; i--) {
            AgentMessage msg = oldMessages.get(i);
            int tokens = msg.getTokenCount() != null ? msg.getTokenCount() : 0;
            if (tokens <= remaining) {
                result.addFirst(msg);
                remaining -= tokens;
            }
        }
        result.addAll(recentMessages);
        return result;
    }
}
