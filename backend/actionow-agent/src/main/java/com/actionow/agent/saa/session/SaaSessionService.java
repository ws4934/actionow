package com.actionow.agent.saa.session;

import com.actionow.agent.config.AgentRuntimeConfigService;
import com.actionow.agent.constant.AgentConstants;
import com.actionow.agent.constant.SessionStatus;
import com.actionow.agent.core.context.SessionContextHolder;
import com.actionow.agent.core.execution.ExecutionRegistry;
import com.actionow.agent.saa.execution.AgentTeardownService;
import com.actionow.agent.core.scope.AgentContext;
import com.actionow.agent.dto.request.SessionQueryRequest;
import com.actionow.agent.entity.AgentMessage;
import com.actionow.agent.entity.AgentSessionEntity;
import com.actionow.agent.mapper.AgentMessageMapper;
import com.actionow.agent.mapper.AgentSessionMapper;
import com.actionow.common.core.context.UserContext;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.result.PageResult;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SAA 会话服务
 * 替代 v1 的 ActionowSessionService（实现 ADK BaseSessionService 接口）
 *
 * 核心差异：
 * - 不实现 ADK BaseSessionService，是普通 @Service
 * - 移除 RxJava Single/Maybe/Completable 包装，全部使用纯 Java 同步方法
 * - 移除 ADK Session / Event 相关结构体
 * - 直接返回 AgentSessionEntity 而不是 ADK Session 对象
 *
 * 会话生命周期（与 v1 相同）：
 * - ACTIVE → ARCHIVED → DELETED（软删除 90 天后物理清理）
 *
 * @author Actionow
 */
@Slf4j
@Service
public class SaaSessionService {

    private final AgentSessionMapper sessionMapper;
    private final AgentMessageMapper messageMapper;
    private final ExecutionRegistry executionRegistry;
    private final AgentRuntimeConfigService runtimeConfig;
    private final AgentTeardownService agentTeardownService;
    private final com.actionow.agent.metrics.AgentMetrics agentMetrics;

