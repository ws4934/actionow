package com.actionow.collab.websocket;

import com.actionow.collab.constant.CollabConstants;
import com.actionow.collab.dto.*;
import com.actionow.collab.dto.message.*;
import com.actionow.collab.manager.EntityCollaborationManager;
import com.actionow.collab.manager.PresenceManager;
import com.actionow.collab.manager.SessionManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 协作 WebSocket 处理器
 * 处理所有协作相关的 WebSocket 消息
 * 优化版：使用 Virtual Threads 并行广播
 *
 * @author Actionow
 */
@Slf4j
@Component
public class CollaborationHub extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final SessionManager sessionManager;
    private final PresenceManager presenceManager;
    private final EntityCollaborationManager entityCollabManager;

    // Virtual Thread Executor for parallel broadcasting
    private final ExecutorService virtualThreadExecutor;

    public CollaborationHub(ObjectMapper objectMapper,
                            SessionManager sessionManager,
                            PresenceManager presenceManager,
                            EntityCollaborationManager entityCollabManager) {
        this.objectMapper = objectMapper;
        this.sessionManager = sessionManager;
        this.presenceManager = presenceManager;
        this.entityCollabManager = entityCollabManager;
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @PreDestroy
    public void shutdown() {
        virtualThreadExecutor.shutdown();
        log.info("Virtual thread executor shutdown");
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = (String) session.getAttributes().get(WebSocketAuthInterceptor.ATTR_USER_ID);
        String nickname = (String) session.getAttributes().get(WebSocketAuthInterceptor.ATTR_NICKNAME);
        String avatar = (String) session.getAttributes().get(WebSocketAuthInterceptor.ATTR_AVATAR);
        String workspaceId = (String) session.getAttributes().get(WebSocketAuthInterceptor.ATTR_WORKSPACE_ID);

        if (userId == null) {
            log.warn("Connection without userId, closing: {}", session.getId());
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        // 注册会话
        sessionManager.registerSession(session, userId, nickname, avatar, workspaceId);

        // 更新在线状态
        if (workspaceId != null) {
            presenceManager.userOnline(workspaceId, userId, nickname, avatar);
        }

        // 发送连接成功消息
        send(session, OutboundMessage.of(CollabConstants.MessageType.CONNECTED, Map.of(
                "sessionId", session.getId(),
                "userId", userId,
                "workspaceId", workspaceId != null ? workspaceId : ""
        )));

        log.info("User connected: userId={}, sessionId={}, workspaceId={}", userId, session.getId(), workspaceId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        SessionManager.SessionContext context = sessionManager.getContext(session);
        if (context == null) {
            sessionManager.unregisterSession(session);
            return;
        }

        String userId = context.getUserId();
        String workspaceId = context.getWorkspaceId();
        String scriptId = context.getScriptId();

        // 如果在剧本中，广播用户离开
        if (scriptId != null) {
            // 清理实体协作状态
            if (context.getFocusedEntityType() != null) {
                entityCollabManager.removeViewer(
                        context.getFocusedEntityType(),
                        context.getFocusedEntityId(),
                        userId
                );
                entityCollabManager.releaseEditLock(
                        context.getFocusedEntityType(),
                        context.getFocusedEntityId(),
                        userId
                );

                // 广播实体协作状态更新
                broadcastEntityCollaboration(scriptId, context.getFocusedEntityType(), context.getFocusedEntityId(), null);
            }

            // 从 Redis 移除剧本用户
            presenceManager.leaveScript(workspaceId, userId, scriptId);

            // 广播用户离开
            broadcastToScript(scriptId, session, OutboundMessage.of(
                    CollabConstants.MessageType.USER_LEFT,
                    UserLeftEvent.builder()
                            .scriptId(scriptId)
                            .userId(userId)
                            .nickname(context.getNickname())
                            .build()
            ));
        }

        // 注销会话
        sessionManager.unregisterSession(session);

        // 检查是否还有其他会话，没有则标记离线
        if (workspaceId != null && !sessionManager.hasOtherSessions(userId, session.getId())) {
            presenceManager.userOffline(workspaceId, userId);
        }

        log.info("User disconnected: userId={}, sessionId={}, status={}", userId, session.getId(), status);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            InboundMessage msg = objectMapper.readValue(message.getPayload(), InboundMessage.class);
            String type = msg.getType();

            log.debug("Received message: type={}, sessionId={}", type, session.getId());

            switch (type) {
                case CollabConstants.MessageType.PING, CollabConstants.MessageType.HEARTBEAT -> handlePing(session);
                case CollabConstants.MessageType.ENTER_SCRIPT -> handleEnterScript(session, msg);
                case CollabConstants.MessageType.LEAVE_SCRIPT -> handleLeaveScript(session);
                case CollabConstants.MessageType.SWITCH_TAB -> handleSwitchTab(session, msg);
                case CollabConstants.MessageType.FOCUS_ENTITY -> handleFocusEntity(session, msg);
                case CollabConstants.MessageType.BLUR_ENTITY -> handleBlurEntity(session, msg);
                case CollabConstants.MessageType.START_EDITING -> handleStartEditing(session, msg);
                case CollabConstants.MessageType.STOP_EDITING -> handleStopEditing(session, msg);
                default -> log.debug("Unknown message type: {}", type);
            }
        } catch (Exception e) {
            log.error("Error handling message: {}", message.getPayload(), e);
            send(session, OutboundMessage.of(CollabConstants.MessageType.ERROR,
                    Map.of("message", "消息处理失败: " + e.getMessage())));
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error for session {}: {}", session.getId(), exception.getMessage());
    }

    // ==================== 消息处理方法 ====================

    private void handlePing(WebSocketSession session) {
        SessionManager.SessionContext ctx = sessionManager.getContext(session);
        if (ctx != null && ctx.getWorkspaceId() != null) {
            presenceManager.heartbeat(ctx.getWorkspaceId(), ctx.getUserId());
        }
        send(session, OutboundMessage.of(CollabConstants.MessageType.PONG, null));
    }

    private void handleEnterScript(WebSocketSession session, InboundMessage msg) {
        SessionManager.SessionContext ctx = sessionManager.getContext(session);
        if (ctx == null) return;

        String scriptId = msg.getScriptId();
        String tab = msg.getTab() != null ? msg.getTab() : CollabConstants.Tab.DETAIL;

        // 如果之前在其他剧本，先离开
        if (ctx.getScriptId() != null && !ctx.getScriptId().equals(scriptId)) {
            handleLeaveScriptInternal(session, ctx);
        }

        // 更新会话状态
        sessionManager.enterScript(session, scriptId, tab);

        // 更新 Redis
        presenceManager.enterScript(ctx.getWorkspaceId(), ctx.getUserId(), scriptId);

        // 获取当前剧本协作状态
        List<UserLocation> users = sessionManager.getScriptUserLocations(scriptId);
        ScriptCollaboration collab = ScriptCollaboration.builder()
                .scriptId(scriptId)
                .totalUsers(users.size())
                .users(users)
                .tabUserCounts(countUsersByTab(users))
                .build();

        // 发送给当前用户
        send(session, OutboundMessage.of(CollabConstants.MessageType.SCRIPT_COLLABORATION, collab));

        // 广播给其他用户
        UserLocation userLocation = UserLocation.builder()
                .userId(ctx.getUserId())
                .nickname(ctx.getNickname())
                .avatar(ctx.getAvatar())
                .page(CollabConstants.Page.SCRIPT_DETAIL)
                .scriptId(scriptId)
                .tab(tab)
                .collabStatus(CollabConstants.CollabStatus.VIEWING)
                .build();

        broadcastToScript(scriptId, session, OutboundMessage.of(
                CollabConstants.MessageType.USER_JOINED,
                UserJoinedEvent.builder().scriptId(scriptId).user(userLocation).build()
        ));

        log.debug("User {} entered script {}, tab {}", ctx.getUserId(), scriptId, tab);
    }

    private void handleLeaveScript(WebSocketSession session) {
        SessionManager.SessionContext ctx = sessionManager.getContext(session);
        if (ctx == null || ctx.getScriptId() == null) return;

        handleLeaveScriptInternal(session, ctx);
    }

    private void handleLeaveScriptInternal(WebSocketSession session, SessionManager.SessionContext ctx) {
        String scriptId = ctx.getScriptId();

        // 清理实体协作状态
        if (ctx.getFocusedEntityType() != null) {
            entityCollabManager.removeViewer(ctx.getFocusedEntityType(), ctx.getFocusedEntityId(), ctx.getUserId());
            entityCollabManager.releaseEditLock(ctx.getFocusedEntityType(), ctx.getFocusedEntityId(), ctx.getUserId());
            broadcastEntityCollaboration(scriptId, ctx.getFocusedEntityType(), ctx.getFocusedEntityId(), null);
        }

        // 更新 Redis
        presenceManager.leaveScript(ctx.getWorkspaceId(), ctx.getUserId(), scriptId);

        // 广播用户离开
        broadcastToScript(scriptId, session, OutboundMessage.of(
                CollabConstants.MessageType.USER_LEFT,
                UserLeftEvent.builder()
                        .scriptId(scriptId)
                        .userId(ctx.getUserId())
                        .nickname(ctx.getNickname())
                        .build()
        ));

        // 更新会话状态
        sessionManager.leaveScript(session);

        log.debug("User {} left script {}", ctx.getUserId(), scriptId);
    }

    private void handleSwitchTab(WebSocketSession session, InboundMessage msg) {
        SessionManager.SessionContext ctx = sessionManager.getContext(session);
        if (ctx == null || ctx.getScriptId() == null) return;

        String previousTab = ctx.getTab();
        String previousEntityType = ctx.getFocusedEntityType();
        String previousEntityId = ctx.getFocusedEntityId();

        // 清理之前的实体聚焦
        if (previousEntityType != null) {
            entityCollabManager.removeViewer(previousEntityType, previousEntityId, ctx.getUserId());
            entityCollabManager.releaseEditLock(previousEntityType, previousEntityId, ctx.getUserId());
            broadcastEntityCollaboration(ctx.getScriptId(), previousEntityType, previousEntityId, null);
        }

        // 更新 Tab
        sessionManager.switchTab(session, msg.getTab());

        // 广播位置变化
        UserLocation location = UserLocation.builder()
                .userId(ctx.getUserId())
                .nickname(ctx.getNickname())
                .avatar(ctx.getAvatar())
                .page(CollabConstants.Page.SCRIPT_DETAIL)
                .scriptId(ctx.getScriptId())
                .tab(msg.getTab())
                .collabStatus(CollabConstants.CollabStatus.VIEWING)
                .build();

        broadcastToScript(ctx.getScriptId(), session, OutboundMessage.of(
                CollabConstants.MessageType.USER_LOCATION_CHANGED,
                UserLocationChangedEvent.builder()
                        .scriptId(ctx.getScriptId())
                        .user(location)
                        .previousTab(previousTab)
                        .previousEntityType(previousEntityType)
                        .previousEntityId(previousEntityId)
                        .build()
        ));

        log.debug("User {} switched tab from {} to {}", ctx.getUserId(), previousTab, msg.getTab());
    }

    private void handleFocusEntity(WebSocketSession session, InboundMessage msg) {
        SessionManager.SessionContext ctx = sessionManager.getContext(session);
        if (ctx == null || ctx.getScriptId() == null) return;

        // 清理之前的聚焦
        if (ctx.getFocusedEntityType() != null) {
            entityCollabManager.removeViewer(ctx.getFocusedEntityType(), ctx.getFocusedEntityId(), ctx.getUserId());
        }

        // 添加新的聚焦
        Collaborator collaborator = entityCollabManager.createCollaborator(
                ctx.getUserId(), ctx.getNickname(), ctx.getAvatar(), CollabConstants.CollabStatus.VIEWING
        );

        entityCollabManager.addViewer(msg.getEntityType(), msg.getEntityId(), collaborator);
        sessionManager.focusEntity(session, msg.getEntityType(), msg.getEntityId());

        // 广播实体协作状态
        broadcastEntityCollaboration(ctx.getScriptId(), msg.getEntityType(), msg.getEntityId(), null);

        log.debug("User {} focused entity {}:{}", ctx.getUserId(), msg.getEntityType(), msg.getEntityId());
    }

    private void handleBlurEntity(WebSocketSession session, InboundMessage msg) {
        SessionManager.SessionContext ctx = sessionManager.getContext(session);
        if (ctx == null) return;

        entityCollabManager.removeViewer(msg.getEntityType(), msg.getEntityId(), ctx.getUserId());
        sessionManager.focusEntity(session, null, null);

        // 广播实体协作状态
        if (ctx.getScriptId() != null) {
            broadcastEntityCollaboration(ctx.getScriptId(), msg.getEntityType(), msg.getEntityId(), null);
        }

        log.debug("User {} blurred entity {}:{}", ctx.getUserId(), msg.getEntityType(), msg.getEntityId());
    }

    private void handleStartEditing(WebSocketSession session, InboundMessage msg) {
        SessionManager.SessionContext ctx = sessionManager.getContext(session);
        if (ctx == null || ctx.getScriptId() == null) return;

        Collaborator editor = entityCollabManager.createCollaborator(
                ctx.getUserId(), ctx.getNickname(), ctx.getAvatar(), CollabConstants.CollabStatus.EDITING
        );

        boolean success = entityCollabManager.tryAcquireEditLock(msg.getEntityType(), msg.getEntityId(), editor);

        if (success) {
            sessionManager.setCollabStatus(session, CollabConstants.CollabStatus.EDITING);

            // 广播编辑状态
            broadcastEntityCollaboration(ctx.getScriptId(), msg.getEntityType(), msg.getEntityId(), null);

            log.debug("User {} started editing entity {}:{}", ctx.getUserId(), msg.getEntityType(), msg.getEntityId());
        } else {
            // 发送锁定失败
            Collaborator lockedBy = entityCollabManager.getCurrentEditor(msg.getEntityType(), msg.getEntityId());
            send(session, OutboundMessage.of(CollabConstants.MessageType.EDITING_LOCKED, Map.of(
                    "entityType", msg.getEntityType(),
                    "entityId", msg.getEntityId(),
                    "lockedBy", lockedBy != null ? lockedBy : Map.of()
            )));

            log.debug("User {} failed to start editing entity {}:{} (locked)", ctx.getUserId(), msg.getEntityType(), msg.getEntityId());
        }
    }

    private void handleStopEditing(WebSocketSession session, InboundMessage msg) {
        SessionManager.SessionContext ctx = sessionManager.getContext(session);
        if (ctx == null || ctx.getScriptId() == null) return;

        entityCollabManager.releaseEditLock(msg.getEntityType(), msg.getEntityId(), ctx.getUserId());
        sessionManager.setCollabStatus(session, CollabConstants.CollabStatus.VIEWING);

        // 广播解锁状态
        broadcastEntityCollaboration(ctx.getScriptId(), msg.getEntityType(), msg.getEntityId(), null);

        log.debug("User {} stopped editing entity {}:{}", ctx.getUserId(), msg.getEntityType(), msg.getEntityId());
    }

    // ==================== 广播方法 ====================

    /**
     * 广播实体协作状态
     */
    private void broadcastEntityCollaboration(String scriptId, String entityType, String entityId, WebSocketSession excludeSession) {
        EntityCollaboration collab = entityCollabManager.getEntityCollaboration(entityType, entityId);
        OutboundMessage message = OutboundMessage.of(
                CollabConstants.MessageType.ENTITY_COLLABORATION,
                EntityCollaborationEvent.builder()
                        .scriptId(scriptId)
                        .entityType(entityType)
                        .entityId(entityId)
                        .viewers(collab.getViewers())
                        .editor(collab.getEditor())
                        .build()
        );
        broadcastToScript(scriptId, excludeSession, message);
    }

    /**
     * 发送消息给剧本内所有用户（排除指定会话）
     * 优化版：使用 Virtual Threads 并行发送
     */
    private void broadcastToScript(String scriptId, WebSocketSession excludeSession, OutboundMessage message) {
        Set<WebSocketSession> sessions = sessionManager.getScriptSessions(scriptId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        // 预序列化一次，所有会话共享
        String json;
        try {
            json = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize broadcast message", e);
            return;
        }

        // 收集需要发送的会话
        List<WebSocketSession> targetSessions = new ArrayList<>();
        for (WebSocketSession session : sessions) {
            if (session.isOpen() && (excludeSession == null || !session.getId().equals(excludeSession.getId()))) {
                targetSessions.add(session);
            }
        }

        if (targetSessions.isEmpty()) {
            return;
        }

        // Virtual Threads 并行发送
        List<CompletableFuture<Void>> futures = targetSessions.stream()
                .map(session -> CompletableFuture.runAsync(
                        () -> sendRaw(session, json),
                        virtualThreadExecutor
                ))
                .toList();

        // 非阻塞：不等待完成，让 Virtual Threads 后台处理
        // 如需同步等待可取消注释：CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        log.debug("Broadcasting to {} sessions in script {} (async)", targetSessions.size(), scriptId);
    }

    /**
     * 发送消息给剧本内所有用户
     */
    public void sendToScript(String scriptId, OutboundMessage message) {
        broadcastToScript(scriptId, null, message);
    }

    /**
     * 发送任意格式消息给剧本内所有用户
     * 用于统一格式的通知消息
     */
    public void broadcastToScriptRaw(String scriptId, Object message) {
        Set<WebSocketSession> sessions = sessionManager.getScriptSessions(scriptId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize message for script {}", scriptId, e);
            return;
        }

        List<CompletableFuture<Void>> futures = sessions.stream()
                .filter(WebSocketSession::isOpen)
                .map(session -> CompletableFuture.runAsync(
                        () -> sendRaw(session, json),
                        virtualThreadExecutor
                ))
                .toList();

        log.debug("Broadcasting raw message to {} sessions in script {} (async)", futures.size(), scriptId);
    }

    /**
     * 发送统一格式消息给工作空间内所有用户
     * 用于跨服务通知推送
     */
    public void sendToWorkspace(String workspaceId, Object message) {
        Set<WebSocketSession> sessions = sessionManager.getWorkspaceSessions(workspaceId);
        if (sessions == null || sessions.isEmpty()) {
            log.debug("No sessions found for workspace: {}", workspaceId);
            return;
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize message for workspace {}", workspaceId, e);
            return;
        }

        List<CompletableFuture<Void>> futures = sessions.stream()
                .filter(WebSocketSession::isOpen)
                .map(session -> CompletableFuture.runAsync(
                        () -> sendRaw(session, json),
                        virtualThreadExecutor
                ))
                .toList();

        log.debug("Broadcasting to {} sessions in workspace {} (async)", futures.size(), workspaceId);
    }

    /**
     * 发送统一格式消息给指定用户
     * 使用 Virtual Threads 并行发送，避免多 Tab 场景下串行阻塞
     */
    public void sendToUser(String workspaceId, String userId, Object message) {
        Set<WebSocketSession> sessions = sessionManager.getUserSessions(userId);
        if (sessions == null || sessions.isEmpty()) {
            log.debug("No active session for user {} in workspace {}", userId, workspaceId);
            return;
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize message for user {}", userId, e);
            return;
        }

        List<WebSocketSession> targetSessions = sessions.stream()
                .filter(WebSocketSession::isOpen)
                .filter(s -> {
                    SessionManager.SessionContext ctx = sessionManager.getContext(s);
                    return ctx != null && workspaceId.equals(ctx.getWorkspaceId());
                })
                .toList();

        if (targetSessions.size() <= 1) {
            // 单 session 直接发送，无需启动虚拟线程
            targetSessions.forEach(s -> sendRaw(s, json));
        } else {
            targetSessions.forEach(s ->
                    CompletableFuture.runAsync(() -> sendRaw(s, json), virtualThreadExecutor));
        }
    }

    /**
     * 发送消息给多个用户（定向通知推送）
     */
    public void sendToUsers(String workspaceId, List<String> userIds, Object message) {
        if (userIds == null || userIds.isEmpty()) return;

        String json;
        try {
            json = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize message for users", e);
            return;
        }

        for (String userId : userIds) {
            Set<WebSocketSession> sessions = sessionManager.getUserSessions(userId);
            if (sessions == null) continue;
            for (WebSocketSession session : sessions) {
                if (!session.isOpen()) continue;
                SessionManager.SessionContext ctx = sessionManager.getContext(session);
                if (ctx != null && workspaceId.equals(ctx.getWorkspaceId())) {
                    CompletableFuture.runAsync(() -> sendRaw(session, json), virtualThreadExecutor);
                }
            }
        }

        log.debug("Sent message to {} users in workspace {} (async)", userIds.size(), workspaceId);
    }

    /**
     * 发送原始 JSON 字符串给指定会话（避免重复序列化）
     * 线程安全：同步访问 WebSocket session
     */
    private void sendRaw(WebSocketSession session, String json) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            // WebSocket sendMessage 不是线程安全的，需要同步
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                }
            }
        } catch (IOException e) {
            log.debug("Failed to send message to session {}: {}", session.getId(), e.getMessage());
        }
    }

    /**
     * 发送消息给指定会话
     * 线程安全：同步访问 WebSocket session
     */
    private void send(WebSocketSession session, OutboundMessage message) {
        if (session == null || !session.isOpen()) return;
        try {
            String json = objectMapper.writeValueAsString(message);
            // WebSocket sendMessage 不是线程安全的，需要同步
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                }
            }
        } catch (IOException e) {
            log.error("Failed to send message to session {}", session.getId(), e);
        }
    }

    // ==================== 工具方法 ====================

    private Map<String, Integer> countUsersByTab(List<UserLocation> users) {
        return users.stream()
                .filter(u -> u.getTab() != null)
                .collect(Collectors.groupingBy(UserLocation::getTab, Collectors.summingInt(u -> 1)));
    }
}
