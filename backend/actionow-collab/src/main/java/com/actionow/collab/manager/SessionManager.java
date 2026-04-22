package com.actionow.collab.manager;

import com.actionow.collab.constant.CollabConstants;
import com.actionow.collab.dto.UserLocation;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * WebSocket 会话管理器
 * 管理用户会话、工作空间会话、剧本会话的映射关系
 *
 * @author Actionow
 */
@Slf4j
@Component
public class SessionManager {

    /**
     * userId -> Set<WebSocketSession> (支持多设备/多Tab)
     */
    private final Map<String, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    /**
     * workspaceId -> Set<WebSocketSession>
     */
    private final Map<String, Set<WebSocketSession>> workspaceSessions = new ConcurrentHashMap<>();

    /**
     * scriptId -> Set<WebSocketSession>
     */
    private final Map<String, Set<WebSocketSession>> scriptSessions = new ConcurrentHashMap<>();

    /**
     * sessionId -> SessionContext
     */
    private final Map<String, SessionContext> sessionContexts = new ConcurrentHashMap<>();

    /**
     * 会话上下文
     */
    @Data
    @Builder
    public static class SessionContext {
        private String sessionId;
        private String userId;
        private String nickname;
        private String avatar;
        private String workspaceId;
        private String scriptId;
        private String tab;
        private String focusedEntityType;
        private String focusedEntityId;
        private String collabStatus;
    }

    /**
     * 注册会话
     */
    public void registerSession(WebSocketSession session, String userId, String nickname,
                                String avatar, String workspaceId) {
        String sessionId = session.getId();

        SessionContext context = SessionContext.builder()
                .sessionId(sessionId)
                .userId(userId)
                .nickname(nickname)
                .avatar(avatar)
                .workspaceId(workspaceId)
                .build();
        sessionContexts.put(sessionId, context);

        userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);

        if (workspaceId != null) {
            workspaceSessions.computeIfAbsent(workspaceId, k -> ConcurrentHashMap.newKeySet()).add(session);
        }

