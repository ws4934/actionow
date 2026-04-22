package com.actionow.agent.saa.execution;

import com.actionow.agent.billing.service.TokenCountingService;
import com.actionow.agent.constant.AgentConstants;
import com.actionow.agent.interaction.AgentStreamBridge;
import com.actionow.agent.saa.session.SaaSessionService;
import com.actionow.agent.config.constant.AgentExecutionMode;
import com.actionow.agent.metrics.AgentMetrics;
import com.actionow.agent.core.agent.AgentResponse;
import com.actionow.agent.core.agent.AgentStreamEvent;
import com.actionow.agent.core.execution.ExecutionRegistry;
import com.actionow.agent.dto.request.SendMessageRequest;
import com.actionow.agent.runtime.AgentRuntimeGateway;
import com.actionow.agent.runtime.ExecutionRequest;
import com.actionow.agent.runtime.ExecutionTranscript;
import com.actionow.agent.runtime.ToolAccessPolicy;
import com.actionow.agent.saa.factory.SaaChatModelFactory;
import com.actionow.agent.saa.stream.SaaStreamProcessor;
import com.actionow.common.core.context.UserContext;
import com.actionow.common.core.context.UserContextHolder;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

import com.actionow.agent.resolution.dto.ResolvedAgentProfile;
import com.actionow.agent.resolution.dto.ResolvedSkillInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * SAA Agent 执行器（SAA v2）
 * 替代 v1 的 ActionowAgentRunner（基于 Google ADK InMemoryRunner + Flowable）
 *
 * 核心变化：
 * - ADK InMemoryRunner + Flowable&lt;Event&gt; → SAA SupervisorAgent + Flux&lt;Message&gt;
 * - RxJava3 to Reactor adapter → 纯 Project Reactor
 * - ADK Session → Spring AI ChatMemory (JdbcChatMemoryRepository)
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SaaAgentRunner {

    private final AgentPreflightService agentPreflightService;
    private final AgentTeardownService agentTeardownService;
    private final AgentRuntimeGateway runtimeGateway;
    private final SaaStreamProcessor streamProcessor;
    private final ExecutionRegistry executionRegistry;
    private final ToolAccessPolicy toolAccessPolicy;
    private final com.actionow.agent.context.ContextWindowManager contextWindowManager;
    private final AgentMetrics agentMetrics;
    private final TokenCountingService tokenCountingService;
    private final SaaChatModelFactory chatModelFactory;
    private final AgentStreamBridge streamBridge;
    private final SaaSessionService sessionService;

    /**
     * 写入侧"每 message event 一行"开关。
     *
     * <p>打开时：每个 {@code EVENT_MESSAGE} 在 SSE 下发的同一时刻经
     * {@link SaaSessionService#appendAssistantSegment} 独立入库为一条 assistant 段落，
     * 占位消息仅作 generating→completed 的状态标记（content 不覆盖）。
     *
     * <p>关闭时：维持旧路径 —— 所有文本进 buffer，结束时一次写回 placeholder.content。
     */
    @Value("${actionow.agent.message.per-segment-write.enabled:false}")
    private boolean perSegmentWriteEnabled;

    @PostConstruct
    public void init() {
        log.info("SaaAgentRunner initialized (port 8091, framework: spring-ai-alibaba-agent-framework)");
    }

    // ==================== 流式执行 ====================

    /**
     * 重连订阅 — 客户端断线后不发送新消息、仅恢复既有 in-flight 生成的 SSE 流。
     *
     * <p>与 {@link #runStream} 的区别：不会触发新的 LLM 调用 / 工具执行，仅：
     * <ol>
     *   <li>把新 sink 挂到 {@link AgentStreamBridge}（替换可能存在的旧 sink）</li>
     *   <li>立即回放缓冲区中 {@code lastEventId} 之后的事件</li>
     *   <li>继续订阅后续由原 runStream 线程 publish 的事件</li>
     * </ol>
     *
     * <p>当原 runStream 发出终止事件（done/cancelled/error）时，本 Flux 借由
     * {@code takeUntilOther} 自动完成；客户端若在重连后等不到任何事件（生成已提前结束），
     * 应通过 {@code /state} 端点确认终态。
     */
    public Flux<AgentStreamEvent> subscribeReconnect(String sessionId, long lastEventId) {
        return Flux.<AgentStreamEvent>create(sink -> {
            streamBridge.register(sessionId, sink);
            sink.onDispose(() -> streamBridge.unregister(sessionId, sink));
            int replayed = streamBridge.replayAfter(sessionId, lastEventId);
            log.info("Reconnect subscribed: sessionId={}, lastEventId={}, replayed={}",
                    sessionId, lastEventId, replayed);
        })
        .takeUntil(AgentStreamEvent::isTerminal)
        .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 流式发送消息（SSE）
     * 返回 Flux&lt;AgentStreamEvent&gt; 供 AgentController 消费
     */
    public Flux<AgentStreamEvent> runStream(String sessionId, String userId, SendMessageRequest request,
                                             @Nullable UserContext userContext) {
        if (AgentExecutionMode.fromCode(request.getExecutionMode()) == AgentExecutionMode.MISSION) {
            return Flux.error(new IllegalStateException("SaaAgentRunner 仅用于 CHAT 执行，MISSION 请使用 AgentRuntimeGateway"));
        }
        return Flux.<AgentStreamEvent>create(sink -> {
            if (userContext != null) {
                UserContextHolder.setContext(userContext);
            }
            // 注册 sink 到 bridge，让工具（尤其是 ask_user_* HITL 工具）可以主动推事件到当前 SSE 流
            streamBridge.register(sessionId, sink);
            sink.onDispose(() -> streamBridge.unregister(sessionId, sink));

            AgentPreflightService.PreflightResult pre = null;
            try {
                pre = agentPreflightService.prepareStream(sessionId, request);
                subscribeToStream(sink, sessionId, pre, userContext);
            } catch (Exception e) {
                log.error("SaaAgentRunner.runStream error for session {}: {}", sessionId, e.getMessage(), e);
                if (pre != null) {
                    try {
                        agentTeardownService.onError(sessionId, pre, true);
                    } catch (Exception te) {
                        log.warn("Teardown failed after preflight error: {}", te.getMessage());
                    }
                }
                sink.error(e);
            } finally {
                UserContextHolder.clear();
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 订阅 Agent 流并注册回调。
     * 从 runStream 提取，负责构建 ExecutionRequest、绑定 onNext/onError/onComplete 以及取消处理。
     */
    private void subscribeToStream(FluxSink<AgentStreamEvent> sink, String sessionId,
                                    AgentPreflightService.PreflightResult pre, @Nullable UserContext userContext) {
        AtomicInteger iterationCount = new AtomicInteger(0);
        AtomicInteger lastContentIteration = new AtomicInteger(-1);
        StringBuilder contentBuffer = new StringBuilder();
        List<AgentStreamEvent> collectedToolEvents = new ArrayList<>();
        AtomicBoolean terminalHandled = new AtomicBoolean(false);
        AtomicReference<Disposable> subscriptionRef = new AtomicReference<>();

        // 段落合并缓冲：把相邻的 EVENT_MESSAGE chunk 合成一行 assistant 段落再入库，
        // 避免 token 级切片在 DB 里留下十几行只差几个字的记录。
        // 触发 flush 的边界：任意非-message 事件、iteration 切换、流终态（success/error/cancel）。
        StringBuilder segmentBuffer = new StringBuilder();
        AtomicReference<Long> segmentFirstEventId = new AtomicReference<>();
        AtomicInteger segmentIteration = new AtomicInteger(-1);
        Runnable flushSegment = () -> {
            if (!perSegmentWriteEnabled) return;
            if (pre.executionMode() == AgentExecutionMode.MISSION) return;
            if (segmentBuffer.length() == 0) return;
            String text = segmentBuffer.toString();
            segmentBuffer.setLength(0);
            Long firstEventId = segmentFirstEventId.getAndSet(null);
            int iter = segmentIteration.getAndSet(-1);
            try {
                sessionService.appendAssistantSegment(
                        sessionId, text, firstEventId, iter < 0 ? null : iter);
            } catch (Exception e) {
                log.warn("flush assistant segment failed sessionId={} firstEventId={}: {}",
                        sessionId, firstEventId, e.getMessage());
            }
        };

        Runnable cancelExecution = () -> {
            if (!terminalHandled.compareAndSet(false, true)) return;
            if (userContext != null) UserContextHolder.setContext(userContext);
            try {
                flushSegment.run();
                String cancelledContent = streamProcessor.resolveFinalContent(
                        contentBuffer.toString(), sessionId, collectedToolEvents);
                streamBridge.publish(sessionId, AgentStreamEvent.cancelled(cancelledContent, iterationCount.get()));
                agentMetrics.recordExecutionCancelled();
                agentTeardownService.onCancelled(sessionId, pre, cancelledContent, collectedToolEvents, true);
                Disposable active = subscriptionRef.get();
                if (active != null) active.dispose();
                sink.complete();
            } finally {
                UserContextHolder.clear();
            }
        };

        List<Message> contextMessages =
                buildContextMessagesQuietly(sessionId, pre.augmentedInput(),
                        pre.resolvedAgent(), pre.contextSystemPrompt());

        Disposable subscription = runtimeGateway.executeStream(ExecutionRequest.builder()
                        .mode(pre.executionMode())
                        .sessionId(sessionId)
                        .resolvedAgent(pre.resolvedAgent())
                        .input(pre.augmentedInput())
                        .contextMessages(contextMessages)
                        .media(pre.media())
                        .inputTokens((long) pre.userTokenCount())
                        .toolAccessPolicy(toolAccessPolicy)
                        .toolSkillMapping(buildToolSkillMapping(pre.resolvedAgent()))
                        .build())
                .subscribe(event -> {
                    if (executionRegistry.isCancelled(sessionId)) {
                        cancelExecution.run();
                        return;
                    }
                    boolean isMessage = AgentConstants.EVENT_MESSAGE.equals(event.getType());
                    // 非-message 事件（tool_call / tool_result / status / structured_data / ask_user / ...）
                    // 必须先 flush 已累积的 assistant 段落再写本事件行，
                    // 否则 DB sequence 里会出现"tool 行在前、它前面的 LLM 叙述在后"的倒置。
                    if (!isMessage) {
                        flushSegment.run();
                    }
                    if (event.isToolEvent()) {
                        collectedToolEvents.add(event);
                        // 实时落库：tool_call / tool_result 在 SSE 下发的同一刻写入 DB，
                        // 保证客户端中途断开重连时 DB 回放与流回放一致。
                        agentTeardownService.onToolEvent(sessionId, event, pre.executionMode());
                    }
                    if (isMessage) {
                        int eventIter = event.getIteration() != null ? event.getIteration() : 0;
                        int prevIter = lastContentIteration.get();
                        if (eventIter > prevIter) {
                            contentBuffer.setLength(0);
                            lastContentIteration.set(eventIter);
                        }
                        contentBuffer.append(event.getContent() != null ? event.getContent() : "");
                    }
                    iterationCount.updateAndGet(current ->
                            Math.max(current, event.getIteration() != null ? event.getIteration() : current));
                    // 统一经 bridge 发布：为事件分配单调 eventId 并写入 per-session 环形缓冲,
                    // 使得客户端断线重连时可通过 Last-Event-ID 回放缺失段；bridge 内部会把事件
                    // 转发给当前已注册的 sink，与直接 sink.next(event) 语义等价。
                    streamBridge.publish(sessionId, event);
                    // Step 2 写入侧：EVENT_MESSAGE chunk 进合并缓冲，由 flushSegment 在边界处
                    // 成段落入库（首个 chunk 的 eventId 作为段落 key，兼容 appendAssistantSegment
                    // 内部 (sessionId, eventId) 去重）。非-message 事件已在上面触发 flush。
                    if (perSegmentWriteEnabled
                            && isMessage
                            && event.getContent() != null
                            && !event.getContent().isEmpty()
                            && pre.executionMode() != AgentExecutionMode.MISSION) {
                        int eventIter = event.getIteration() != null ? event.getIteration() : 0;
                        // iteration 切换时先把上一轮已积累的段落 flush，避免跨 ReAct 轮合并。
                        if (segmentBuffer.length() > 0 && segmentIteration.get() != eventIter) {
                            flushSegment.run();
                        }
                        if (segmentBuffer.length() == 0) {
                            segmentFirstEventId.set(event.getEventId());
                            segmentIteration.set(eventIter);
                        }
                        segmentBuffer.append(event.getContent());
                    }
                },
                error -> {
                    flushSegment.run();
                    handleStreamError(sink, sessionId, pre, error, terminalHandled, userContext);
                },
                () -> {
                    flushSegment.run();
                    handleStreamComplete(sink, sessionId, pre, contentBuffer,
                            collectedToolEvents, iterationCount, terminalHandled, userContext);
                }
                );

        subscriptionRef.set(subscription);
        executionRegistry.setCancellationHandler(sessionId, cancelExecution);
        if (executionRegistry.isCancelled(sessionId)) {
            cancelExecution.run();
        }
    }

    /**
     * 流完成回调 — 计算统计、调用 teardown、发送 DONE 事件。
     */
    private void handleStreamComplete(FluxSink<AgentStreamEvent> sink, String sessionId,
                                       AgentPreflightService.PreflightResult pre,
                                       StringBuilder contentBuffer, List<AgentStreamEvent> collectedToolEvents,
                                       AtomicInteger iterationCount, AtomicBoolean terminalHandled,
                                       @Nullable UserContext userContext) {
        if (!terminalHandled.compareAndSet(false, true)) return;
        if (userContext != null) UserContextHolder.setContext(userContext);
        try {
            String finalContent = streamProcessor.resolveFinalContent(
                    contentBuffer.toString(), sessionId, collectedToolEvents);
            long elapsedMs = System.currentTimeMillis() - pre.startMs();
            int totalToolCalls = (int) collectedToolEvents.stream()
                    .filter(e -> AgentConstants.EVENT_TOOL_CALL.equals(e.getType())).count();

            long inputTokens = pre.userTokenCount();
            long outputTokens = countResponseTokens(finalContent, pre);
            agentTeardownService.onSuccess(sessionId, pre, finalContent,
                    collectedToolEvents, inputTokens, outputTokens);
            agentMetrics.recordExecutionSuccess(elapsedMs);

            long estimatedTotal = inputTokens + outputTokens;
            streamBridge.publish(sessionId, AgentStreamEvent.doneWithStats(
                    iterationCount.get(), elapsedMs, totalToolCalls, estimatedTotal));
            sink.complete();
        } finally {
            UserContextHolder.clear();
        }
    }

    /**
     * 流错误回调 — 记录日志、调用 teardown、传播错误。
     */
    private void handleStreamError(FluxSink<AgentStreamEvent> sink, String sessionId,
                                    AgentPreflightService.PreflightResult pre, Throwable error,
                                    AtomicBoolean terminalHandled, @Nullable UserContext userContext) {
        if (!terminalHandled.compareAndSet(false, true)) return;
        if (userContext != null) UserContextHolder.setContext(userContext);
        try {
            log.error("SAA agent stream error for session {}: {}", sessionId, error.getMessage(), error);
            agentTeardownService.onError(sessionId, pre, true);
            agentMetrics.recordExecutionError(System.currentTimeMillis() - pre.startMs());
            sink.error(error);
        } finally {
            UserContextHolder.clear();
        }
    }

    // ==================== 同步执行 ====================

    /**
     * 同步发送消息（非流式）
     */
    public AgentResponse run(String sessionId, String userId, SendMessageRequest request) {
        if (AgentExecutionMode.fromCode(request.getExecutionMode()) == AgentExecutionMode.MISSION) {
            throw new IllegalStateException("SaaAgentRunner 仅用于 CHAT 执行，MISSION 请使用 AgentRuntimeGateway");
        }
        AgentPreflightService.PreflightResult pre = null;
        try {
            pre = agentPreflightService.prepareSync(sessionId, request);

            List<Message> contextMessages =
                    buildContextMessagesQuietly(sessionId, pre.augmentedInput(),
                            pre.resolvedAgent(), pre.contextSystemPrompt());

            ExecutionTranscript transcript = runtimeGateway.execute(ExecutionRequest.builder()
                    .mode(pre.executionMode())
                    .sessionId(sessionId)
                    .resolvedAgent(pre.resolvedAgent())
                    .input(pre.augmentedInput())
                    .contextMessages(contextMessages)
                    .media(pre.media())
                    .inputTokens((long) pre.userTokenCount())
                    .toolAccessPolicy(toolAccessPolicy)
                    .toolSkillMapping(buildToolSkillMapping(pre.resolvedAgent()))
                    .build());

            agentTeardownService.onSuccess(sessionId, pre, transcript.getFinalText(), transcript.getRawEvents(),
                    transcript.getUsage().getInputTokens(), transcript.getUsage().getOutputTokens());

            return AgentResponse.builder()
                    .success(true)
                    .content(transcript.getFinalText())
                    .toolCalls(transcript.getToolCalls())
                    .iterations(transcript.getIterations())
                    .tokenUsage(AgentResponse.TokenUsage.builder()
                            .inputTokens((int) transcript.getUsage().getInputTokens())
                            .outputTokens((int) transcript.getUsage().getOutputTokens())
                            .totalTokens((int) transcript.getUsage().getTotalTokens())
                            .build())
                    .build();

        } catch (Exception e) {
            log.error("SaaAgentRunner.run error for session {}: {}", sessionId, e.getMessage(), e);
            if (pre != null) {
                agentTeardownService.onError(sessionId, pre, true);
            }
            throw new RuntimeException("Agent execution failed: " + e.getMessage(), e);
        } finally {
            // 兜底清理：防止 onSuccess/onError 本身抛出时 ThreadLocal 残留
            agentTeardownService.clearScopeContext(sessionId);
        }
    }

    // ==================== 内部工具方法 ====================

    /**
     * 构建上下文消息列表（含历史 + 上下文系统提示词），失败时静默降级。
     * 当完整构建失败但存在 contextSystemPrompt 时，回退为无历史但保留上下文系统提示词，
     * 确保 LLM 至少能获得作用域/剧本/章节等信息。
     */
    @Nullable
    private List<Message> buildContextMessagesQuietly(String sessionId, String input,
                                                      @Nullable ResolvedAgentProfile resolvedAgent,
                                                      @Nullable String contextSystemPrompt) {
        try {
            String llmProviderId = resolvedAgent != null ? resolvedAgent.getLlmProviderId() : null;
            if (llmProviderId != null) {
                return contextWindowManager.buildContextMessages(
                        sessionId, input, llmProviderId, contextSystemPrompt);
            }
        } catch (Exception e) {
            log.warn("构建上下文消息失败，回退到无历史模式: {}", e.getMessage());
        }
        // 回退：无法构建完整历史，但仍注入上下文系统提示词（如有）
        if (contextSystemPrompt != null) {
            List<Message> fallback = new ArrayList<>();
            fallback.add(new SystemMessage(contextSystemPrompt));
            fallback.add(new UserMessage(input));
            return fallback;
        }
        return null;
    }

    /**
     * 用 JTokkit 真实计算输出 Token 数。
     * <p>先从 provide profile 取到 llmProviderId，再通过 SaaChatModelFactory 解析成真实 modelId（命中 5min 缓存），
     * 保证 Gemini CJK 修正、GPT/Claude 编码选择等按模型维度正确命中。
     */
    private long countResponseTokens(String content, AgentPreflightService.PreflightResult pre) {
        if (content == null || content.isBlank()) {
            return 0L;
        }
        String llmProviderId = pre != null && pre.resolvedAgent() != null
                ? pre.resolvedAgent().getLlmProviderId()
                : null;
        String modelName = chatModelFactory.resolveModelName(llmProviderId);
        int count = tokenCountingService.countTokensForModel(content, modelName);
        return Math.max(1, count);
    }

    private Map<String, String> buildToolSkillMapping(ResolvedAgentProfile profile) {
        if (profile == null || profile.getResolvedSkills() == null) {
            return Map.of();
        }
        Map<String, String> mapping = new HashMap<>();
        for (ResolvedSkillInfo skill : profile.getResolvedSkills()) {
            if (skill.getToolIds() != null) {
                for (String toolId : skill.getToolIds()) {
                    mapping.put(toolId, skill.getName());
                }
            }
        }
        return mapping;
    }
}