    public SaaSessionService(AgentSessionMapper sessionMapper,
                              AgentMessageMapper messageMapper,
                              ExecutionRegistry executionRegistry,
                              AgentRuntimeConfigService runtimeConfig,
                              @org.springframework.context.annotation.Lazy AgentTeardownService agentTeardownService,
                              com.actionow.agent.metrics.AgentMetrics agentMetrics) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.executionRegistry = executionRegistry;
        this.runtimeConfig = runtimeConfig;
        this.agentTeardownService = agentTeardownService;
        this.agentMetrics = agentMetrics;
    }

    // ==================== 会话创建 ====================

    /**
     * 创建会话（同步）
     * 返回已持久化的 AgentSessionEntity
     */
    @Transactional
    public AgentSessionEntity createSession(String agentType, String userId,
                                             String workspaceId, String scriptId) {
        return createSession(agentType, userId, workspaceId, scriptId, null, null);
    }

    /**
     * 创建会话（含 skillNames）
     */
    @Transactional
    public AgentSessionEntity createSession(String agentType, String userId,
                                             String workspaceId, String scriptId,
                                             java.util.List<String> skillNames) {
        return createSession(agentType, userId, workspaceId, scriptId, skillNames, null);
    }

    /**
     * 创建会话（含 skillNames + scopeContext）
     */
    @Transactional
    public AgentSessionEntity createSession(String agentType, String userId,
                                             String workspaceId, String scriptId,
                                             java.util.List<String> skillNames,
                                             java.util.Map<String, Object> scopeContext) {
        ensureTenantContext();
        handleActiveSessionLimit(userId, scriptId);

        AgentSessionEntity entity = new AgentSessionEntity();
        entity.setWorkspaceId(workspaceId);
        entity.setUserId(userId);
        entity.setAgentType(agentType);
        if (scopeContext != null) {
            entity.setScopeContext(new java.util.HashMap<>(scopeContext));
        } else if (scriptId != null) {
            entity.setScriptId(scriptId);
        }
        entity.setStatus(SessionStatus.ACTIVE.getCode());
        entity.setMessageCount(0);
        entity.setTotalTokens(0L);
        entity.setLastActiveAt(LocalDateTime.now());
        entity.setSkillNames(skillNames);

        sessionMapper.insert(entity);
        log.info("Created agent session: {} for user: {} in workspace: {}, scriptId: {}, skillNames: {}",
                entity.getId(), userId, workspaceId, scriptId, skillNames);

        return entity;
    }

    /**
     * 创建 Mission 内部运行时会话。
     * 该会话用于隔离 ChatMemory 与内部执行上下文，不应在用户会话列表中展示。
     */
    @Transactional
    public AgentSessionEntity createInternalMissionSession(AgentSessionEntity sourceSession) {
        ensureTenantContext();

        AgentSessionEntity entity = new AgentSessionEntity();
        entity.setWorkspaceId(sourceSession.getWorkspaceId());
        entity.setUserId(sourceSession.getUserId());
        entity.setAgentType(sourceSession.getAgentType());
        entity.setScope(sourceSession.getScope());
        if (sourceSession.getScopeContext() != null) {
            entity.setScopeContext(new java.util.HashMap<>(sourceSession.getScopeContext()));
        }
        entity.setStatus(SessionStatus.ACTIVE.getCode());
        entity.setMessageCount(0);
        entity.setTotalTokens(0L);
        entity.setLastActiveAt(LocalDateTime.now());
        entity.setSkillNames(sourceSession.getSkillNames());

        Map<String, Object> extras = new java.util.HashMap<>();
        if (sourceSession.getExtras() != null) {
            extras.putAll(sourceSession.getExtras());
        }
        extras.put("internalSession", true);
        extras.put("sessionKind", "MISSION_RUNTIME");
        extras.put("sourceSessionId", sourceSession.getId());
        entity.setExtras(extras);

        sessionMapper.insert(entity);
        log.info("Created internal mission session: {} from source session: {}",
                entity.getId(), sourceSession.getId());
        return entity;
    }

    /**
     * 基于执行上下文创建 Mission 运行时会话，不依赖来源 AgentSessionEntity 必须存在。
     */
    @Transactional
    public AgentSessionEntity createInternalMissionSession(AgentContext agentContext,
                                                           String userId,
                                                           String workspaceId) {
        ensureTenantContext();

        AgentSessionEntity entity = new AgentSessionEntity();
        entity.setWorkspaceId(workspaceId);
        entity.setUserId(userId);
        entity.setAgentType(agentContext != null && agentContext.getAgentType() != null
                ? agentContext.getAgentType()
                : "COORDINATOR");
        if (agentContext != null) {
            entity.setScope(agentContext.getScope() != null ? agentContext.getScope().getCode() : null);
            entity.setScopeContext(extractScopeContext(agentContext));
            entity.setSkillNames(agentContext.getSkillNames());
        }
        entity.setStatus(SessionStatus.ACTIVE.getCode());
        entity.setMessageCount(0);
        entity.setTotalTokens(0L);
        entity.setLastActiveAt(LocalDateTime.now());

        Map<String, Object> extras = new java.util.HashMap<>();
        extras.put("internalSession", true);
        extras.put("sessionKind", "MISSION_RUNTIME");
        if (agentContext != null && agentContext.getSessionId() != null) {
            extras.put("sourceSessionId", agentContext.getSessionId());
        }
        entity.setExtras(extras);

        sessionMapper.insert(entity);
        log.info("Created internal mission session: {} from agent context session: {}",
                entity.getId(), agentContext != null ? agentContext.getSessionId() : null);
        return entity;
    }

    // ==================== 会话查询 ====================

    /**
     * 获取会话实体
     */
    public AgentSessionEntity getSessionEntity(String sessionId) {
        ensureTenantContext();
        AgentSessionEntity entity = sessionMapper.selectById(sessionId);
        if (entity == null || entity.getIsDeleted()) {
            throw new BusinessException("0709003", "会话不存在");
        }
        return entity;
    }

    /**
     * 查询用户会话（分页）
     */
    public PageResult<AgentSessionEntity> queryUserSessions(String userId, SessionQueryRequest request) {
        long total = sessionMapper.countByStandalone(userId, request.getStandalone(), request.getScriptId());
        List<AgentSessionEntity> list = sessionMapper.selectByStandalone(
                userId, request.getStandalone(), request.getScriptId(),
                request.getSize(), request.getOffset());
        return PageResult.of((long) request.getPage(), (long) request.getSize(), total, list);
    }

    /**
     * 获取用户活跃会话列表
     */
    public List<AgentSessionEntity> listActiveUserSessions(String userId, int limit) {
        return sessionMapper.selectActiveByUserId(userId, limit);
    }

    /**
     * 获取用户归档会话列表
     */
    public List<AgentSessionEntity> listArchivedUserSessions(String userId, int limit) {
        return sessionMapper.selectArchivedByUserId(userId, limit);
    }

    /**
     * 获取工作空间的活跃会话列表（内部调用）
     */
    public List<AgentSessionEntity> listWorkspaceSessions(String workspaceId, int limit) {
        return sessionMapper.selectActiveByWorkspace(workspaceId, limit);
    }

    /**
     * 获取用户的活跃会话列表（按用户 ID 筛选）
     */
    public List<AgentSessionEntity> listUserSessions(String userId, int limit) {
        return sessionMapper.selectByUserId(userId, limit);
    }

    /**
     * 获取会话消息列表
     */
    public List<AgentMessage> getMessages(String sessionId) {
        return messageMapper.selectBySessionId(sessionId);
    }

    // ==================== 会话生命周期 ====================

    /**
     * 结束会话（转为归档）
     */
    public void endSession(String sessionId) {
        archiveSession(sessionId);
    }

    /**
     * 归档会话
     */
    public boolean archiveSession(String sessionId) {
        AgentSessionEntity entity = sessionMapper.selectById(sessionId);
        if (entity == null || !SessionStatus.ACTIVE.getCode().equals(entity.getStatus())) {
            return false;
        }
        entity.setStatus(SessionStatus.ARCHIVED.getCode());
        entity.setArchivedAt(LocalDateTime.now());
        sessionMapper.updateById(entity);
        agentTeardownService.clearSessionMemory(sessionId);
        log.info("Session archived: {}", sessionId);
        return true;
    }

    /**
     * 恢复归档会话
     */
    public boolean resumeSession(String sessionId) {
        AgentSessionEntity entity = sessionMapper.selectById(sessionId);
        if (entity == null || !SessionStatus.ARCHIVED.getCode().equals(entity.getStatus())) {
            return false;
        }
        handleActiveSessionLimit(entity.getUserId(), entity.getScriptId());
        entity.setStatus(SessionStatus.ACTIVE.getCode());
        entity.setArchivedAt(null);
        sessionMapper.updateById(entity);
        log.info("Session resumed: {}", sessionId);
        return true;
    }

    /**
     * 删除会话（软删除）
     */
    @Transactional
    public void deleteSessionSync(String sessionId) {
        ensureTenantContext();
        AgentSessionEntity entity = sessionMapper.selectById(sessionId);
        if (entity != null) {
            // MyBatis-Plus deleteById 自动执行逻辑删除: UPDATE SET deleted=1 WHERE id=?
            // 注意：updateById 不会更新 logic-delete-field，所以不能手动 setDeleted+updateById
            sessionMapper.deleteById(sessionId);
            agentTeardownService.clearSessionMemory(sessionId);
            log.info("Session soft-deleted: {}", sessionId);
        }
    }

    // ==================== 消息管理 ====================

    /**
     * 保存用户消息
     */
    @Transactional
    public AgentMessage saveUserMessage(String sessionId, String content, Integer tokenCount, List<String> attachmentIds) {
        AgentMessage message = new AgentMessage();
        message.setSessionId(sessionId);
        message.setRole(AgentConstants.ROLE_USER);
        message.setContent(content);
        message.setTokenCount(tokenCount);
        message.setAttachmentIds(attachmentIds != null && !attachmentIds.isEmpty() ? attachmentIds : null);
        message.setSequence(nextSequence(sessionId));
        messageMapper.insert(message);
        incrementSessionMessageCount(sessionId, tokenCount);
        return message;
    }

    /**
     * 保存助手消息
     */
    @Transactional
    public AgentMessage saveAssistantMessage(String sessionId, String content, Integer tokenCount) {
        AgentMessage message = new AgentMessage();
        message.setSessionId(sessionId);
        message.setRole(AgentConstants.ROLE_ASSISTANT);
        message.setContent(content);
        message.setTokenCount(tokenCount);
        message.setSequence(nextSequence(sessionId));
        messageMapper.insert(message);
        incrementSessionMessageCount(sessionId, tokenCount);
        return message;
    }

    /**
     * 保存带扩展属性的助手消息（用于 MissionCard 等投影消息）。
     */
    @Transactional
    public AgentMessage saveAssistantMessageWithExtras(String sessionId, String content,
                                                       Integer tokenCount, Map<String, Object> extras) {
        AgentMessage message = new AgentMessage();
        message.setSessionId(sessionId);
        message.setRole(AgentConstants.ROLE_ASSISTANT);
        message.setContent(content);
        message.setTokenCount(tokenCount);
        message.setExtras(extras != null && !extras.isEmpty() ? new LinkedHashMap<>(extras) : null);
        message.setSequence(nextSequence(sessionId));
        messageMapper.insert(message);
        incrementSessionMessageCount(sessionId, tokenCount);
        return message;
    }

    /**
     * 创建占位消息（generating 状态，用于 SSE 客户端重连）
     */
    @Transactional
    public AgentMessage savePlaceholderMessage(String sessionId) {
        AgentMessage message = new AgentMessage();
        message.setSessionId(sessionId);
        message.setRole(AgentConstants.ROLE_ASSISTANT);
        message.setContent("");
        message.setStatus(AgentConstants.MESSAGE_STATUS_GENERATING);
        message.setSequence(nextSequence(sessionId));
        messageMapper.insert(message);
        return message;
    }

    /**
     * 追加一段 assistant 段落 —— 用于 Step 2 "每个 message event 一行" 的写入路径。
     *
     * <p>与 {@link #saveAssistantMessage} 的区别：
     * <ul>
     *   <li>带 {@code eventId}（写入 extras）用于跨 pod / 重连去重；</li>
     *   <li>显式标记 {@code extras.kind=assistant_segment}，便于查询侧过滤 / 统计；</li>
     *   <li>{@code iteration} 写入 extras 供前端重建 ReAct 步序。</li>
     * </ul>
     *
     * <p>不影响 {@link #savePlaceholderMessage} 创建的占位消息（仍保持 generating 状态，
     * 作为 session 级"正在生成"指示 + 心跳挂载点）；段落作为 status=completed 的独立行插入，
     * sequence 单调递增，与 tool_call / tool_result 行按时间顺序穿插。
     *
     * @param content 段落文本（空 / null 将被跳过，返回 null）
     * @param eventId 来源 SSE 事件的 eventId（bridge 分配的单调 id，nullable）
     * @param iteration ReAct 迭代轮次（nullable）
     */
    @Transactional
    public AgentMessage appendAssistantSegment(String sessionId, String content,
                                               Long eventId, Integer iteration) {
        if (content == null || content.isBlank()) return null;

        // 跨 pod 重放 / runner 重连幂等：相同 (session_id, event_id) 已入库则跳过。
        // 分区表无法加全局 UNIQUE 约束（必须含分区键），用 app 层 SELECT 做 dedup。
        if (eventId != null) {
            AgentMessage existing = messageMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgentMessage>()
                            .eq(AgentMessage::getSessionId, sessionId)
                            .eq(AgentMessage::getEventId, eventId)
                            .last("LIMIT 1"));
            if (existing != null) {
                log.debug("skip duplicate assistant segment sessionId={} eventId={} existingId={}",
                        sessionId, eventId, existing.getId());
                agentMetrics.recordSegmentWriteDedup();
                return existing;
            }
        }

        AgentMessage message = new AgentMessage();
        message.setSessionId(sessionId);
        message.setRole(AgentConstants.ROLE_ASSISTANT);
        message.setContent(content);
        message.setStatus(AgentConstants.MESSAGE_STATUS_COMPLETED);
        message.setSequence(nextSequence(sessionId));
        message.setEventId(eventId);

        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("kind", "assistant_segment");
        if (eventId != null) extras.put("eventId", eventId);
        if (iteration != null) extras.put("iteration", iteration);
        message.setExtras(extras);

        messageMapper.insert(message);
        incrementSessionMessageCount(sessionId, null);
        agentMetrics.recordSegmentWritten();
        return message;
    }

    /**
     * 完成占位消息：仅收尾状态，不覆盖 content —— 用于 Step 2 写入模式。
     *
     * <p>在"每个 message event 一行"的新路径下，占位消息仅作 generating→completed
     * 的状态标记；真正的文本内容已由 {@link #appendAssistantSegment} 独立入库。
     * 此方法把 placeholder 状态推到终态（completed / failed / cancelled）同时保留
     * 原 content=""，避免与段落行内容重复。
     *
     * <p>与 {@link #finalizePlaceholderMessage} 对偶：前者同时写 content，后者只动 status。
     */
    @Transactional
    public void finalizePlaceholderStatusOnly(String messageId, String status, Integer tokenCount) {
        AgentMessage message = messageMapper.selectById(messageId);
        if (message != null) {
            message.setStatus(status != null ? status : AgentConstants.MESSAGE_STATUS_COMPLETED);
            if (tokenCount != null) {
                message.setTokenCount(tokenCount);
            }
            messageMapper.updateById(message);
            if (tokenCount != null && tokenCount > 0) {
                updateSessionTokenCount(message.getSessionId(), tokenCount);
            }
            // content 保持原 "" —— per-segment-write 路径的标记
            agentMetrics.recordPlaceholderFinalized(
                    message.getContent() == null || message.getContent().isBlank());
        }
    }

    /**
     * 完成占位消息（更新内容和状态）
     */
    @Transactional
    public void finalizePlaceholderMessage(String messageId, String content,
                                            String status, Integer tokenCount) {
        AgentMessage message = messageMapper.selectById(messageId);
        if (message != null) {
            message.setContent(content);
            message.setStatus(status != null ? status : AgentConstants.MESSAGE_STATUS_COMPLETED);
            if (tokenCount != null) {
                message.setTokenCount(tokenCount);
            }
            messageMapper.updateById(message);
            if (tokenCount != null && tokenCount > 0) {
                updateSessionTokenCount(message.getSessionId(), tokenCount);
            }
            agentMetrics.recordPlaceholderFinalized(content == null || content.isBlank());
        }
    }

    /**
     * 保存工具调用消息
     */
    @Transactional
    public AgentMessage saveToolCallMessage(String sessionId, String toolCallId,
                                             String toolName, Map<String, Object> toolArguments) {
        AgentMessage message = new AgentMessage();
        message.setSessionId(sessionId);
        message.setRole(AgentConstants.ROLE_TOOL);
        message.setToolCallId(toolCallId);
        message.setToolName(toolName);
        message.setToolArguments(toolArguments != null ? new LinkedHashMap<>(toolArguments) : null);
        message.setContent(toolName);
        message.setSequence(nextSequence(sessionId));
        messageMapper.insert(message);
        incrementSessionMessageCount(sessionId);
        return message;
    }

    /**
     * 保存工具结果消息
     */
    @Transactional
    public AgentMessage saveToolResultMessage(String sessionId, String toolCallId,
                                               String toolName, boolean success, String result) {
        AgentMessage message = new AgentMessage();
        message.setSessionId(sessionId);
        message.setRole(AgentConstants.ROLE_TOOL);
        message.setToolCallId(toolCallId);
        message.setToolName(toolName);
        message.setContent(result);
        Map<String, Object> toolResult = new LinkedHashMap<>();
        toolResult.put("success", success);
        toolResult.put("output", result != null ? result : "");
        message.setToolResult(toolResult);
        message.setStatus(success ? AgentConstants.MESSAGE_STATUS_COMPLETED : AgentConstants.MESSAGE_STATUS_FAILED);
        message.setSequence(nextSequence(sessionId));
        messageMapper.insert(message);
        incrementSessionMessageCount(sessionId);
        return message;
    }

    /**
     * 保存消息（通用方法，兼容 v1 接口）
     */
    @Transactional
    public AgentMessage saveMessage(String sessionId, String role, String content, Integer tokenCount) {
        AgentMessage message = new AgentMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setTokenCount(tokenCount);
        message.setStatus(AgentConstants.MESSAGE_STATUS_COMPLETED);
        message.setSequence(nextSequence(sessionId));
        messageMapper.insert(message);
        incrementSessionMessageCount(sessionId, tokenCount);
        return message;
    }

    /**
     * 更新消息的 extras 字段（用于设置 CHECKPOINT 标记等）
     */
    @Transactional
    public void updateMessageExtras(String sessionId, String messageId, Map<String, Object> extras) {
        AgentMessage msg = messageMapper.selectById(messageId);
        if (msg != null && sessionId.equals(msg.getSessionId())) {
            msg.setExtras(extras);
            messageMapper.updateById(msg);
        }
    }

    /**
     * 更新占位消息的心跳时间戳（由 {@code AgentHeartbeatScheduler} 每 N 秒调用一次）。
     *
     * <p>采用轻量级 updateById（无 where 条件收窄）是可接受的，因为：
     * <ul>
     *   <li>心跳只在 generating 占位消息上生效 —— 调用方负责把 messageId 限定在占位消息之上</li>
     *   <li>字段 {@code lastHeartbeatAt} 独立于 content/status，不会覆盖主流程写入</li>
     * </ul>
     *
     * <p>失败只记 WARN，不影响 SSE 心跳事件本身的下发。
     */
    public void touchHeartbeat(String messageId) {
        if (messageId == null) return;
        try {
            AgentMessage patch = new AgentMessage();
            patch.setId(messageId);
            patch.setLastHeartbeatAt(LocalDateTime.now());
            messageMapper.updateById(patch);
        } catch (Exception e) {
            log.warn("心跳时间戳写入失败 messageId={}: {}", messageId, e.getMessage());
        }
    }

    /**
     * 批量更新一组占位消息的心跳时间戳 —— 心跳调度器每 tick 汇总本轮所有 in-flight 占位
     * 消息 id，一次 UPDATE ... WHERE id IN (...) 完成。相比逐条 updateById 将写放大从 N 降到 1，
     * 在高并发会话（>500 activeSessions）场景下显著降低 DB 压力。
     *
     * <p>失败只记 WARN；心跳事件本身的 SSE 下发不受影响。
     */
    public void touchHeartbeatBatch(java.util.List<String> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) return;
        try {
            messageMapper.touchHeartbeatBatch(messageIds, LocalDateTime.now());
        } catch (Exception e) {
            log.warn("批量心跳时间戳写入失败 count={}: {}", messageIds.size(), e.getMessage());
        }
    }

    /**
     * skip-placeholder 路径：标记 session 级"正在生成"。替代空 placeholder 行。
     */
    public void markSessionGenerating(String sessionId) {
        if (sessionId == null) return;
        try {
            sessionMapper.markGenerating(sessionId);
        } catch (Exception e) {
            log.warn("标记 session 生成状态失败 sessionId={}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * skip-placeholder 路径：清除 session 级"正在生成"标记。Teardown 所有终态调用。
     */
    public void clearSessionGenerating(String sessionId) {
        if (sessionId == null) return;
        try {
            sessionMapper.clearGenerating(sessionId);
        } catch (Exception e) {
            log.warn("清除 session 生成状态失败 sessionId={}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * skip-placeholder 路径：批量刷新 session 心跳。对应 {@link #touchHeartbeatBatch}
     * 的 session 级对偶 —— 心跳挂在 session 而非占位消息上。
     */
    public void touchSessionHeartbeatBatch(java.util.List<String> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) return;
        try {
            sessionMapper.touchSessionHeartbeatBatch(sessionIds);
        } catch (Exception e) {
            log.warn("批量 session 心跳写入失败 count={}: {}", sessionIds.size(), e.getMessage());
        }
    }

    /**
     * 查询当前仍处于 generating 状态的 assistant 占位消息。
     * 用于会话恢复端点判断是否有"正在生成但 SSE 已断开"的流。
     * 返回 null 表示当前无 in-flight 生成。
     */
    public AgentMessage findInFlightPlaceholder(String sessionId) {
        if (sessionId == null) return null;
        try {
            return messageMapper.selectInFlightPlaceholder(sessionId);
        } catch (Exception e) {
            log.warn("查询 in-flight 占位消息失败 sessionId={}: {}", sessionId, e.getMessage());
            return null;
        }
    }

    // ==================== 维护任务 ====================

    /**
     * 定期清理超时的 generating 状态消息（超过 10 分钟）
     */
    @Scheduled(fixedDelayString = "${actionow.agent.session.cleanup-interval-ms:600000}")
    public void cleanupStaleGeneratingMessages() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusMinutes(10);
            int updated = messageMapper.cleanupStaleGeneratingMessages(cutoff);
            if (updated > 0) {
                log.info("Cleaned up {} stale generating messages", updated);
            }
        } catch (Exception e) {
            log.error("Failed to cleanup stale generating messages: {}", e.getMessage());
        }
    }

    /**
     * 定期清理 SessionContextHolder 中过期的内存上下文（超过 30 分钟未活动的）
     */
    @org.springframework.beans.factory.annotation.Value("${actionow.agent.session.context-expiry-ms:1800000}")
    public void setContextExpiryMs(long expiryMs) {
        SessionContextHolder.setExpiryMs(expiryMs);
    }

    @Scheduled(fixedDelayString = "${actionow.agent.session.context-cleanup-interval-ms:300000}")
    public void cleanupExpiredSessionContexts() {
        try {
            int before = SessionContextHolder.size();
            SessionContextHolder.cleanupExpired();
            int after = SessionContextHolder.size();
            if (before > after) {
                log.info("Cleaned up {} expired session contexts, remaining: {}", before - after, after);
            }
            // 顺便清理 teardown 幂等标记集合
            agentTeardownService.evictCleanedSessions();
        } catch (Exception e) {
            log.error("Failed to cleanup expired session contexts: {}", e.getMessage());
        }
    }

    /**
     * 归档空闲会话
     */
    public int archiveIdleSessions(int idleArchiveDays, int batchSize) {
        // NOTE: mapper archives all sessions older than threshold in one UPDATE;
        // batchSize is accepted for API compatibility but not enforced here.
        LocalDateTime cutoff = LocalDateTime.now().minusDays(idleArchiveDays);
        return sessionMapper.archiveIdleSessions(cutoff, LocalDateTime.now());
    }

    public int cleanupDeletedSessions(int retentionDays, int batchSize) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        List<AgentSessionEntity> sessions = sessionMapper.selectSessionsForPermanentDelete(cutoff, batchSize);
        sessions.forEach(s -> sessionMapper.deleteById(s.getId()));
        return sessions.size();
    }

    // ==================== 私有方法 ====================

    private void ensureTenantContext() {
        UserContext context = UserContextHolder.getContext();
        if (context == null) {
            log.warn("UserContext is null, tenant context not set");
            return;
        }
        String tenantSchema = context.getTenantSchema();
        if (tenantSchema == null || tenantSchema.isBlank()) {
            log.warn("TenantSchema not set in UserContext, workspaceId={}", context.getWorkspaceId());
        }
    }

    private Map<String, Object> extractScopeContext(AgentContext agentContext) {
        Map<String, Object> scopeContext = new java.util.HashMap<>();
        if (agentContext == null) {
            return scopeContext;
        }
        if (agentContext.getScriptId() != null) scopeContext.put("scriptId", agentContext.getScriptId());
        if (agentContext.getEpisodeId() != null) scopeContext.put("episodeId", agentContext.getEpisodeId());
        if (agentContext.getStoryboardId() != null) scopeContext.put("storyboardId", agentContext.getStoryboardId());
        if (agentContext.getCharacterId() != null) scopeContext.put("characterId", agentContext.getCharacterId());
        if (agentContext.getSceneId() != null) scopeContext.put("sceneId", agentContext.getSceneId());
        if (agentContext.getPropId() != null) scopeContext.put("propId", agentContext.getPropId());
        if (agentContext.getStyleId() != null) scopeContext.put("styleId", agentContext.getStyleId());
        if (agentContext.getAssetId() != null) scopeContext.put("assetId", agentContext.getAssetId());
        return scopeContext;
    }

    private void handleActiveSessionLimit(String userId, String scriptId) {
        if (scriptId != null) {
            int maxPerScope = runtimeConfig.getSessionMaxActivePerScope();
            int activeCount = sessionMapper.countActiveByUserAndScript(userId, scriptId);
            if (activeCount >= maxPerScope) {
                int archived = sessionMapper.archiveOldestSessionByScript(userId, scriptId);
                if (archived > 0) {
                    log.info("Auto-archived oldest session for user: {} in script: {} (limit: {})",
                            userId, scriptId, maxPerScope);
                }
            }
        } else {
            int maxGlobal = runtimeConfig.getSessionMaxActiveGlobal();
            int activeCount = sessionMapper.countActiveGlobalByUser(userId);
            if (activeCount >= maxGlobal) {
                int archived = sessionMapper.archiveOldestGlobalSession(userId);
                if (archived > 0) {
                    log.info("Auto-archived oldest global session for user: {} (limit: {})",
                            userId, maxGlobal);
                }
            }
        }
    }

    private void incrementSessionMessageCount(String sessionId, Integer tokenCount) {
        sessionMapper.incrementMessageStats(sessionId, tokenCount);
    }

    private void incrementSessionMessageCount(String sessionId) {
        sessionMapper.incrementMessageStats(sessionId, null);
    }

    private void updateSessionTokenCount(String sessionId, Integer tokenCount) {
        if (tokenCount != null && tokenCount > 0) {
            sessionMapper.addTokens(sessionId, tokenCount);
        }
    }

    /**
     * 为会话生成下一个消息序号。
     * <p>仅安全于 {@code @Transactional} 方法内调用：先取事务级咨询锁串行化同一 session 的并发写入，
     * 再读取当前 MAX(sequence) + 1。锁在事务结束时自动释放。
     */
    private int nextSequence(String sessionId) {
        messageMapper.acquireSessionSeqLock(sessionId);
        return messageMapper.getMaxSequence(sessionId) + 1;
    }
}
