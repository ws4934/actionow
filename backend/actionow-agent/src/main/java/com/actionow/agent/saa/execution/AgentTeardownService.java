package com.actionow.agent.saa.execution;

import com.actionow.agent.config.constant.AgentExecutionMode;
import com.actionow.agent.billing.service.BillingIntegrationService;
import com.actionow.agent.constant.AgentConstants;
import com.actionow.agent.context.memory.WorkingMemoryStore;
import com.actionow.agent.core.agent.AgentStreamEvent;
import com.actionow.agent.core.context.SessionContextHolder;
import com.actionow.agent.core.execution.ExecutionRegistry;
import com.actionow.agent.core.scope.AgentContextHolder;
import com.actionow.agent.entity.AgentSessionEntity;
import com.actionow.agent.publisher.AgentCollabEventPublisher;
import com.actionow.agent.saa.session.SaaSessionService;
import com.actionow.agent.saa.stream.SaaStreamProcessor;
import com.actionow.agent.saa.interceptor.StatusEmittingInterceptor;
import com.actionow.agent.scriptwriting.tools.StructuredOutputTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 执行后置服务
 * 封装成功/失败路径的资源清理：计费结算/取消 → permit 释放 → 工具事件持久化 → 协作事件发布
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentTeardownService {

    private final SaaSessionService sessionService;
    private final BillingIntegrationService billingIntegrationService;
    private final ExecutionRegistry executionRegistry;
    private final AgentCollabEventPublisher collabEventPublisher;
    private final SaaStreamProcessor streamProcessor;
    private final WorkingMemoryStore workingMemoryStore;

    /** 已清理过的 sessionId 集合，防止多路径重复清理 */
    private final Set<String> cleanedSessions = ConcurrentHashMap.newKeySet();

    /**
     * 与 {@code SaaAgentRunner.perSegmentWriteEnabled} 共用同一开关：
     * 打开时 onSuccess / onCancelled 不再把 finalContent 写回 placeholder，
     * 只推进 placeholder 状态至 completed / cancelled，真正的文本已由 runner
     * 在流里逐段落入库。
     */
    @Value("${actionow.agent.message.per-segment-write.enabled:false}")
    private boolean perSegmentWriteEnabled;

    /**
     * 成功路径清理
     * 持久化消息 → 记录 Token → 结算计费 → 发布协作事件 → 释放 permit → 清理上下文
     *
     * @param inputTokens  估算的输入 Token 数（来自 preflight 的 userTokenCount）
     * @param outputTokens 估算的输出 Token 数（来自 TokenCountingService 对 finalContent 的估算）
     */
    public void onSuccess(String sessionId, AgentPreflightService.PreflightResult pre,
                          String finalContent, List<AgentStreamEvent> toolEvents,
                          long inputTokens, long outputTokens) {
        AgentSessionEntity session = pre.session();

        if (pre.executionMode() == AgentExecutionMode.MISSION) {
            executionRegistry.unregisterExecution(sessionId);
            executionRegistry.releasePermit();
            clearScopeContext(sessionId);
            return;
        }

        if (pre.placeholderMessageId() != null) {
            if (perSegmentWriteEnabled) {
                // 文本已由 SaaAgentRunner 在 message event 到达时逐段落入库，
                // 占位消息仅推进状态到 completed，避免重复写入造成内容翻倍。
                sessionService.finalizePlaceholderStatusOnly(
                        pre.placeholderMessageId(), "completed", null);
            } else {
                sessionService.finalizePlaceholderMessage(
                        pre.placeholderMessageId(), finalContent, "completed", null);
            }
        } else {
            // 无 placeholder 的极少数路径（非流式回退等）：segment 模式下段落行已
            // 沿途插入，不再补全单条合并消息；旧模式下仍一次性落库。
            if (!perSegmentWriteEnabled) {
                sessionService.saveAssistantMessage(sessionId, finalContent, null);
            }
        }
        // skip-placeholder 路径：session 级 generating_since / last_heartbeat_at 收尾。
        // 旧路径下 generating_since 本就为 NULL，UPDATE 幂等无副作用。
        sessionService.clearSessionGenerating(sessionId);

        // 工具事件已在 SaaAgentRunner 订阅回调中经 onToolEvent() 实时落库，
        // 此处不再批量重写，避免重复插入。
        doRecordTokenUsage(pre, session, inputTokens, outputTokens);
        doSettleBilling(pre, session);
        publishCollabEvents(session, toolEvents);

        executionRegistry.unregisterExecution(sessionId);
        executionRegistry.releasePermit();
        clearScopeContext(sessionId);
    }

    /**
     * 失败路径清理（preflight 成功后执行阶段的异常）
     * 取消计费 → 释放 permit（如已获取）→ 清理上下文
     */
    public void onError(String sessionId, AgentPreflightService.PreflightResult pre, boolean permitAcquired) {
        if (pre != null && pre.executionMode() == AgentExecutionMode.MISSION) {
            if (permitAcquired) {
                executionRegistry.unregisterExecution(sessionId);
                executionRegistry.releasePermit();
            }
            clearScopeContext(sessionId);
            return;
        }
        if (pre != null && pre.placeholderMessageId() != null) {
            try {
                if (perSegmentWriteEnabled) {
                    sessionService.finalizePlaceholderStatusOnly(
                            pre.placeholderMessageId(), AgentConstants.MESSAGE_STATUS_FAILED, null);
                } else {
                    sessionService.finalizePlaceholderMessage(
                            pre.placeholderMessageId(), "", AgentConstants.MESSAGE_STATUS_FAILED, null);
                }
            } catch (Exception e) {
                log.warn("Failed to finalize placeholder on error: {}", e.getMessage());
            }
        }
        sessionService.clearSessionGenerating(sessionId);
        if (pre != null) {
            doCancelBilling(pre, pre.session());
        }
        if (permitAcquired) {
            executionRegistry.unregisterExecution(sessionId);
            executionRegistry.releasePermit();
        }
        clearScopeContext(sessionId);
    }

    /**
     * 取消路径清理
     * 保存已生成的部分内容/工具记录 → 取消计费 → 释放 permit → 清理上下文
     */
    public void onCancelled(String sessionId, AgentPreflightService.PreflightResult pre,
                             String partialContent, List<AgentStreamEvent> toolEvents, boolean permitAcquired) {
        if (pre != null && pre.executionMode() == AgentExecutionMode.MISSION) {
            if (permitAcquired) {
                executionRegistry.unregisterExecution(sessionId);
                executionRegistry.releasePermit();
            }
            clearScopeContext(sessionId);
            return;
        }
        if (pre != null && pre.placeholderMessageId() != null) {
            try {
                if (perSegmentWriteEnabled) {
                    // 文本段落已逐条入库；取消路径仅把占位消息状态推到 cancelled。
                    sessionService.finalizePlaceholderStatusOnly(
                            pre.placeholderMessageId(),
                            AgentConstants.MESSAGE_STATUS_CANCELLED,
                            null);
                } else {
                    sessionService.finalizePlaceholderMessage(
                            pre.placeholderMessageId(),
                            partialContent,
                            AgentConstants.MESSAGE_STATUS_CANCELLED,
                            null);
                }
            } catch (Exception e) {
                log.warn("Failed to finalize placeholder on cancel: {}", e.getMessage());
            }
        }
        sessionService.clearSessionGenerating(sessionId);

        // 工具事件已在订阅回调中实时落库。

        if (pre != null) {
            doCancelBilling(pre, pre.session());
        }
        if (permitAcquired) {
            executionRegistry.unregisterExecution(sessionId);
            executionRegistry.releasePermit();
        }
        clearScopeContext(sessionId);
    }

    // ==================== 私有方法 ====================

    /**
     * 清理 ThreadLocal 作用域上下文。
     * 接受显式 sessionId，避免跨线程时 getCurrentSessionId() 返回 null。
     * 幂等：同一 sessionId 仅执行一次实质清理，后续调用为空操作。
     * Package-private 供同包的 SaaAgentRunner finally 块调用（兜底）。
     */
    void clearScopeContext(String sessionId) {
        if (sessionId == null || !cleanedSessions.add(sessionId)) {
            // null 或已清理过，跳过
            return;
        }
        AgentContextHolder.clearContext();
        SessionContextHolder.clearCurrentSessionId();
        SessionContextHolder.clear(sessionId);
        StructuredOutputTools.clearResult(sessionId);
        StatusEmittingInterceptor.clearSession(sessionId);
        // 注意：Working Memory 不在此处清理。
        // Working Memory 是 session 级缓存，需要跨多轮执行持久存在，
        // 仅在 session 归档/删除时由 clearSessionMemory() 显式清理。
        // cleanedSessions 中的 sessionId 会随 evictCleanedSessions() 定期清理
    }

    /**
     * 清理 session 级缓存资源（Working Memory）。
     * 仅在 session 归档/删除等终态转换时调用，不在每轮执行结束时调用。
     */
    public void clearSessionMemory(String sessionId) {
        if (sessionId != null) {
            workingMemoryStore.clearSession(sessionId);
            log.debug("Cleared working memory for archived/deleted session: {}", sessionId);
        }
    }

    /**
     * 定期清理幂等标记集合，由外部调度器调用（如 SaaSessionService 的 @Scheduled）。
     * 防止 cleanedSessions 无限增长。
     */
    public void evictCleanedSessions() {
        int size = cleanedSessions.size();
        if (size > 0) {
            cleanedSessions.clear();
            log.debug("Evicted {} cleaned session markers", size);
        }
    }

    /**
     * 工具事件实时落库（来自 SaaAgentRunner 流订阅回调）。
     *
     * <p>替代先前在 {@code onSuccess} / {@code onCancelled} 末端批量写的做法：
     * 批量写要求客户端一定要等到流完整结束才能在历史视图看到 tool_call / tool_result，
     * 中途掉线或跨进程重连时已经发生的工具行为都会丢失。本方法在 SSE 发出事件的
     * 同一时刻落库，保证重连后 DB 回放与流回放一致。
     *
     * <p>MISSION 执行模式由 {@code MissionExecutor.persistStepMessages} 负责落库，
     * 此处跳过以避免重复写入。
     *
     * <p>任何异常仅 WARN，不影响主流程 / SSE 下发（审计 fail-open 优先于用户体验受损）。
     */
    public void onToolEvent(String sessionId, AgentStreamEvent event,
                            com.actionow.agent.config.constant.AgentExecutionMode executionMode) {
        if (event == null || sessionId == null) return;
        if (executionMode == com.actionow.agent.config.constant.AgentExecutionMode.MISSION) return;
        try {
            if (AgentConstants.EVENT_TOOL_CALL.equals(event.getType())) {
                sessionService.saveToolCallMessage(
                        sessionId, event.getToolCallId(),
                        event.getToolName(),
                        event.getToolArguments());
            } else if (AgentConstants.EVENT_TOOL_RESULT.equals(event.getType())) {
                Map<String, Object> parsed = streamProcessor.parseToolResultAsMap(event.getContent());
                boolean success = !(parsed.get("success") instanceof Boolean value) || value;
                sessionService.saveToolResultMessage(
                        sessionId, event.getToolCallId(),
                        event.getToolName(), success, event.getContent());
            }
        } catch (Exception e) {
            log.warn("Failed to persist tool event immediately sessionId={}, type={}, toolName={}: {}",
                    sessionId, event.getType(), event.getToolName(), e.getMessage());
        }
    }

    private void publishCollabEvents(AgentSessionEntity session, List<AgentStreamEvent> toolEvents) {
        try {
            for (AgentStreamEvent event : toolEvents) {
                if (AgentConstants.EVENT_TOOL_RESULT.equals(event.getType()) &&
                        streamProcessor.isCreateTool(event.getToolName())) {
                    String entityType = streamProcessor.extractTargetEntityType(event.getToolName());
                    String entityId = streamProcessor.extractTargetEntityId(
                            event.getToolArguments() != null ? event.getToolArguments() : Map.of());
                    if (entityType != null && entityId != null) {
                        collabEventPublisher.publishToolResult(
                                session.getId(), session.getAgentType(),
                                event.getToolCallId(), event.getToolName(),
                                event.getToolResult() != null ? event.getToolResult() : Map.of(),
                                entityType, entityId);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to publish collab events: {}", e.getMessage());
        }
    }

    private void doRecordTokenUsage(AgentPreflightService.PreflightResult pre,
                                     AgentSessionEntity session,
                                     long inputTokens, long outputTokens) {
        if (pre.billingSession() == null || session == null) return;
        if (inputTokens <= 0 && outputTokens <= 0) return;
        try {
            AgentStreamEvent.TokenUsage tokenUsage = AgentStreamEvent.TokenUsage.builder()
                    .promptTokens(inputTokens)
                    .completionTokens(outputTokens)
                    .totalTokens(inputTokens + outputTokens)
                    .estimated(true)
                    .build();
            billingIntegrationService.recordTokenUsage(session.getId(), null, tokenUsage);
        } catch (Exception e) {
            log.warn("Failed to record token usage before settlement: {}", e.getMessage());
        }
    }

    private void doSettleBilling(AgentPreflightService.PreflightResult pre, AgentSessionEntity session) {
        if (pre.billingSession() == null || session == null) return;
        try {
            billingIntegrationService.settleBilling(session.getId(), session.getWorkspaceId(), session.getUserId());
        } catch (Exception e) {
            log.error("Failed to settle billing session: {}", e.getMessage());
        }
    }

    private void doCancelBilling(AgentPreflightService.PreflightResult pre, AgentSessionEntity session) {
        if (pre.billingSession() == null || session == null) return;
        try {
            billingIntegrationService.cancelBilling(session.getId(), session.getWorkspaceId(), session.getUserId());
        } catch (Exception e) {
            log.error("Failed to cancel billing session: {}", e.getMessage());
        }
    }
}
