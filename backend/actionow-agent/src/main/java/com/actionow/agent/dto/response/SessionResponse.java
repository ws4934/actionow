package com.actionow.agent.dto.response;

import com.actionow.agent.entity.AgentSessionEntity;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 会话响应
 *
 * @author Actionow
 */
@Data
@Builder
public class SessionResponse {

    /**
     * 会话 ID
     */
    private String id;

    /**
     * Agent 类型
     */
    private String agentType;

    /**
     * 用户 ID
     */
    private String userId;

    /**
     * 工作空间 ID
     */
    private String workspaceId;

    /**
     * 关联的剧本 ID（便捷字段，从 scopeContext 中提取）
     */
    private String scriptId;

    /**
     * 作用域上下文
     */
    private Map<String, Object> scopeContext;

    /**
     * 会话状态
     */
    private String status;

    /**
     * 会话标题
     */
    private String title;

    /**
     * 消息数量
     */
    private Integer messageCount;

    /**
     * Token 使用量
     */
    private Long totalTokens;

    /**
     * 扩展属性
     */
    private Map<String, Object> extras;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 最后活跃时间
     */
    private LocalDateTime lastActiveAt;

    /**
     * 归档时间（仅归档会话有值）
     */
    private LocalDateTime archivedAt;

    /**
     * 是否正在生成中（尽力而为的信号，服务重启后丢失）
     * 真正的可靠性由消息 status 字段保证
     */
    private Boolean generating;

    /**
     * 从实体转换
     */
    public static SessionResponse from(AgentSessionEntity entity) {
        if (entity == null) {
            return null;
        }
        return SessionResponse.builder()
                .id(entity.getId())
                .agentType(entity.getAgentType())
                .userId(entity.getUserId())
                .workspaceId(entity.getWorkspaceId())
                .scriptId(entity.getScriptId())
                .scopeContext(entity.getScopeContext())
                .status(entity.getStatus())
                .title(entity.getTitle())
                .messageCount(entity.getMessageCount())
                .totalTokens(entity.getTotalTokens())
                .extras(entity.getExtras())
                .createdAt(entity.getCreatedAt())
                .lastActiveAt(entity.getLastActiveAt())
                .archivedAt(entity.getArchivedAt())
                .build();
    }
}
