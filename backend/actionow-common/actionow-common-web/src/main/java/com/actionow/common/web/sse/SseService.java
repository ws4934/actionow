package com.actionow.common.web.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 统一 SSE 服务
 * 提供标准的 SSE 连接管理、心跳机制、事件发送功能
 *
 * 使用方式：
 * 1. 注入 SseService
 * 2. 调用 createConnection() 创建连接
 * 3. 调用 sendEvent() 发送事件
 * 4. 连接完成后调用 complete() 或 completeWithError()
 *
 * @author Actionow
 */
@Slf4j
public class SseService {

    /**
     * 默认超时时间：5分钟
     */
    private static final long DEFAULT_TIMEOUT = 5 * 60 * 1000L;

    /**
     * 心跳间隔：20秒
     */
    private static final long HEARTBEAT_INTERVAL = 20 * 1000L;

    /**
     * 连接存储
     */
    private final Map<String, SseConnection> connections = new ConcurrentHashMap<>();

    /**
     * 超时时间（毫秒）
     */
    private final long timeout;

    public SseService() {
        this(DEFAULT_TIMEOUT);
    }

    public SseService(long timeout) {
        this.timeout = timeout;
    }

    /**
     * 创建 SSE 连接
     *
     * @param connectionId 连接ID（建议使用 sessionId 或 taskId）
     * @param userId       用户ID
     * @param workspaceId  工作空间ID
     * @return SseEmitter 实例
     */
    public SseEmitter createConnection(String connectionId, String userId, String workspaceId) {
        return createConnection(connectionId, userId, workspaceId, null, null);
    }

