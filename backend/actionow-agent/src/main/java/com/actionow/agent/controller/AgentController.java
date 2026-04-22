package com.actionow.agent.controller;

import com.actionow.agent.constant.AgentConstants;
import com.actionow.agent.constant.AgentType;
import com.actionow.agent.core.agent.AgentResponse;
import com.actionow.agent.core.execution.ExecutionRegistry;
import com.actionow.agent.dto.request.CreateSessionRequest;
import com.actionow.agent.dto.request.SendMessageRequest;
import com.actionow.agent.dto.request.SessionQueryRequest;
import com.actionow.agent.dto.response.AttachmentInfo;
import com.actionow.agent.dto.response.MessageResponse;
import com.actionow.agent.dto.response.SessionResponse;
import com.actionow.agent.feign.AssetFeignClient;
import com.actionow.agent.feign.dto.AssetDetailResponse;
import com.actionow.agent.dto.response.SessionStateResponse;
import com.actionow.agent.interaction.AgentAskHistoryService;
import com.actionow.agent.interaction.AgentStreamBridge;
import com.actionow.agent.interaction.PendingAskResponse;
import com.actionow.agent.interaction.UserInteractionService;
import com.actionow.agent.metrics.AgentMetrics;
import com.actionow.agent.entity.AgentMessage;
import com.actionow.agent.entity.AgentSessionEntity;
import com.actionow.agent.resolution.dto.ResolvedAgentProfile;
import com.actionow.agent.resolution.service.AgentResolutionService;
import com.actionow.agent.saa.factory.SaaAgentFactory;
import com.actionow.agent.saa.execution.SaaAgentRunner;
import com.actionow.agent.saa.session.AgentMessageCollapser;
import com.actionow.agent.saa.session.SaaSessionService;
import com.actionow.common.core.context.UserContext;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.result.PageResult;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireWorkspaceMember;
import com.actionow.common.web.sse.SseResponseHelper;
import com.actionow.common.web.sse.SseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Agent 控制器（SAA v2）
 * 提供 Agent 会话和消息管理 API
 * 基于 Spring AI Alibaba Agent Framework 实现
 *
 * @author Actionow
 */
