package com.actionow.common.web.sse;

import lombok.Builder;
import lombok.Data;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * SSE 连接封装
 * 包装 SseEmitter 并添加元数据
 *
 * @author Actionow
 */
@Data
@Builder
public class SseConnection {

    /**
     * 连接唯一标识
     */
    private String connectionId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 会话ID（可选，用于Agent场景）
     */
    private String sessionId;

    /**
     * 工作空间ID
     */
    private String workspaceId;

    /**
     * SSE Emitter 实例
     */
    private SseEmitter emitter;

    /**
     * 连接创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 最后活跃时间
     */
    private LocalDateTime lastActiveAt;

    /**
     * 额外元数据
     */
    private Map<String, Object> metadata;

    /**
     * 更新最后活跃时间
     */
    public void touch() {
        this.lastActiveAt = LocalDateTime.now();
    }
}
