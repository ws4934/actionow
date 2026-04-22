package com.actionow.agent.runtime;

import com.alibaba.cloud.ai.graph.agent.flow.agent.SupervisorAgent;
import com.actionow.agent.billing.service.TokenCountingService;
import com.actionow.agent.core.agent.AgentResponse;
import com.actionow.agent.core.agent.AgentStreamEvent;
import com.actionow.agent.saa.factory.SaaAgentFactory;
import com.actionow.agent.saa.factory.SaaChatModelFactory;
import com.actionow.agent.saa.stream.SaaStreamProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基于 SAA 的 Runtime Gateway。
 * 仅负责模型执行与事件提取，不做持久化、计费、ExecutionRegistry 管理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SaaAgentRuntimeGateway implements AgentRuntimeGateway {

    @Value("${actionow.agent.stream.max-retries:2}")
    private int maxStreamRetries;

    @Value("${actionow.agent.stream.retry-delay-ms:1000}")
    private long retryDelayMs;

    private final SaaAgentFactory agentFactory;
    private final SaaChatModelFactory chatModelFactory;
    private final SaaStreamProcessor streamProcessor;
    private final TokenCountingService tokenCountingService;

    @Override
    public ExecutionTranscript execute(ExecutionRequest request) {
        // 同步执行：直接构建/复用 Agent，收集事件流
        SupervisorAgent supervisor = resolveAgent(request);

        StringBuilder contentBuffer = new StringBuilder();
        int lastContentIteration = -1;
        List<AgentStreamEvent> rawEvents = new ArrayList<>();
        AtomicInteger iterationCount = new AtomicInteger(0);
        List<AgentStreamEvent> collectedToolEvents = new ArrayList<>();
        Map<String, String> toolSkillMapping = request.getToolSkillMapping();

        Flux<Message> messageFlux;
        try {
            messageFlux = resolveMessageFlux(supervisor, request);
        } catch (Exception e) {
            throw new RuntimeException("Agent stream execution failed: " + e.getMessage(), e);
        }

        for (AgentStreamEvent event : messageFlux.flatMapIterable(message ->
                streamProcessor.convertMessageToEvents(message, iterationCount, collectedToolEvents, toolSkillMapping))
                .onErrorResume(e -> {
                    if (isEmptyFluxError(e)) {
                        handleEmptyFluxError(request.getSessionId(), iterationCount.get(), rawEvents.size());
                        return Flux.empty();
                    }
                    return Flux.error(e);
                }).toIterable()) {
            rawEvents.add(event);
            if (event.isMessage()) {
                int eventIter = event.getIteration() != null ? event.getIteration() : 0;
                if (eventIter > lastContentIteration) {
                    contentBuffer.setLength(0);
                    lastContentIteration = eventIter;
                }
                contentBuffer.append(event.getContent() != null ? event.getContent() : "");
            }
        }

        String finalText = streamProcessor.resolveFinalContent(
                contentBuffer.toString(),
                request.getSessionId(),
                rawEvents.stream().filter(AgentStreamEvent::isToolEvent).toList()
        );

        long inputTokens = request.getInputTokens() != null ? request.getInputTokens() : 0L;
        long outputTokens = countOutputTokens(finalText, request);
        int iterations = rawEvents.stream()
                .map(AgentStreamEvent::getIteration)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);

        return ExecutionTranscript.builder()
                .finalText(finalText)
                .toolCalls(streamProcessor.buildToolCallInfos(rawEvents.stream().filter(AgentStreamEvent::isToolEvent).toList()))
                .usage(ExecutionTranscript.TokenUsage.builder()
                        .inputTokens(inputTokens)
                        .outputTokens(outputTokens)
                        .totalTokens(inputTokens + outputTokens)
                        .build())
                .modelName(resolveModelName(request))
                .rawEvents(rawEvents)
                .iterations(iterations)
                .agent(supervisor)
                .build();
    }

    @Override
    public Flux<AgentStreamEvent> executeStream(ExecutionRequest request) {
        return Flux.defer(() -> {
            try {
                SupervisorAgent supervisor = resolveAgent(request);
                AtomicInteger iterationCount = new AtomicInteger(0);
                List<AgentStreamEvent> collectedToolEvents = new ArrayList<>();
                Map<String, String> toolSkillMapping = request.getToolSkillMapping();

                Flux<Message> messageFlux = resolveMessageFlux(supervisor, request);

                return messageFlux.flatMapIterable(message ->
                        streamProcessor.convertMessageToEvents(message, iterationCount, collectedToolEvents, toolSkillMapping))
                        .onErrorResume(e -> {
                            if (isEmptyFluxError(e)) {
                                handleEmptyFluxError(request.getSessionId(),
                                        iterationCount.get(), collectedToolEvents.size());
                                return Flux.empty();
                            }
                            return Flux.error(e);
                        });
            } catch (Exception e) {
                log.error("Runtime gateway executeStream failed: sessionId={}, error={}",
                        request.getSessionId(), e.getMessage(), e);
                return Flux.error(new RuntimeException("Runtime execution failed: " + e.getMessage(), e));
            }
        }).retryWhen(Retry.backoff(maxStreamRetries, Duration.ofMillis(retryDelayMs))
                .filter(this::isRetryableStreamError)
                .doBeforeRetry(signal -> log.warn("Retrying stream after transient error (attempt {}): {}",
                        signal.totalRetries() + 1, signal.failure().getMessage())));
    }

    /**
     * 判断是否为 SAA 框架 NodeExecutor 抛出的 "Empty flux" 错误。
     * 常见于 LLM API 返回 null/空或会话中途取消时。
     */
    private boolean isEmptyFluxError(Throwable error) {
        if (error instanceof IllegalStateException) {
            String msg = error.getMessage();
            return msg != null && msg.contains("Empty flux");
        }
        return false;
    }

    /**
     * 根据迭代上下文区分 Empty flux 的严重程度。
     * <ul>
     *   <li>iteration=0 且无事件 → LLM API 真·返回空（可能是 API 故障或认证失败），WARN 级别</li>
     *   <li>iteration>0 或已有事件 → 流中途终止（取消/超时），INFO 级别</li>
     * </ul>
     */
    private void handleEmptyFluxError(String sessionId, int iteration, int eventCount) {
        if (iteration == 0 && eventCount == 0) {
            log.warn("LLM API returned empty result with no prior output: sessionId={} — possible API failure or auth issue",
                    sessionId);
        } else {
            log.info("Empty flux after {} iteration(s) and {} event(s): sessionId={} — completing gracefully",
                    iteration, eventCount, sessionId);
        }
    }

    /**
     * 判断是否为可重试的瞬态流错误（网络 IO、HTTP/2 流重置等）。
     * 不重试业务逻辑错误或认证错误。
     */
    private boolean isRetryableStreamError(Throwable error) {
        if (error instanceof IOException) {
            return true;
        }
        // GenAiIOException、StreamResetException 等被包装在 RuntimeException 中
        String message = error.getMessage();
        if (message != null && (message.contains("stream was reset")
                || message.contains("Failed to read next JSON object from the stream")
                || message.contains("connection was reset"))) {
            return true;
        }
        Throwable cause = error.getCause();
        return cause instanceof IOException;
    }

    /**
     * 解析或复用 Agent 实例。
     * 当 request.cachedAgent 非空时直接复用，否则新建。
     */
    private SupervisorAgent resolveAgent(ExecutionRequest request) {
        if (request.getCachedAgent() != null) {
            log.debug("复用缓存 Agent: sessionId={}", request.getSessionId());
            return request.getCachedAgent();
        }
        java.util.List<String> allowedToolIds = request.getToolAccessPolicy() != null
                ? request.getToolAccessPolicy().filterToolIds(request.getMode(), request.getResolvedAgent())
                : null;
        return agentFactory.buildResolvedAgent(request.getResolvedAgent(), allowedToolIds);
    }

    /**
     * 选择合适的消息输入方式。
     * 优先使用 contextMessages（含历史），并将 media 注入最后一条 UserMessage；
     * 无历史时回退到单条 UserMessage 或纯文本。
     */
    private Flux<Message> resolveMessageFlux(SupervisorAgent supervisor,
                                              ExecutionRequest request)
            throws com.alibaba.cloud.ai.graph.exception.GraphRunnerException {
        if (request.getContextMessages() != null && !request.getContextMessages().isEmpty()) {
            List<Message> messages = request.getContextMessages();
            // 当存在附件 media 时，将其注入到最后一条 UserMessage 中
            if (hasMedia(request)) {
                injectMediaIntoLastUserMessage(messages, request.getMedia());
            }
            return supervisor.streamMessages(messages);
        }
        if (hasMedia(request)) {
            return supervisor.streamMessages(buildUserMessage(request));
        }
        return supervisor.streamMessages(request.getInput());
    }

    /**
     * 将 media 列表注入到消息列表中最后一条 UserMessage。
     * 通过替换该 UserMessage 为包含 media 的新实例实现。
     */
    private void injectMediaIntoLastUserMessage(List<Message> messages, List<Media> media) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage lastUserMsg) {
                messages.set(i, UserMessage.builder()
                        .text(lastUserMsg.getText())
                        .media(media)
                        .build());
                return;
            }
        }
        // 如果没有找到 UserMessage（理论上不会发生），追加一条带 media 的消息
        messages.add(UserMessage.builder()
                .text("")
                .media(media)
                .build());
    }

    private boolean hasMedia(ExecutionRequest request) {
        return request.getMedia() != null && !request.getMedia().isEmpty();
    }

    private UserMessage buildUserMessage(ExecutionRequest request) {
        return UserMessage.builder()
                .text(request.getInput())
                .media(request.getMedia() != null ? request.getMedia() : List.<Media>of())
                .build();
    }

    /**
     * 返回 llmProviderId（provider 的引用键，不是真实 model name）。
     * 真实 model name 请走 {@link #resolveActualModelName(ExecutionRequest)}。
     */
    private String resolveModelName(ExecutionRequest request) {
        if (request.getResolvedAgent() == null) {
            return null;
        }
        return request.getResolvedAgent().getLlmProviderId();
    }

    /**
     * 把 providerId 解析为真实 modelId（如 "gpt-4o-mini"、"gemini-2.5-flash"），
     * 使 {@link TokenCountingService} 能命中对应编码与 Gemini CJK 修正。
     * 命中 SaaChatModelFactory 的 5 分钟凭证缓存，热路径几乎零成本。
     */
    private String resolveActualModelName(ExecutionRequest request) {
        return chatModelFactory.resolveModelName(resolveModelName(request));
    }

    /**
     * 用 JTokkit 对最终文本做真实分词计数；带中日韩字符的 Gemini 请求会走 TokenCountingService 内部的修正因子。
     */
    private long countOutputTokens(String finalText, ExecutionRequest request) {
        if (finalText == null || finalText.isBlank()) {
            return 0L;
        }
        int count = tokenCountingService.countTokensForModel(finalText, resolveActualModelName(request));
        return Math.max(1, count);
    }
}
