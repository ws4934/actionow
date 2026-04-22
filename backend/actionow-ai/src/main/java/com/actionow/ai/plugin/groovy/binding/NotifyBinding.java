package com.actionow.ai.plugin.groovy.binding;

import com.actionow.common.mq.constant.MqConstants;
import com.actionow.common.mq.message.WebSocketMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 消息通知绑定
 * 提供脚本中可用的消息通知方法
 * 通过 RabbitMQ 发送通知到消息队列
 *
 * @author Actionow
 */
@Slf4j
public class NotifyBinding {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 工作空间ID
     */
    private String workspaceId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 执行ID（任务ID）
     */
    private String executionId;

    /**
     * 租户Schema
     */
    private String tenantSchema;

    public NotifyBinding(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 设置上下文信息
     *
     * @param workspaceId  工作空间ID
     * @param userId       用户ID
     * @param executionId  执行ID
     * @param tenantSchema 租户Schema
     */
    public void setContext(String workspaceId, String userId, String executionId, String tenantSchema) {
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.executionId = executionId;
        this.tenantSchema = tenantSchema;
    }

    // ==================== 任务相关通知 ====================

    /**
     * 发送任务进度通知
     *
     * @param progress 进度百分比（0-100）
     * @param message  进度消息
     */
    public void taskProgress(int progress, String message) {
        log.info("[NotifyBinding] Task progress: {}% - {}", progress, message);

        Map<String, Object> payload = new HashMap<>();
        payload.put("taskId", executionId);
        payload.put("progress", Math.min(100, Math.max(0, progress)));
        payload.put("message", message);
        payload.put("stage", determineStage(progress));

        sendNotification(NotificationType.TASK_PROGRESS, "任务进度", message, payload);
    }

    /**
     * 发送任务完成通知
     *
     * @param result 结果数据
     */
    public void taskCompleted(Map<String, Object> result) {
        log.info("[NotifyBinding] Task completed: {}", executionId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("taskId", executionId);
        payload.put("result", result);

        sendNotification(NotificationType.TASK_COMPLETED, "任务完成", "AI生成任务已完成", payload);
    }

    /**
     * 发送任务失败通知
     *
     * @param error 错误信息
     */
    public void taskFailed(String error) {
        log.warn("[NotifyBinding] Task failed: {} - {}", executionId, error);

        Map<String, Object> payload = new HashMap<>();
        payload.put("taskId", executionId);
        payload.put("error", error);

        sendNotification(NotificationType.TASK_FAILED, "任务失败", error, payload);
    }

    // ==================== 生成结果通知 ====================

    /**
     * 发送生成结果通知
     *
     * @param assetId  素材ID
     * @param fileUrl  文件URL
     * @param metadata 元数据
     */
    public void generationResult(String assetId, String fileUrl, Map<String, Object> metadata) {
        log.info("[NotifyBinding] Generation result for asset: {}", assetId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("taskId", executionId);
        payload.put("assetId", assetId);
        payload.put("fileUrl", fileUrl);
        payload.put("metadata", metadata);

        sendNotification(NotificationType.GENERATION_RESULT, "生成完成", "AI内容生成完成", payload);
    }

    // ==================== 解析结果通知 ====================

    /**
     * 发送剧本解析完成通知
     *
     * @param scriptId 剧本ID
     * @param summary  解析摘要
     */
    public void parseCompleted(String scriptId, Map<String, Object> summary) {
        log.info("[NotifyBinding] Script parse completed: {}", scriptId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("taskId", executionId);
        payload.put("scriptId", scriptId);
        payload.put("summary", summary);

        String message = buildParseSummary(summary);
        sendNotification(NotificationType.PARSE_COMPLETED, "解析完成", message, payload);
    }

    /**
     * 发送实体创建通知
     *
     * @param entityType 实体类型（character/scene/prop/episode/storyboard）
     * @param entityId   实体ID
     * @param entityName 实体名称
     */
    public void entityCreated(String entityType, String entityId, String entityName) {
        log.info("[NotifyBinding] Entity created: {} - {} ({})", entityType, entityName, entityId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("taskId", executionId);
        payload.put("entityType", entityType);
        payload.put("entityId", entityId);
        payload.put("entityName", entityName);

        String message = String.format("已创建%s: %s", getEntityTypeName(entityType), entityName);
        sendNotification(NotificationType.ENTITY_CREATED, "创建成功", message, payload);
    }

    // ==================== 自定义通知 ====================

    /**
     * 发送自定义通知
     *
     * @param type    通知类型
     * @param title   标题
     * @param message 消息内容
     * @param payload 附加数据
     */
    public void send(String type, String title, String message, Map<String, Object> payload) {
        log.info("[NotifyBinding] Custom notification: {} - {}", type, title);

        sendNotification(type, title, message, payload != null ? payload : new HashMap<>());
    }

    /**
     * 发送简单通知（仅消息）
     *
     * @param message 消息内容
     */
    public void send(String message) {
        send("INFO", "通知", message, null);
    }

    /**
     * 发送带标题的简单通知
     *
     * @param title   标题
     * @param message 消息内容
     */
    public void send(String title, String message) {
        send("INFO", title, message, null);
    }

    // ==================== 系统通知 ====================

    /**
     * 发送系统告警（用于脚本运行异常等）
     *
     * @param level   告警级别（INFO/WARN/ERROR）
     * @param message 告警消息
     */
    public void alert(String level, String message) {
        log.warn("[NotifyBinding] System alert [{}]: {}", level, message);

        Map<String, Object> payload = new HashMap<>();
        payload.put("taskId", executionId);
        payload.put("level", level);
        payload.put("source", "groovy-script");

        sendNotification(NotificationType.SYSTEM_ALERT, "系统告警", message, payload);
    }

    // ==================== 私有方法 ====================

    /**
     * 发送通知到消息队列
     * <p>
     * 消息结构与 WebSocketNotificationConsumer 期望的格式对齐：
     * - 顶层字段直接映射为 WebSocketMessage（无 payload 嵌套）
     * - targetUserId 放在顶层，供 Consumer 读取后持久化并定向推送
     * - title / content 放在顶层，供 Consumer.persistNotification 读取
     */
    private void sendNotification(String type, String title, String message, Map<String, Object> payload) {
        try {
            Map<String, Object> notification = new HashMap<>();
            // WebSocketMessage 字段
            notification.put("type", type);
            notification.put("domain", "task");
            notification.put("entityType", "task");
            notification.put("entityId", executionId);
            notification.put("workspaceId", workspaceId);
            notification.put("data", payload);
            // 确定性 eventId：基于 type + executionId + workspaceId + userId 派生
            // 同一逻辑事件（同 taskId + type）无论被触发多少次，eventId 始终一致，
            // 在 WebSocketNotificationConsumer 的 Redis 去重和 t_notification 的唯一索引上都可命中
            notification.put("eventId",
                    WebSocketMessage.deterministicEventId(type, executionId, workspaceId, userId));
            notification.put("timestamp", System.currentTimeMillis());

            // Consumer 持久化所需的额外字段
            notification.put("targetUserId", userId);
            notification.put("title", title);
            notification.put("content", message);
            notification.put("senderId", userId);

            // 使用正确的 routing key，与 wsNotificationQueue 的绑定匹配
            rabbitTemplate.convertAndSend(
                    MqConstants.EXCHANGE_DIRECT,
                    MqConstants.Ws.ROUTING_TASK_STATUS,
                    notification
            );

            log.debug("[NotifyBinding] Notification sent: type={}, userId={}", type, userId);
        } catch (Exception e) {
            log.error("[NotifyBinding] Failed to send notification: type={}", type, e);
            // 不抛出异常，避免影响主流程
        }
    }

    /**
     * 根据进度判断阶段
     */
    private String determineStage(int progress) {
        if (progress < 20) {
            return "INITIALIZING";
        } else if (progress < 50) {
            return "PROCESSING";
        } else if (progress < 80) {
            return "GENERATING";
        } else if (progress < 100) {
            return "FINALIZING";
        } else {
            return "COMPLETED";
        }
    }

    /**
     * 构建解析摘要消息
     */
    private String buildParseSummary(Map<String, Object> summary) {
        StringBuilder sb = new StringBuilder("已解析");
        boolean first = true;

        if (summary.containsKey("characterCount")) {
            sb.append(" ").append(summary.get("characterCount")).append(" 个角色");
            first = false;
        }
        if (summary.containsKey("sceneCount")) {
            if (!first) sb.append(",");
            sb.append(" ").append(summary.get("sceneCount")).append(" 个场景");
            first = false;
        }
        if (summary.containsKey("propCount")) {
            if (!first) sb.append(",");
            sb.append(" ").append(summary.get("propCount")).append(" 个道具");
            first = false;
        }
        if (summary.containsKey("episodeCount")) {
            if (!first) sb.append(",");
            sb.append(" ").append(summary.get("episodeCount")).append(" 个章节");
        }

        return sb.toString();
    }

    /**
     * 获取实体类型中文名
     */
    private String getEntityTypeName(String entityType) {
        return switch (entityType.toLowerCase()) {
            case "character" -> "角色";
            case "scene" -> "场景";
            case "prop" -> "道具";
            case "style" -> "风格";
            case "episode" -> "章节";
            case "storyboard" -> "分镜";
            case "script" -> "剧本";
            default -> entityType;
        };
    }

    /**
     * 通知类型常量
     */
    public static final class NotificationType {
        public static final String TASK_PROGRESS = "TASK_PROGRESS";
        public static final String TASK_COMPLETED = "TASK_COMPLETED";
        public static final String TASK_FAILED = "TASK_FAILED";
        public static final String GENERATION_RESULT = "GENERATION_RESULT";
        public static final String PARSE_COMPLETED = "PARSE_COMPLETED";
        public static final String ENTITY_CREATED = "ENTITY_CREATED";
        public static final String SYSTEM_ALERT = "SYSTEM_ALERT";

        private NotificationType() {
        }
    }
}