    /**
     * 创建 SSE 连接（完整参数）
     *
     * @param connectionId 连接ID
     * @param userId       用户ID
     * @param workspaceId  工作空间ID
     * @param sessionId    会话ID（可选）
     * @param metadata     额外元数据（可选）
     * @return SseEmitter 实例
     */
    public SseEmitter createConnection(String connectionId, String userId, String workspaceId,
                                        String sessionId, Map<String, Object> metadata) {
        // 如果已存在连接，先关闭旧连接
        SseConnection existing = connections.get(connectionId);
        if (existing != null) {
            log.warn("Connection already exists, closing old connection: {}", connectionId);
            try {
                existing.getEmitter().complete();
            } catch (Exception e) {
                // ignore
            }
            connections.remove(connectionId);
        }

        // 创建新的 SseEmitter
        SseEmitter emitter = new SseEmitter(timeout);

        // 构建连接对象
        SseConnection connection = SseConnection.builder()
                .connectionId(connectionId)
                .userId(userId)
                .workspaceId(workspaceId)
                .sessionId(sessionId)
                .emitter(emitter)
                .createdAt(LocalDateTime.now())
                .lastActiveAt(LocalDateTime.now())
                .metadata(metadata)
                .build();

        // 注册生命周期回调
        emitter.onCompletion(() -> {
            log.debug("SSE connection completed: {}", connectionId);
            connections.remove(connectionId);
        });

        emitter.onTimeout(() -> {
            log.warn("SSE connection timeout: {}", connectionId);
            connections.remove(connectionId);
            emitter.complete();
        });

        emitter.onError(e -> {
            if (!isClientDisconnectError(e)) {
                log.error("SSE connection error: {}", connectionId, e);
            }
            connections.remove(connectionId);
        });

        // 存储连接
        connections.put(connectionId, connection);

        // 发送连接成功事件
        try {
            emitter.send(SseEmitter.event()
                    .id(String.valueOf(System.currentTimeMillis()))
                    .name("connect")
                    .data(Map.of("status", "connected"), MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            log.warn("Failed to send connect event: {}", connectionId);
        }

        log.info("SSE connection created: connectionId={}, userId={}, workspaceId={}",
                connectionId, userId, workspaceId);

        return emitter;
    }

    /**
     * 发送 SSE 事件
     *
     * @param connectionId 连接ID
     * @param eventName    事件名称
     * @param data         事件数据（对象会自动序列化为 JSON）
     * @return 是否发送成功
     */
    public boolean sendEvent(String connectionId, String eventName, Object data) {
        SseConnection connection = connections.get(connectionId);
        if (connection == null) {
            log.debug("Connection not found (client may have disconnected): {}", connectionId);
            return false;
        }

        try {
            SseEmitter.SseEventBuilder eventBuilder = SseEmitter.event()
                    .id(String.valueOf(System.currentTimeMillis()))
                    .name(eventName);

            if (data instanceof String) {
                // 已经是字符串（可能是预序列化的 JSON），直接发送，不指定 MediaType 避免二次序列化
                eventBuilder.data(data);
            } else {
                // 对象类型，让 SseEmitter 通过 HttpMessageConverter 序列化
                eventBuilder.data(data, MediaType.APPLICATION_JSON);
            }

            connection.getEmitter().send(eventBuilder);

            connection.touch();
            log.debug("SSE event sent: connectionId={}, event={}", connectionId, eventName);
            return true;
        } catch (IOException e) {
            if (!isClientDisconnectError(e)) {
                log.warn("Failed to send SSE event: connectionId={}, event={}, error={}",
                        connectionId, eventName, e.getMessage());
            }
            connections.remove(connectionId);
            return false;
        }
    }

    /**
     * 发送 SSE 事件（使用回调处理发送失败）
     *
     * @param connectionId 连接ID
     * @param eventName    事件名称
     * @param data         事件数据
     * @param onError      错误回调
     */
    public void sendEvent(String connectionId, String eventName, Object data, Consumer<Exception> onError) {
        if (!sendEvent(connectionId, eventName, data)) {
            if (onError != null) {
                onError.accept(new IOException("Failed to send SSE event"));
            }
        }
    }

    /**
     * 完成连接（正常关闭）
     *
     * @param connectionId 连接ID
     */
    public void complete(String connectionId) {
        SseConnection connection = connections.get(connectionId);
        if (connection != null) {
            try {
                // 发送完成事件
                connection.getEmitter().send(SseEmitter.event()
                        .id(String.valueOf(System.currentTimeMillis()))
                        .name("done")
                        .data(Map.of("status", "completed"), MediaType.APPLICATION_JSON));
                connection.getEmitter().complete();
            } catch (IOException e) {
                // ignore
            }
            connections.remove(connectionId);
            log.info("SSE connection completed: {}", connectionId);
        }
    }

    /**
     * 完成连接（错误关闭）
     *
     * @param connectionId 连接ID
     * @param error        错误信息
     */
    public void completeWithError(String connectionId, Throwable error) {
        SseConnection connection = connections.get(connectionId);
        if (connection != null) {
            try {
                // 发送错误事件，直接传对象让 SseEmitter 序列化
                Map<String, String> errorData = Map.of(
                        "status", "error",
                        "errorMessage", error.getMessage() != null ? error.getMessage() : "Unknown error"
                );
                connection.getEmitter().send(SseEmitter.event()
                        .id(String.valueOf(System.currentTimeMillis()))
                        .name("error")
                        .data(errorData, MediaType.APPLICATION_JSON));
            } catch (IOException e) {
                // ignore
            }
            // Use complete() instead of completeWithError() to prevent Spring MVC from
            // dispatching an async error that it cannot write as JSON to an SSE response
            try {
                connection.getEmitter().complete();
            } catch (Exception e) {
                // ignore
            }
            connections.remove(connectionId);
            log.info("SSE connection completed with error: {}, error={}", connectionId, error.getMessage());
        }
    }

    /**
     * 广播事件给所有连接
     *
     * @param eventName 事件名称
     * @param data      事件数据
     */
    public void broadcast(String eventName, Object data) {
        connections.forEach((connectionId, connection) -> {
            sendEvent(connectionId, eventName, data);
        });
    }

    /**
     * 广播事件给指定工作空间的所有连接
     *
     * @param workspaceId 工作空间ID
     * @param eventName   事件名称
     * @param data        事件数据
     */
    public void broadcastToWorkspace(String workspaceId, String eventName, Object data) {
        connections.values().stream()
                .filter(conn -> workspaceId.equals(conn.getWorkspaceId()))
                .forEach(conn -> sendEvent(conn.getConnectionId(), eventName, data));
    }

    /**
     * 广播事件给指定用户的所有连接
     *
     * @param userId    用户ID
     * @param eventName 事件名称
     * @param data      事件数据
     */
    public void broadcastToUser(String userId, String eventName, Object data) {
        connections.values().stream()
                .filter(conn -> userId.equals(conn.getUserId()))
                .forEach(conn -> sendEvent(conn.getConnectionId(), eventName, data));
    }

    /**
     * 发送心跳（定时任务调用）
     * 每20秒发送一次心跳，保持连接活跃
     */
    @Scheduled(fixedRate = HEARTBEAT_INTERVAL)
    public void sendHeartbeat() {
        if (connections.isEmpty()) {
            return;
        }

        log.debug("Sending heartbeat to {} connections", connections.size());
        long now = System.currentTimeMillis();

        connections.forEach((connectionId, connection) -> {
            try {
                connection.getEmitter().send(SseEmitter.event()
                        .id(String.valueOf(now))
                        .name("heartbeat")
                        .data(Map.of("timestamp", now), MediaType.APPLICATION_JSON));
                connection.touch();
            } catch (IOException e) {
                if (!isClientDisconnectError(e)) {
                    log.warn("Heartbeat failed for connection: {}", connectionId);
                }
                connections.remove(connectionId);
            }
        });
    }

    /**
     * 获取活跃连接数
     *
     * @return 连接数
     */
    public int getActiveConnectionCount() {
        return connections.size();
    }

    /**
     * 获取指定工作空间的连接数
     *
     * @param workspaceId 工作空间ID
     * @return 连接数
     */
    public int getConnectionCountByWorkspace(String workspaceId) {
        return (int) connections.values().stream()
                .filter(conn -> workspaceId.equals(conn.getWorkspaceId()))
                .count();
    }

    /**
     * 检查连接是否存在
     *
     * @param connectionId 连接ID
     * @return 是否存在
     */
    public boolean hasConnection(String connectionId) {
        return connections.containsKey(connectionId);
    }

    /**
     * 获取连接信息
     *
     * @param connectionId 连接ID
     * @return 连接信息，不存在返回null
     */
    public SseConnection getConnection(String connectionId) {
        return connections.get(connectionId);
    }

    /**
     * 判断是否为客户端断开连接错误
     */
    private boolean isClientDisconnectError(Throwable e) {
        String message = e.getMessage();
        if (message == null) {
            message = "";
        }
        return message.contains("Broken pipe")
                || message.contains("Connection reset")
                || message.contains("Client aborted")
                || message.contains("AsyncRequestTimeoutException")
                || message.contains("EOFException")
                || e instanceof java.io.IOException
                || (e.getCause() != null && isClientDisconnectError(e.getCause()));
    }
}