@Slf4j
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AgentController {

    private final SaaAgentRunner agentRunner;
    private final SaaSessionService sessionService;
    private final ExecutionRegistry executionRegistry;
    private final ObjectMapper objectMapper;
    private final SseService sseService;
    private final SaaAgentFactory agentFactory;
    private final AgentResolutionService agentResolutionService;
    private final AssetFeignClient assetFeignClient;
    private final UserInteractionService userInteractionService;
    private final AgentAskHistoryService agentAskHistoryService;
    private final AgentStreamBridge agentStreamBridge;
    private final AgentMetrics agentMetrics;

    // Virtual thread executor for SSE streaming
    private final ExecutorService sseExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 只读段落切分开关 —— 打开时把旧 placeholder 的整段 blob 按空行切成多段返回，
     * 让前端不再分别处理"遗留 blob"和"新逐段落行"两种渲染路径。详见
     * {@link com.actionow.agent.saa.session.AssistantSegmentSplitter}。
     */
    @Value("${actionow.agent.message.segment-split.enabled:true}")
    private boolean segmentSplitEnabled;

    /**
     * 相邻同类消息合并开关。打开时 {@code GET /messages} 返回会把连续的 assistant_segment
     * 行（含 legacy_split 产物）合并为一条气泡；tool_call / tool_result / user 作为段落边界
     * 自然打断合并组。关闭时返回原始事件粒度，便于对拍和排查。
     */
    @Value("${actionow.agent.message.collapse-adjacent.enabled:true}")
    private boolean collapseAdjacentEnabled;

    /**
     * 清理资源
     * 关闭 SSE 执行器，防止资源泄漏
     */
    @PreDestroy
    public void destroy() {
        log.info("Shutting down SSE executor...");
        sseExecutor.shutdown();
        try {
            if (!sseExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warn("SSE executor did not terminate gracefully, forcing shutdown");
                sseExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            sseExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("SSE executor shutdown complete");
    }

    /**
     * 创建会话
     */
    @PostMapping("/sessions")
    @RequireWorkspaceMember
    public Result<SessionResponse> createSession(@Valid @RequestBody CreateSessionRequest request) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        String agentType = request.getAgentType() != null
                ? request.getAgentType().toUpperCase()
                : AgentType.COORDINATOR.getCode();
        ResolvedAgentProfile resolvedAgent = agentResolutionService.resolve(
                agentType,
                workspaceId,
                userId,
                request.getSkillNames()
        );
        if (!Boolean.TRUE.equals(resolvedAgent.getCoordinator())
                && !Boolean.TRUE.equals(resolvedAgent.getStandaloneEnabled())) {
            return Result.fail("0709003", "该 Agent 不支持直接创建独立会话: " + agentType);
        }

        AgentSessionEntity entity = sessionService.createSession(
                agentType,
                userId,
                workspaceId,
                request.getScriptId(),
                request.getSkillNames(),
                request.getScopeContext()
        );

        SessionResponse response = SessionResponse.builder()
                .id(entity.getId())
                .agentType(entity.getAgentType())
                .userId(userId)
                .workspaceId(workspaceId)
                .scriptId(request.getScriptId())
                .status(AgentConstants.SESSION_STATUS_ACTIVE)
                .messageCount(0)
                .build();

        return Result.success(response);
    }

    /**
     * 获取会话详情
     */
    @GetMapping("/sessions/{sessionId}")
    @RequireWorkspaceMember
    public Result<SessionResponse> getSession(@PathVariable String sessionId) {
        AgentSessionEntity entity = sessionService.getSessionEntity(sessionId);
        SessionResponse response = SessionResponse.builder()
                .id(entity.getId())
                .agentType(entity.getAgentType())
                .userId(entity.getUserId())
                .workspaceId(entity.getWorkspaceId())
                .scriptId(entity.getScriptId())
                .title(entity.getTitle())
                .status(entity.getStatus())
                .messageCount(entity.getMessageCount())
                .totalTokens(entity.getTotalTokens())
                .createdAt(entity.getCreatedAt())
                .lastActiveAt(entity.getLastActiveAt())
                .generating(executionRegistry.hasActiveExecution(sessionId))
                .build();
        return Result.success(response);
    }

    /**
     * 会话恢复状态端点 — 前端进入对话页 / SSE 重连后调用一次。
     *
     * <p>返回"此刻是否有 in-flight 生成 / pending ask"的权威快照，
     * 附带 {@link SessionStateResponse.ResumeHint} 供前端直接分发恢复动作。
     * 优先级：{@code ANSWER_ASK > RESUME_STREAM > IDLE}，即当同时存在
     * pendingAsk 与占位消息时，应先引导用户回答 HITL。
     *
     * <p>P1 阶段 {@code lastEventId} 固定返回 0；{@code lastHeartbeatAt} 为 null —
     * 待 {@code #4 eventId + 回放} / {@code #6 心跳} 落地后自动切换为真实值，端点契约不变。
     */
    @GetMapping("/sessions/{sessionId}/state")
    @RequireWorkspaceMember
    public Result<SessionStateResponse> getSessionState(@PathVariable String sessionId) {
        AgentSessionEntity entity = sessionService.getSessionEntity(sessionId);
        String callerId = UserContextHolder.getUserId();
        if (callerId == null || !callerId.equals(entity.getUserId())) {
            log.warn("state 越权访问被拒: sessionId={}, owner={}, caller={}",
                    sessionId, entity.getUserId(), callerId);
            return Result.fail("0709040", "无权限访问他人的会话");
        }

        SessionStateResponse.SessionInfo sessionInfo = SessionStateResponse.SessionInfo.builder()
                .id(entity.getId())
                .agentType(entity.getAgentType())
                .title(entity.getTitle())
                .status(entity.getStatus())
                .messageCount(entity.getMessageCount())
                .totalTokens(entity.getTotalTokens())
                .createdAt(entity.getCreatedAt())
                .lastActiveAt(entity.getLastActiveAt())
                .build();

        AgentMessage placeholder = sessionService.findInFlightPlaceholder(sessionId);
        boolean registryActive = executionRegistry.hasActiveExecution(sessionId);
        // skip-placeholder 路径：从 session 级 generating_since 推断 inFlight，
        // placeholder 缺失不再等价于"未生成"。
        boolean sessionGenerating = entity.getGeneratingSince() != null;
        boolean inFlight = placeholder != null || registryActive || sessionGenerating;

        String startedAtSource;
        LocalDateTime startedAt;
        LocalDateTime lastHeartbeatAt;
        String placeholderIdForResp;
        if (placeholder != null) {
            startedAtSource = "placeholder";
            startedAt = placeholder.getCreatedAt();
            lastHeartbeatAt = placeholder.getLastHeartbeatAt();
            placeholderIdForResp = placeholder.getId();
        } else {
            startedAtSource = "session";
            startedAt = entity.getGeneratingSince();
            lastHeartbeatAt = entity.getLastHeartbeatAt();
            placeholderIdForResp = null;
        }
        Long staleMs = null;
        if (startedAt != null) {
            staleMs = Duration.between(startedAt, LocalDateTime.now(ZoneOffset.UTC)).toMillis();
        }
        log.debug("/state sessionId={} placeholder={} sessionGenerating={} source={} inFlight={}",
                sessionId, placeholder != null, sessionGenerating, startedAtSource, inFlight);
        SessionStateResponse.GenerationInfo generation = SessionStateResponse.GenerationInfo.builder()
                .inFlight(inFlight)
                .placeholderMessageId(placeholderIdForResp)
                .startedAt(startedAt)
                .lastHeartbeatAt(lastHeartbeatAt)
                .staleMs(staleMs)
                .build();

        PendingAskResponse pendingAsk = PendingAskResponse.of(
                agentAskHistoryService.findLatestPending(sessionId));

        SessionStateResponse.ResumeHint hint;
        if (pendingAsk.isPending()) {
            hint = SessionStateResponse.ResumeHint.ANSWER_ASK;
        } else if (inFlight) {
            hint = SessionStateResponse.ResumeHint.RESUME_STREAM;
        } else {
            hint = SessionStateResponse.ResumeHint.IDLE;
        }

        return Result.success(SessionStateResponse.builder()
                .session(sessionInfo)
                .generation(generation)
                .pendingAsk(pendingAsk)
                .lastEventId(agentStreamBridge.currentMaxEventId(sessionId))
                .resumeHint(hint)
                .build());
    }

    /**
     * 获取当前用户的会话列表
     * 支持分页和根据 standalone 筛选
     *
     * @param page       页码（从1开始，默认1）
     * @param size       每页大小（默认20，最大100）
     * @param standalone 是否独立 Agent 会话（true: 独立 Agent 会话, false: 协调者会话, null: 全部）
     * @param scriptId   剧本 ID（可选，按剧本筛选会话）
     */
    @GetMapping("/sessions")
    @RequireWorkspaceMember
    public Result<PageResult<SessionResponse>> listSessions(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) Boolean standalone,
            @RequestParam(required = false) String scriptId) {
        String userId = UserContextHolder.getUserId();

        SessionQueryRequest request = new SessionQueryRequest();
        request.setPage(page);
        request.setSize(Math.min(size, 100));
        request.setStandalone(standalone);
        request.setScriptId(scriptId);

        PageResult<AgentSessionEntity> pageResult = sessionService.queryUserSessions(userId, request);

        List<SessionResponse> responses = pageResult.getRecords().stream()
                .map(entity -> SessionResponse.builder()
                        .id(entity.getId())
                        .agentType(entity.getAgentType())
                        .userId(entity.getUserId())
                        .workspaceId(entity.getWorkspaceId())
                        .scriptId(entity.getScriptId())
                        .title(entity.getTitle())
                        .status(entity.getStatus())
                        .messageCount(entity.getMessageCount())
                        .createdAt(entity.getCreatedAt())
                        .lastActiveAt(entity.getLastActiveAt())
                        .generating(executionRegistry.hasActiveExecution(entity.getId()))
                        .build())
                .toList();

        PageResult<SessionResponse> result = PageResult.of(
                pageResult.getCurrent(),
                pageResult.getSize(),
                pageResult.getTotal(),
                responses
        );

        return Result.success(result);
    }

    /**
     * 获取会话消息历史
     * 包括 user、assistant 消息以及 tool_call、tool_result 记录
     */
    @GetMapping("/sessions/{sessionId}/messages")
    @RequireWorkspaceMember
    public Result<List<MessageResponse>> getMessages(@PathVariable String sessionId) {
        List<AgentMessage> messages = sessionService.getMessages(sessionId);
        List<MessageResponse> responses = new ArrayList<>(messages.size());
        for (AgentMessage message : messages) {
            if (segmentSplitEnabled) {
                responses.addAll(MessageResponse.expandAssistant(message));
            } else {
                responses.add(MessageResponse.from(message));
            }
        }
        if (collapseAdjacentEnabled) {
            try {
                responses = new ArrayList<>(AgentMessageCollapser.collapse(
                        responses, size -> agentMetrics.recordCollapseGroupSize(size, "read")));
            } catch (Exception e) {
                log.warn("Collapse adjacent assistant segments failed, falling back to raw list: {}",
                        e.getMessage());
            }
        }

        // Batch-resolve attachment details
        Set<String> allAssetIds = new LinkedHashSet<>();
        for (MessageResponse resp : responses) {
            if (resp.getAttachmentIds() != null) {
                allAssetIds.addAll(resp.getAttachmentIds());
            }
        }
        if (!allAssetIds.isEmpty()) {
            Map<String, AttachmentInfo> assetMap = resolveAttachments(new ArrayList<>(allAssetIds));
            for (MessageResponse resp : responses) {
                if (resp.getAttachmentIds() != null && !resp.getAttachmentIds().isEmpty()) {
                    List<AttachmentInfo> attachments = resp.getAttachmentIds().stream()
                            .map(assetMap::get)
                            .filter(Objects::nonNull)
                            .toList();
                    resp.setAttachments(attachments);
                }
            }
        }

        return Result.success(responses);
    }

    private Map<String, AttachmentInfo> resolveAttachments(List<String> assetIds) {
        Map<String, AttachmentInfo> map = new HashMap<>();
        try {
            Result<List<AssetDetailResponse>> result = assetFeignClient.batchGetAssetDetails(assetIds);
            if (result != null && result.isSuccess() && result.getData() != null) {
                for (AssetDetailResponse asset : result.getData()) {
                    if (asset.getId() == null) continue;
                    map.put(asset.getId(), AttachmentInfo.builder()
                            .assetId(asset.getId())
                            .url(asset.getFileUrl())
                            .thumbnailUrl(asset.getThumbnailUrl())
                            .fileName(asset.getName())
                            .mimeType(asset.getMimeType())
                            .fileSize(asset.getFileSize())
                            .assetType(asset.getAssetType())
                            .build());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to resolve attachments for message display: {}", e.getMessage());
        }
        return map;
    }

    /**
     * 发送消息（同步响应）
     */
    @PostMapping("/sessions/{sessionId}/messages")
    @RequireWorkspaceMember
    public Result<AgentResponse> sendMessage(
            @PathVariable String sessionId,
            @Valid @RequestBody SendMessageRequest request) {
        if (request.isStream()) {
            return Result.fail("0709010", "请使用 /sessions/{sessionId}/messages/stream 端点获取流式响应");
        }

        String userId = UserContextHolder.getUserId();
        AgentResponse response = agentRunner.run(sessionId, userId, request);
        return Result.success(response);
    }

    /**
     * 发送消息（流式响应 - SSE）
     */
    @PostMapping(value = "/sessions/{sessionId}/messages/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequireWorkspaceMember
    public SseEmitter sendMessageStream(
            @PathVariable String sessionId,
            @Valid @RequestBody SendMessageRequest request,
            HttpServletResponse response) {
        log.info("Stream message request for session: {}", sessionId);

        SseResponseHelper.configureSseHeaders(response);

        String userId = UserContextHolder.getUserId();
        String workspaceId = UserContextHolder.getWorkspaceId();
        UserContext capturedContext = UserContextHolder.getContext();

        SseEmitter emitter = sseService.createConnection(
                sessionId,
                userId,
                workspaceId,
                sessionId,
                Map.of("type", "agent_stream", "message", request.getMessage())
        );

        sseExecutor.submit(() -> {
            try {
                log.info("SSE stream started for session: {}", sessionId);

                agentRunner.runStream(sessionId, userId, request, capturedContext)
                        .doOnNext(event -> {
                            MessageResponse msgResponse = MessageResponse.from(event);
                            boolean sent = sseService.sendEvent(sessionId, event.getType(), msgResponse);
                            if (sent) {
                                log.debug("SSE event sent: type={}, session={}", event.getType(), sessionId);
                            }
                        })
                        .doOnComplete(() -> {
                            sseService.complete(sessionId);
                            log.info("SSE stream completed for session: {}", sessionId);
                        })
                        .doOnError(e -> {
                            if (!isClientDisconnectError(e)) {
                                log.error("SSE stream error for session: {}", sessionId, e);
                            }
                            sseService.completeWithError(sessionId, e);
                        })
                        .blockLast();
            } catch (Exception e) {
                if (!isClientDisconnectError(e)) {
                    log.error("SSE executor error: ", e);
                }
                sseService.completeWithError(sessionId, e);
            }
        });

        return emitter;
    }

    /**
     * SSE 重连端点 — 客户端断线后恢复既有 in-flight 生成流。
     *
     * <p>请求头 {@code Last-Event-ID} 指定上次收到的最大事件 ID，服务端回放缓冲中
     * 大于该 ID 的事件，然后继续推送后续新事件直至生成终止。也可通过 URL 查询参数
     * {@code lastEventId} 覆盖（便于调试和非原生 EventSource 客户端）。
     *
     * <p>注意事项：
     * <ul>
     *   <li>不会触发新的 LLM 调用，仅订阅现有执行的事件流</li>
     *   <li>若原 stream 已终止（done/cancelled/error），可能立即完成 —— 前端应合并使用
     *       {@code /state} 端点判定当前是否还有 in-flight 生成</li>
     *   <li>同一 session 同一时刻只会有一个活跃 sink，后到的订阅会替换前者
     *       （对应 POST /messages/stream 的原 sink 会被挤出，其 HTTP emitter 正常超时关闭）</li>
     * </ul>
     */
    @GetMapping(value = "/sessions/{sessionId}/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequireWorkspaceMember
    public SseEmitter reconnectStream(
            @PathVariable String sessionId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventIdHeader,
            @RequestParam(value = "lastEventId", required = false) Long lastEventIdParam,
            HttpServletResponse response) {
        AgentSessionEntity session = sessionService.getSessionEntity(sessionId);
        String callerId = UserContextHolder.getUserId();
        if (callerId == null || !callerId.equals(session.getUserId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "无权限访问他人的会话");
        }

        long lastEventId = parseLastEventId(lastEventIdHeader, lastEventIdParam);
        log.info("SSE reconnect for session={}, lastEventId={}", sessionId, lastEventId);

        SseResponseHelper.configureSseHeaders(response);

        String userId = UserContextHolder.getUserId();
        String workspaceId = UserContextHolder.getWorkspaceId();
        SseEmitter emitter = sseService.createConnection(
                sessionId, userId, workspaceId, sessionId,
                Map.of("type", "agent_stream_reconnect", "lastEventId", lastEventId));

        sseExecutor.submit(() -> {
            try {
                agentRunner.subscribeReconnect(sessionId, lastEventId)
                        .doOnNext(event -> {
                            MessageResponse msgResponse = MessageResponse.from(event);
                            sseService.sendEvent(sessionId, event.getType(), msgResponse);
                        })
                        .doOnComplete(() -> sseService.complete(sessionId))
                        .doOnError(e -> {
                            if (!isClientDisconnectError(e)) {
                                log.error("SSE reconnect stream error for session: {}", sessionId, e);
                            }
                            sseService.completeWithError(sessionId, e);
                        })
                        .blockLast();
            } catch (Exception e) {
                if (!isClientDisconnectError(e)) {
                    log.error("SSE reconnect executor error: ", e);
                }
                sseService.completeWithError(sessionId, e);
            }
        });

        return emitter;
    }

    private long parseLastEventId(String header, Long queryParam) {
        if (queryParam != null && queryParam >= 0) return queryParam;
        if (header != null && !header.isBlank()) {
            try {
                long v = Long.parseLong(header.trim());
                if (v >= 0) return v;
            } catch (NumberFormatException ignored) {}
        }
        return 0L;
    }

    /**
     * 检查是否为客户端断开连接错误
     */
    private boolean isClientDisconnectError(Throwable e) {
        String message = e.getMessage();
        if (message == null) {
            message = "";
        }
        return message.contains("Broken pipe")
                || message.contains("Connection reset")
                || message.contains("Client aborted")
                || e instanceof java.io.IOException
                || (e.getCause() != null && isClientDisconnectError(e.getCause()));
    }

    /**
     * 结束会话
     */
    @PostMapping("/sessions/{sessionId}/end")
    @RequireWorkspaceMember
    public Result<Void> endSession(@PathVariable String sessionId) {
        sessionService.endSession(sessionId);
        return Result.success();
    }

    /**
     * 取消正在进行的生成
     */
    @PostMapping("/sessions/{sessionId}/cancel")
    @RequireWorkspaceMember
    public Result<Map<String, Object>> cancelGeneration(@PathVariable String sessionId) {
        log.info("Cancel generation request for session: {}", sessionId);

        if (!executionRegistry.hasActiveExecution(sessionId)) {
            return Result.fail("0709020", "当前没有正在进行的生成任务");
        }

        boolean cancelled = executionRegistry.requestCancellation(sessionId);
        if (!cancelled) {
            return Result.fail("0709021", "取消请求已发送或执行已完成");
        }

        // 级联取消：唤醒所有因 ask_user 阻塞的工具线程，避免它们持续等到超时
        int cancelledAsks = userInteractionService.cancelAllBySessionId(sessionId, "session cancelled by user");

        ExecutionRegistry.ExecutionContext context = executionRegistry.getExecutionContext(sessionId);
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("sessionId", sessionId);
        result.put("cancelled", true);
        result.put("cancelledAsks", cancelledAsks);
        if (context != null) {
            result.put("elapsedMs", context.getElapsedMs());
        }

        log.info("Cancellation signal sent for session: {}", sessionId);
        return Result.success(result);
    }

    /**
     * 归档会话
     */
    @PostMapping("/sessions/{sessionId}/archive")
    @RequireWorkspaceMember
    public Result<Void> archiveSession(@PathVariable String sessionId) {
        boolean success = sessionService.archiveSession(sessionId);
        if (!success) {
            return Result.fail("0709030", "会话无法归档，可能已经是归档状态");
        }
        return Result.success();
    }

    /**
     * 恢复归档会话
     */
    @PostMapping("/sessions/{sessionId}/resume")
    @RequireWorkspaceMember
    public Result<Void> resumeSession(@PathVariable String sessionId) {
        boolean success = sessionService.resumeSession(sessionId);
        if (!success) {
            return Result.fail("0709031", "会话无法恢复，可能不是归档状态");
        }
        return Result.success();
    }

    /**
     * 获取用户的活跃会话列表
     */
    @GetMapping("/sessions/active")
    @RequireWorkspaceMember
    public Result<List<SessionResponse>> listActiveSessions(
            @RequestParam(defaultValue = "20") int limit) {
        String userId = UserContextHolder.getUserId();
        List<AgentSessionEntity> entities = sessionService.listActiveUserSessions(userId, limit);
        List<SessionResponse> responses = entities.stream()
                .map(entity -> SessionResponse.builder()
                        .id(entity.getId())
                        .agentType(entity.getAgentType())
                        .userId(entity.getUserId())
                        .workspaceId(entity.getWorkspaceId())
                        .scriptId(entity.getScriptId())
                        .title(entity.getTitle())
                        .status(entity.getStatus())
                        .messageCount(entity.getMessageCount())
                        .createdAt(entity.getCreatedAt())
                        .lastActiveAt(entity.getLastActiveAt())
                        .build())
                .toList();
        return Result.success(responses);
    }

    /**
     * 获取用户的归档会话列表
     */
    @GetMapping("/sessions/archived")
    @RequireWorkspaceMember
    public Result<List<SessionResponse>> listArchivedSessions(
            @RequestParam(defaultValue = "50") int limit) {
        String userId = UserContextHolder.getUserId();
        List<AgentSessionEntity> entities = sessionService.listArchivedUserSessions(userId, limit);
        List<SessionResponse> responses = entities.stream()
                .map(entity -> SessionResponse.builder()
                        .id(entity.getId())
                        .agentType(entity.getAgentType())
                        .userId(entity.getUserId())
                        .workspaceId(entity.getWorkspaceId())
                        .scriptId(entity.getScriptId())
                        .title(entity.getTitle())
                        .status(entity.getStatus())
                        .messageCount(entity.getMessageCount())
                        .createdAt(entity.getCreatedAt())
                        .lastActiveAt(entity.getLastActiveAt())
                        .archivedAt(entity.getArchivedAt())
                        .build())
                .toList();
        return Result.success(responses);
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/sessions/{sessionId}")
    @RequireWorkspaceMember
    public Result<Void> deleteSession(@PathVariable String sessionId) {
        sessionService.deleteSessionSync(sessionId);
        return Result.success();
    }

    /**
     * 获取 Agent 系统状态
     */
    @GetMapping("/status")
    @RequireWorkspaceMember
    public Result<Map<String, Object>> getAgentSystemStatus() {
        Map<String, Object> status = agentFactory.getSystemStatus();
        return Result.success(status);
    }
}