        log.info("Session registered: sessionId={}, userId={}, workspaceId={}",
                sessionId, userId, workspaceId);
    }

    /**
     * 注销会话
     */
    public SessionContext unregisterSession(WebSocketSession session) {
        String sessionId = session.getId();
        SessionContext context = sessionContexts.remove(sessionId);

        if (context == null) return null;

        removeFromMap(userSessions, context.getUserId(), session);

        if (context.getWorkspaceId() != null) {
            removeFromMap(workspaceSessions, context.getWorkspaceId(), session);
        }

        if (context.getScriptId() != null) {
            removeFromMap(scriptSessions, context.getScriptId(), session);
        }

        log.info("Session unregistered: sessionId={}, userId={}", sessionId, context.getUserId());
        return context;
    }

    /**
     * 进入剧本
     */
    public void enterScript(WebSocketSession session, String scriptId, String tab) {
        SessionContext context = sessionContexts.get(session.getId());
        if (context == null) return;

        if (context.getScriptId() != null && !context.getScriptId().equals(scriptId)) {
            removeFromMap(scriptSessions, context.getScriptId(), session);
        }

        context.setScriptId(scriptId);
        context.setTab(tab != null ? tab : CollabConstants.Tab.DETAIL);
        context.setFocusedEntityType(null);
        context.setFocusedEntityId(null);
        context.setCollabStatus(CollabConstants.CollabStatus.VIEWING);

        scriptSessions.computeIfAbsent(scriptId, k -> ConcurrentHashMap.newKeySet()).add(session);

        log.debug("User {} entered script {}, tab {}", context.getUserId(), scriptId, tab);
    }

    /**
     * 离开剧本
     */
    public void leaveScript(WebSocketSession session) {
        SessionContext context = sessionContexts.get(session.getId());
        if (context == null || context.getScriptId() == null) return;

        String scriptId = context.getScriptId();
        removeFromMap(scriptSessions, scriptId, session);

        context.setScriptId(null);
        context.setTab(null);
        context.setFocusedEntityType(null);
        context.setFocusedEntityId(null);
        context.setCollabStatus(null);

        log.debug("User {} left script {}", context.getUserId(), scriptId);
    }

    /**
     * 切换Tab
     */
    public void switchTab(WebSocketSession session, String tab) {
        SessionContext context = sessionContexts.get(session.getId());
        if (context != null) {
            context.setTab(tab);
            context.setFocusedEntityType(null);
            context.setFocusedEntityId(null);
            context.setCollabStatus(CollabConstants.CollabStatus.VIEWING);
        }
    }

    /**
     * 聚焦实体
     */
    public void focusEntity(WebSocketSession session, String entityType, String entityId) {
        SessionContext context = sessionContexts.get(session.getId());
        if (context != null) {
            context.setFocusedEntityType(entityType);
            context.setFocusedEntityId(entityId);
        }
    }

    /**
     * 设置协作状态
     */
    public void setCollabStatus(WebSocketSession session, String status) {
        SessionContext context = sessionContexts.get(session.getId());
        if (context != null) {
            context.setCollabStatus(status);
        }
    }

    /**
     * 获取会话上下文
     */
    public SessionContext getContext(WebSocketSession session) {
        return sessionContexts.get(session.getId());
    }

    /**
     * 获取会话上下文
     */
    public SessionContext getContext(String sessionId) {
        return sessionContexts.get(sessionId);
    }

    /**
     * 获取用户的所有会话
     */
    public Set<WebSocketSession> getUserSessions(String userId) {
        return userSessions.getOrDefault(userId, Set.of());
    }

    /**
     * 获取工作空间内所有会话
     */
    public Set<WebSocketSession> getWorkspaceSessions(String workspaceId) {
        return workspaceSessions.getOrDefault(workspaceId, Set.of());
    }

    /**
     * 获取用户在指定工作空间的会话（返回第一个活跃会话）
     */
    public WebSocketSession getUserSession(String workspaceId, String userId) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return null;
        }
        return sessions.stream()
                .filter(WebSocketSession::isOpen)
                .filter(s -> {
                    SessionContext ctx = sessionContexts.get(s.getId());
                    return ctx != null && workspaceId.equals(ctx.getWorkspaceId());
                })
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取多个用户在指定工作空间的会话
     */
    public Map<String, Set<WebSocketSession>> getSessionsByUserIds(String workspaceId, List<String> userIds) {
        Map<String, Set<WebSocketSession>> result = new HashMap<>();
        for (String userId : userIds) {
            Set<WebSocketSession> sessions = userSessions.get(userId);
            if (sessions == null) continue;
            Set<WebSocketSession> matched = sessions.stream()
                    .filter(WebSocketSession::isOpen)
                    .filter(s -> {
                        SessionContext ctx = sessionContexts.get(s.getId());
                        return ctx != null && workspaceId.equals(ctx.getWorkspaceId());
                    })
                    .collect(Collectors.toSet());
            if (!matched.isEmpty()) {
                result.put(userId, matched);
            }
        }
        return result;
    }

    /**
     * 获取剧本内所有会话
     */
    public Set<WebSocketSession> getScriptSessions(String scriptId) {
        return scriptSessions.getOrDefault(scriptId, Set.of());
    }

    /**
     * 检查用户是否有其他活跃会话
     */
    public boolean hasOtherSessions(String userId, String excludeSessionId) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null || sessions.isEmpty()) return false;
        return sessions.stream().anyMatch(s -> !s.getId().equals(excludeSessionId) && s.isOpen());
    }

    /**
     * 获取剧本内所有用户位置
     */
    public List<UserLocation> getScriptUserLocations(String scriptId) {
        Set<WebSocketSession> sessions = scriptSessions.get(scriptId);
        if (sessions == null || sessions.isEmpty()) {
            return List.of();
        }

        Map<String, SessionContext> userContexts = new HashMap<>();
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) continue;
            SessionContext ctx = sessionContexts.get(session.getId());
            if (ctx != null) {
                userContexts.putIfAbsent(ctx.getUserId(), ctx);
            }
        }

        return userContexts.values().stream()
                .map(ctx -> UserLocation.builder()
                        .userId(ctx.getUserId())
                        .nickname(ctx.getNickname())
                        .avatar(ctx.getAvatar())
                        .page(CollabConstants.Page.SCRIPT_DETAIL)
                        .scriptId(ctx.getScriptId())
                        .tab(ctx.getTab())
                        .focusedEntityType(ctx.getFocusedEntityType())
                        .focusedEntityId(ctx.getFocusedEntityId())
                        .collabStatus(ctx.getCollabStatus())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 批量获取多个剧本的用户位置
     */
    public Map<String, List<UserLocation>> batchGetScriptUserLocations(List<String> scriptIds) {
        Map<String, List<UserLocation>> result = new HashMap<>();
        for (String scriptId : scriptIds) {
            result.put(scriptId, getScriptUserLocations(scriptId));
        }
        return result;
    }

    /**
     * 获取工作空间在线用户数
     */
    public int getWorkspaceOnlineCount(String workspaceId) {
        Set<WebSocketSession> sessions = workspaceSessions.get(workspaceId);
        if (sessions == null) return 0;
        return (int) sessions.stream()
                .filter(WebSocketSession::isOpen)
                .map(s -> sessionContexts.get(s.getId()))
                .filter(Objects::nonNull)
                .map(SessionContext::getUserId)
                .distinct()
                .count();
    }

    /**
     * 获取剧本在线用户数
     */
    public int getScriptOnlineCount(String scriptId) {
        Set<WebSocketSession> sessions = scriptSessions.get(scriptId);
        if (sessions == null) return 0;
        return (int) sessions.stream()
                .filter(WebSocketSession::isOpen)
                .map(s -> sessionContexts.get(s.getId()))
                .filter(Objects::nonNull)
                .map(SessionContext::getUserId)
                .distinct()
                .count();
    }

    private void removeFromMap(Map<String, Set<WebSocketSession>> map, String key, WebSocketSession session) {
        if (key == null) return;
        map.computeIfPresent(key, (k, set) -> {
            set.remove(session);
            return set.isEmpty() ? null : set;
        });
    }
}
