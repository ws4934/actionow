package com.actionow.common.mq.message;

import com.actionow.common.core.id.UuidGenerator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

/**
 * 统一 WebSocket 消息格式
 * 用于前端实时通知
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息类型
     * @see Type
     */
    private String type;

    /**
     * 业务域
     * @see Domain
     */
    private String domain;

    /**
     * 动作
     * @see Action
     */
    private String action;

    /**
     * 实体类型（如 script, episode, storyboard, character 等）
     */
    private String entityType;

    /**
     * 实体ID
     */
    private String entityId;

    /**
     * 工作空间ID
     */
    private String workspaceId;

    /**
     * 剧本ID（可选，用于剧本级别的广播）
     */
    private String scriptId;

    /**
     * 消息数据
     */
    private Map<String, Object> data;

    /**
     * 时间戳（毫秒）
     */
    private Long timestamp;

    /**
     * 事件ID（用于幂等性检查）
     */
    private String eventId;

    /**
     * 消息类型枚举
     */
    public static final class Type {
        // 连接状态
        public static final String CONNECTED = "CONNECTED";
        public static final String DISCONNECTED = "DISCONNECTED";
        public static final String ERROR = "ERROR";

        // 心跳
        public static final String PING = "PING";
        public static final String PONG = "PONG";

        // 实体变更
        public static final String ENTITY_CHANGED = "ENTITY_CHANGED";

        // 任务相关
        public static final String TASK_PROGRESS = "TASK_PROGRESS";
        public static final String TASK_STATUS_CHANGED = "TASK_STATUS_CHANGED";
        public static final String TASK_COMPLETED = "TASK_COMPLETED";
        public static final String TASK_FAILED = "TASK_FAILED";

        // 协作相关
        public static final String USER_JOINED = "USER_JOINED";
        public static final String USER_LEFT = "USER_LEFT";
        public static final String USER_LOCATION_CHANGED = "USER_LOCATION_CHANGED";
        public static final String ENTITY_COLLABORATION = "ENTITY_COLLABORATION";
        public static final String SCRIPT_COLLABORATION = "SCRIPT_COLLABORATION";
        public static final String EDITING_LOCKED = "EDITING_LOCKED";

        // 素材相关
        public static final String ASSET_UPLOADED = "ASSET_UPLOADED";

        // 钱包相关
        public static final String WALLET_BALANCE_CHANGED = "WALLET_BALANCE_CHANGED";

        private Type() {
        }
    }

    /**
     * 业务域枚举
     */
    public static final class Domain {
        public static final String SYSTEM = "system";
        public static final String PROJECT = "project";
        public static final String TASK = "task";
        public static final String COLLAB = "collab";
        public static final String CANVAS = "canvas";
        public static final String WALLET = "wallet";

        private Domain() {
        }
    }

    /**
     * 动作枚举
     */
    public static final class Action {
        public static final String CREATED = "CREATED";
        public static final String UPDATED = "UPDATED";
        public static final String DELETED = "DELETED";

        private Action() {
        }
    }

    /**
     * 创建实体变更消息
     */
    public static WebSocketMessage entityChanged(String domain, String action, String entityType,
                                                  String entityId, String workspaceId,
                                                  String scriptId, Map<String, Object> data) {
        return WebSocketMessage.builder()
                .type(Type.ENTITY_CHANGED)
                .domain(domain)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .workspaceId(workspaceId)
                .scriptId(scriptId)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .eventId(UuidGenerator.generateShortId())
                .build();
    }

    /**
     * 创建任务状态变更消息
     */
    public static WebSocketMessage taskStatusChanged(String taskId, String status,
                                                      Integer progress, String workspaceId,
                                                      Map<String, Object> data) {
        return WebSocketMessage.builder()
                .type(Type.TASK_STATUS_CHANGED)
                .domain(Domain.TASK)
                .entityType("task")
                .entityId(taskId)
                .workspaceId(workspaceId)
                .data(data != null ? data : Map.of("status", status, "progress", progress != null ? progress : 0))
                .timestamp(System.currentTimeMillis())
                .eventId(deterministicEventId(Type.TASK_STATUS_CHANGED, taskId, status, String.valueOf(progress)))
                .build();
    }

    /**
     * 创建任务完成消息
     */
    public static WebSocketMessage taskCompleted(String taskId, String workspaceId, Map<String, Object> result) {
        return WebSocketMessage.builder()
                .type(Type.TASK_COMPLETED)
                .domain(Domain.TASK)
                .entityType("task")
                .entityId(taskId)
                .workspaceId(workspaceId)
                .data(result)
                .timestamp(System.currentTimeMillis())
                .eventId(deterministicEventId(Type.TASK_COMPLETED, taskId, workspaceId))
                .build();
    }

    /**
     * 创建任务失败消息
     */
    public static WebSocketMessage taskFailed(String taskId, String workspaceId, String errorMessage) {
        return WebSocketMessage.builder()
                .type(Type.TASK_FAILED)
                .domain(Domain.TASK)
                .entityType("task")
                .entityId(taskId)
                .workspaceId(workspaceId)
                .data(Map.of("errorMessage", errorMessage != null ? errorMessage : "未知错误"))
                .timestamp(System.currentTimeMillis())
                .eventId(deterministicEventId(Type.TASK_FAILED, taskId, workspaceId))
                .build();
    }

    /**
     * 基于业务键派生确定性 eventId
     * 目的：让同一逻辑事件（相同 taskId/type 等）无论经由哪条发布路径、被哪个消费者实例处理，
     * 都能在 Redis 去重层与 DB 唯一索引上命中，避免 t_notification 重复写入
     */
    public static String deterministicEventId(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append('|');
            sb.append(parts[i] == null ? "" : parts[i]);
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(32);
            for (int i = 0; i < 16; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return UuidGenerator.generateShortId();
        }
    }

    /**
     * 创建通用消息
     */
    public static WebSocketMessage of(String type, String domain, String workspaceId, Map<String, Object> data) {
        return WebSocketMessage.builder()
                .type(type)
                .domain(domain)
                .workspaceId(workspaceId)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .eventId(UuidGenerator.generateShortId())
                .build();
    }

    /**
     * 创建钱包余额变动消息
     */
    public static WebSocketMessage walletBalanceChanged(
            String workspaceId, Long balance, Long frozen,
            Long delta, String transactionType, String transactionId) {
        Map<String, Object> data = new HashMap<>();
        data.put("balance", balance);
        data.put("frozen", frozen);
        data.put("delta", delta);
        data.put("transactionType", transactionType);
        data.put("transactionId", transactionId);
        return WebSocketMessage.builder()
                .type(Type.WALLET_BALANCE_CHANGED)
                .domain(Domain.WALLET)
                .action(Action.UPDATED)
                .entityType("wallet")
                .workspaceId(workspaceId)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .eventId(UuidGenerator.generateShortId())
                .build();
    }
}
