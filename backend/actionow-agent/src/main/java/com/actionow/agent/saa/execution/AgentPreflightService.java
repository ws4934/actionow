package com.actionow.agent.saa.execution;

import com.actionow.agent.config.constant.AgentExecutionMode;
import com.actionow.agent.billing.dto.BillingSessionResponse;
import com.actionow.agent.billing.service.BillingIntegrationService;
import com.actionow.agent.billing.service.TokenCountingService;
import com.actionow.agent.core.execution.ExecutionRegistry;
import com.actionow.agent.core.scope.AgentContext;
import com.actionow.agent.core.scope.AgentContextBuilder;
import com.actionow.agent.core.scope.AgentContextHolder;
import com.actionow.agent.core.scope.AgentScope;
import com.actionow.agent.core.agent.AgentStreamEvent;
import com.actionow.agent.core.context.SessionContextHolder;
import com.actionow.agent.dto.request.InlineAttachment;
import com.actionow.agent.interaction.AgentStreamBridge;
import com.actionow.agent.dto.request.SendMessageRequest;
import com.actionow.agent.entity.AgentSessionEntity;
import com.actionow.agent.feign.AssetFeignClient;
import com.actionow.agent.resolution.dto.ResolvedAgentProfile;
import com.actionow.agent.resolution.service.AgentResolutionService;
import com.actionow.common.core.result.Result;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import com.actionow.agent.saa.augmentation.ContextAugmentationService;
import com.actionow.agent.saa.session.SaaSessionService;
import com.actionow.common.core.context.UserContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Agent 执行前置服务
 * 封装会话加载 → 作用域上下文 → 并发控制 → 消息保存 → 计费启动 → 占位消息 → 输入增强
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentPreflightService {

    private final SaaSessionService sessionService;
    private final BillingIntegrationService billingIntegrationService;
    private final TokenCountingService tokenCountingService;
    private final ExecutionRegistry executionRegistry;
    private final AssetFeignClient assetFeignClient;
    private final AgentResolutionService agentResolutionService;
    private final AgentContextBuilder agentContextBuilder;
    private final AgentStreamBridge streamBridge;

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @Autowired(required = false)
    private ContextAugmentationService contextAugmentationService;

    /**
     * skip-placeholder 开关：打开后 preflight 不再插入空 placeholder 行，
     * 生成状态改由 {@code t_agent_session.generating_since} 承载。
     * 前端 /state 端点从 session 读取心跳，不依赖空消息行。
     */
    @Value("${actionow.agent.message.skip-placeholder.enabled:false}")
    private boolean skipPlaceholderEnabled;

    /**
     * 执行前置结果
     */
    public record PreflightResult(
            AgentSessionEntity session,
            BillingSessionResponse billingSession,
            String placeholderMessageId,
            String augmentedInput,
            List<Media> media,
            long startMs,
            int userTokenCount,
            ResolvedAgentProfile resolvedAgent,
            AgentExecutionMode executionMode,
            String contextSystemPrompt
    ) {}

    /**
     * 流式执行前置（含 permit 获取）
     * 成功返回：permit 已获取，执行已注册；失败抛出：所有已获取资源已内部清理。
     */
    public PreflightResult prepareStream(String sessionId, SendMessageRequest request) {
        AgentSessionEntity session = sessionService.getSessionEntity(sessionId);
        ResolvedAgentProfile resolvedAgent = resolveAgentProfile(request, session);
        setupScopeContext(session, request, resolvedAgent);

        if (executionRegistry.hasActiveExecution(sessionId)) {
            log.warn("会话 {} 存在先前未完成的执行，强制取消并清理", sessionId);
            executionRegistry.requestCancellation(sessionId);
            executionRegistry.unregisterExecution(sessionId);
            executionRegistry.releasePermit();
        }

        boolean permitAcquired = false;
        try {
            permitAcquired = executionRegistry.tryAcquirePermit();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            clearScopeContext(sessionId);
            throw new IllegalStateException("获取执行许可时被中断", ie);
        }
        if (!permitAcquired) {
            clearScopeContext(sessionId);
            throw new IllegalStateException("系统并发执行数已达上限，请稍后再试");
        }

        try {
            executionRegistry.registerExecution(sessionId);
            emitStatus(sessionId, "preparing", "正在初始化会话", 0.1);

            AgentExecutionMode executionMode = resolveExecutionMode(request, resolvedAgent);
            Integer userTokenCount = tokenCountingService.countTokens(request.getMessage());
            sessionService.saveUserMessage(sessionId, request.getMessage(), userTokenCount, request.getAttachmentIds());
            emitStatus(sessionId, "saving_user_message", "已保存用户消息", 0.25);

            BillingSessionResponse billingSession = billingIntegrationService.startBilling(
                    session.getWorkspaceId(), sessionId, session.getUserId(), session.getAgentType());
            emitStatus(sessionId, "billing_start", "计费会话已开启", 0.35);

            String placeholderMessageId;
            if (skipPlaceholderEnabled) {
                // 新路径：不写空 placeholder，把"正在生成"标记挂到 session
                sessionService.markSessionGenerating(sessionId);
                placeholderMessageId = null;
            } else {
                placeholderMessageId = sessionService.savePlaceholderMessage(sessionId).getId();
            }
            emitStatus(sessionId, "building_context", "正在构建上下文", 0.55);
            String augmentedInput = augmentInput(request.getMessage(), session);
            String contextSystemPrompt = buildContextSystemPrompt(session);

            if (request.getAttachmentIds() != null && !request.getAttachmentIds().isEmpty()) {
                emitStatus(sessionId, "loading_media", "正在加载附件", 0.75);
            }
            List<Media> media = buildMedia(request, session.getWorkspaceId());
            emitStatus(sessionId, "ready", "准备就绪，开始生成", 0.9);

            return new PreflightResult(
                    session, billingSession, placeholderMessageId, augmentedInput,
                    media, System.currentTimeMillis(),
                    userTokenCount != null ? userTokenCount : 0,
                    resolvedAgent, executionMode, contextSystemPrompt
            );
        } catch (Exception e) {
            executionRegistry.unregisterExecution(sessionId);
            executionRegistry.releasePermit();
            clearScopeContext(sessionId);
            throw e;
        }
    }

    /**
     * 同步执行前置（含 permit 获取，无占位消息）
     * 成功返回：permit 已获取，执行已注册；失败抛出：所有已获取资源已内部清理。
     */
    public PreflightResult prepareSync(String sessionId, SendMessageRequest request) {
        AgentSessionEntity session = sessionService.getSessionEntity(sessionId);
        ResolvedAgentProfile resolvedAgent = resolveAgentProfile(request, session);
        setupScopeContext(session, request, resolvedAgent);

        if (executionRegistry.hasActiveExecution(sessionId)) {
            log.warn("会话 {} 存在先前未完成的执行，强制取消并清理", sessionId);
            executionRegistry.requestCancellation(sessionId);
            executionRegistry.unregisterExecution(sessionId);
            executionRegistry.releasePermit();
        }

        boolean permitAcquired = false;
        try {
            permitAcquired = executionRegistry.tryAcquirePermit();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            clearScopeContext(sessionId);
            throw new IllegalStateException("获取执行许可时被中断", ie);
        }
        if (!permitAcquired) {
            clearScopeContext(sessionId);
            throw new IllegalStateException("系统并发执行数已达上限，请稍后再试");
        }

        try {
            executionRegistry.registerExecution(sessionId);

            AgentExecutionMode executionMode = resolveExecutionMode(request, resolvedAgent);
            Integer userTokenCount = tokenCountingService.countTokens(request.getMessage());
            sessionService.saveUserMessage(sessionId, request.getMessage(), userTokenCount, request.getAttachmentIds());

            BillingSessionResponse billingSession = billingIntegrationService.startBilling(
                    session.getWorkspaceId(), sessionId, session.getUserId(), session.getAgentType());

            String augmentedInput = augmentInput(request.getMessage(), session);
            String contextSystemPrompt = buildContextSystemPrompt(session);
            List<Media> media = buildMedia(request, session.getWorkspaceId());

            return new PreflightResult(
                    session, billingSession, null, augmentedInput,
                    media, System.currentTimeMillis(),
                    userTokenCount != null ? userTokenCount : 0,
                    resolvedAgent, executionMode, contextSystemPrompt
            );
        } catch (Exception e) {
            executionRegistry.unregisterExecution(sessionId);
            executionRegistry.releasePermit();
            clearScopeContext(sessionId);
            throw e;
        }
    }

    private void emitStatus(String sessionId, String phase, String label, double progress) {
        if (sessionId == null) return;
        try {
            streamBridge.publish(sessionId, AgentStreamEvent.status(phase, label, progress, null));
        } catch (Exception e) {
            log.debug("emit preflight status failed phase={}: {}", phase, e.getMessage());
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 解析本次请求的 Agent 运行配置。
     */
    private ResolvedAgentProfile resolveAgentProfile(
            SendMessageRequest request, AgentSessionEntity session) {
        List<String> requestedSkillNames = request.getSkillNames() != null
                ? request.getSkillNames()
                : session.getSkillNames();
        return agentResolutionService.resolve(
                session.getAgentType(),
                session.getWorkspaceId(),
                session.getUserId(),
                requestedSkillNames
        );
    }

    private void setupScopeContext(AgentSessionEntity session, SendMessageRequest request,
                                   ResolvedAgentProfile resolvedAgent) {
        AgentScope scope;
        if (request.getScope() != null) {
            try {
                scope = AgentScope.valueOf(request.getScope().toUpperCase());
            } catch (IllegalArgumentException e) {
                scope = resolveScope(session);
            }
        } else {
            scope = resolveScope(session);
        }

        String contextScriptId = request.getScriptId() != null ? request.getScriptId() : session.getScriptId();

        agentContextBuilder.buildAndRegister(AgentContextBuilder.ContextParams.builder()
                .sessionId(session.getId())
                .agentType(session.getAgentType())
                .skillNames(resolvedAgent != null ? resolvedAgent.getResolvedSkillNames() : null)
                .executionMode(AgentExecutionMode.fromCode(request.getExecutionMode()).name())
                .userId(session.getUserId())
                .workspaceId(session.getWorkspaceId())
                .scope(scope)
                .scriptId(contextScriptId)
                .missionId(request.getMissionId())
                .missionStepId(request.getMissionStepId())
                .episodeId(request.getEpisodeId())
                .storyboardId(request.getStoryboardId())
                .characterId(request.getCharacterId())
                .sceneId(request.getSceneId())
                .propId(request.getPropId())
                .styleId(request.getStyleId())
                .assetId(request.getAssetId())
                .build());
    }

    private void clearScopeContext(String sessionId) {
        AgentContextHolder.clearContext();
        SessionContextHolder.clearCurrentSessionId();
        SessionContextHolder.clear(sessionId);
    }

    private AgentScope resolveScope(AgentSessionEntity session) {
        return session.getScriptId() != null ? AgentScope.SCRIPT : AgentScope.GLOBAL;
    }

    private AgentExecutionMode resolveExecutionMode(
            SendMessageRequest request, ResolvedAgentProfile resolvedAgent) {
        AgentExecutionMode requestedMode = AgentExecutionMode.fromCode(request.getExecutionMode());
        AgentExecutionMode profileMode = resolvedAgent != null
                ? AgentExecutionMode.fromCode(resolvedAgent.getExecutionMode())
                : AgentExecutionMode.BOTH;

        if (!supportsExecutionMode(profileMode, requestedMode)) {
            throw new IllegalStateException("Agent 不支持当前执行模式: requested="
                    + requestedMode + ", supported=" + profileMode);
        }
        return requestedMode;
    }

    private boolean supportsExecutionMode(AgentExecutionMode profileMode, AgentExecutionMode requestedMode) {
        return profileMode == AgentExecutionMode.BOTH || profileMode == requestedMode;
    }

    private String augmentInput(String userMessage, AgentSessionEntity session) {
        if (contextAugmentationService == null) {
            return userMessage;
        }
        try {
            return contextAugmentationService.augmentInput(userMessage, session);
        } catch (Exception e) {
            log.warn("Context augmentation failed, using raw input: {}", e.getMessage());
            return userMessage;
        }
    }

    private String buildContextSystemPrompt(AgentSessionEntity session) {
        if (contextAugmentationService == null) {
            return null;
        }
        try {
            return contextAugmentationService.buildContextSystemPrompt(session);
        } catch (Exception e) {
            log.warn("Context system prompt build failed: {}", e.getMessage());
            return null;
        }
    }

    private List<Media> buildMedia(SendMessageRequest request, String workspaceId) {
        List<Media> mediaList = new ArrayList<>();

        // 1. 处理内联附件（base64 / URL 模式）
        List<InlineAttachment> attachments = request.getAttachments();
        if (attachments != null) {
            for (InlineAttachment att : attachments) {
                try {
                    String mimeTypeStr = StringUtils.hasText(att.getMimeType())
                            ? att.getMimeType() : "application/octet-stream";
                    org.springframework.util.MimeType mimeType = MimeTypeUtils.parseMimeType(mimeTypeStr);

                    if (StringUtils.hasText(att.getData())) {
                        byte[] bytes = Base64.getDecoder().decode(att.getData());
                        mediaList.add(Media.builder().mimeType(mimeType).data(bytes).build());
                    } else if (StringUtils.hasText(att.getUrl())) {
                        mediaList.add(new Media(mimeType, URI.create(att.getUrl())));
                    } else {
                        log.warn("InlineAttachment skipped (no data or url): fileName={}", att.getFileName());
                    }
                } catch (Exception e) {
                    log.warn("Failed to process InlineAttachment (skipping): fileName={}, error={}",
                            att.getFileName(), e.getMessage());
                }
            }
        }

        // 2. 处理已上传附件 ID：先批量 RPC 获取 fileUrl + mimeType，然后并行异步下载所有字节；
        //    超时或失败的单个下载回退到 URL 形式，不阻塞其他附件。
        List<String> attachmentIds = request.getAttachmentIds();
        if (attachmentIds != null && !attachmentIds.isEmpty()) {
            if (!StringUtils.hasText(workspaceId)) {
                log.warn("No workspaceId available, skipping attachmentIds resolution");
            } else {
                mediaList.addAll(downloadAttachmentsInParallel(attachmentIds, workspaceId));
            }
        }

        return mediaList;
    }

    /**
     * 并行下载附件字节。每个附件独立异步 HTTP 请求，返回时长不受其他附件影响。
     * <p>总耗时从 N × timeout 降到 max(timeout)。
     */
    private List<Media> downloadAttachmentsInParallel(List<String> attachmentIds, String workspaceId) {
        List<CompletableFuture<Media>> futures = new ArrayList<>(attachmentIds.size());
        for (String assetId : attachmentIds) {
            futures.add(CompletableFuture.supplyAsync(() -> resolveAndDownloadAttachment(assetId, workspaceId)));
        }

        List<Media> resolved = new ArrayList<>(attachmentIds.size());
        try {
            CompletableFuture
                    .allOf(futures.toArray(CompletableFuture[]::new))
                    .get(45, TimeUnit.SECONDS);
        } catch (TimeoutException te) {
            log.warn("Attachment downloads 超时: pending={}/{}",
                    futures.stream().filter(f -> !f.isDone()).count(), futures.size());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Attachment downloads 被中断");
        } catch (ExecutionException ee) {
            log.warn("Attachment downloads 发生异常: {}", ee.getMessage());
        }

        for (int i = 0; i < futures.size(); i++) {
            CompletableFuture<Media> f = futures.get(i);
            try {
                Media m = f.getNow(null);
                if (m != null) resolved.add(m);
            } catch (Exception e) {
                log.warn("Attachment resolve failed id={}: {}", attachmentIds.get(i), e.getMessage());
            }
        }
        return resolved;
    }

    /**
     * 单个附件解析：Feign 取 fileUrl，然后通过 {@link HttpClient#sendAsync} 异步下载字节。
     * 失败时回退到 URL 形式。
     */
    @SuppressWarnings("unchecked")
    private Media resolveAndDownloadAttachment(String assetId, String workspaceId) {
        try {
            Result<Map<String, Object>> result = assetFeignClient.getAsset(workspaceId, assetId);
            if (result == null || !result.isSuccess() || result.getData() == null) {
                log.warn("Failed to get asset info: id={}", assetId);
                return null;
            }
            Map<String, Object> asset = result.getData();
            String fileUrl = (String) asset.get("fileUrl");
            if (!StringUtils.hasText(fileUrl)) {
                log.warn("Asset has no fileUrl, skipping: id={}", assetId);
                return null;
            }
            String mimeTypeStr = StringUtils.hasText((String) asset.get("mimeType"))
                    ? (String) asset.get("mimeType") : "image/jpeg";
            org.springframework.util.MimeType mimeType = MimeTypeUtils.parseMimeType(mimeTypeStr);

            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(fileUrl))
                        .timeout(Duration.ofSeconds(30))
                        .GET().build();
                HttpResponse<byte[]> resp = HTTP_CLIENT.sendAsync(req, HttpResponse.BodyHandlers.ofByteArray())
                        .orTimeout(30, TimeUnit.SECONDS)
                        .get();
                if (resp.statusCode() == 200 && resp.body().length > 0) {
                    log.debug("Resolved attachmentId={} as {} bytes ({})", assetId, resp.body().length, mimeTypeStr);
                    return Media.builder().mimeType(mimeType).data(resp.body()).build();
                }
                log.warn("Download failed for asset id={}, status={}, falling back to URL", assetId, resp.statusCode());
                return new Media(mimeType, URI.create(fileUrl));
            } catch (Exception downloadEx) {
                log.warn("Cannot download asset bytes, falling back to URL: id={}, error={}", assetId, downloadEx.getMessage());
                return new Media(mimeType, URI.create(fileUrl));
            }
        } catch (Exception e) {
            log.warn("Failed to resolve attachmentId={}, skipping: {}", assetId, e.getMessage());
            return null;
        }
    }
}
