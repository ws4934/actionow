package com.actionow.task.websocket;

import com.actionow.common.core.id.UuidGenerator;
import com.actionow.common.mq.constant.MqConstants;
import com.actionow.common.mq.message.WebSocketMessage;
import com.actionow.common.mq.producer.MessageProducer;
import com.actionow.task.dto.TaskResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务通知服务
 * 通过 MQ 发送任务状态变更通知，由 collab 服务统一推送给前端
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskNotificationService {

    private final MessageProducer messageProducer;

    /** 进度通知节流：taskId → 上次发送时间戳（ms） */
    private final Map<String, Long> progressThrottle = new ConcurrentHashMap<>();
    private static final long PROGRESS_MIN_INTERVAL_MS = 1000;
    private static final int THROTTLE_MAP_MAX_SIZE = 10000;

    /**
     * 通知任务状态变更
     */
    public void notifyTaskStatusChange(String workspaceId, TaskResponse task) {
        Map<String, Object> data = new HashMap<>();
        data.put("status", task.getStatus());
        data.put("progress", task.getProgress() != null ? task.getProgress() : 0);
        data.put("taskType", task.getType());
        data.put("taskName", task.getTitle());

        WebSocketMessage message = WebSocketMessage.taskStatusChanged(
                task.getId(),
                task.getStatus(),
                task.getProgress(),
                workspaceId,
                data
        );
        message.setScriptId(task.getScriptId());

        sendNotification(workspaceId, message);
        log.debug("已发送任务状态变更通知: workspaceId={}, taskId={}, status={}",
                workspaceId, task.getId(), task.getStatus());
    }

    /**
     * 通知任务进度更新
     */
    public void notifyTaskProgress(String workspaceId, String taskId, int progress) {
        notifyTaskProgress(workspaceId, taskId, progress, null);
    }

    /**
     * 通知任务进度更新（含 scriptId 作用域）
     * 同一任务最多每秒发送一次进度通知，progress=100 始终发送
     */
    public void notifyTaskProgress(String workspaceId, String taskId, int progress, String scriptId) {
        long now = System.currentTimeMillis();
        if (progress < 100) {
            Long lastSent = progressThrottle.get(taskId);
            if (lastSent != null && (now - lastSent) < PROGRESS_MIN_INTERVAL_MS) {
                return;
            }
        } else {
            // 任务完成，清理节流记录
            progressThrottle.remove(taskId);
        }
        progressThrottle.put(taskId, now);
        // 防止异常路径导致 map 无限增长
        if (progressThrottle.size() > THROTTLE_MAP_MAX_SIZE) {
            long cutoff = now - 60_000;
            progressThrottle.entrySet().removeIf(e -> e.getValue() < cutoff);
        }

        Map<String, Object> data = Map.of("progress", progress);

        WebSocketMessage message = WebSocketMessage.builder()
                .type(WebSocketMessage.Type.TASK_PROGRESS)
                .domain(WebSocketMessage.Domain.TASK)
                .entityType("task")
                .entityId(taskId)
                .workspaceId(workspaceId)
                .scriptId(scriptId)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .eventId(UuidGenerator.generateShortId())
                .build();

        sendNotification(workspaceId, message);
    }

    /**
     * 通知任务完成
     * 同时携带 targetUserId（任务发起人），使 collab 服务能持久化通知记录
     */
    public void notifyTaskCompleted(String workspaceId, TaskResponse task) {
        progressThrottle.remove(task.getId());
        Map<String, Object> data = new HashMap<>();
        data.put("status", task.getStatus());
        data.put("taskType", task.getType());
        data.put("taskName", task.getTitle());
        if (task.getOutputResult() != null) {
            data.put("result", task.getOutputResult());
        }
        // collab WebSocketNotificationConsumer 通过此字段决定是否持久化并定向推送
        if (task.getCreatorId() != null) {
            data.put("targetUserId", task.getCreatorId());
        }

        WebSocketMessage message = WebSocketMessage.taskCompleted(
                task.getId(),
                workspaceId,
                data
        );
        message.setScriptId(task.getScriptId());

        sendNotification(workspaceId, message);
        log.info("已发送任务完成通知: workspaceId={}, taskId={}, creatorId={}",
                workspaceId, task.getId(), task.getCreatorId());
    }

    /**
     * 通知任务失败
     * 同时携带 targetUserId，使 collab 服务能持久化通知记录
     */
    public void notifyTaskFailed(String workspaceId, TaskResponse task) {
        progressThrottle.remove(task.getId());
        Map<String, Object> data = new HashMap<>();
        data.put("errorMessage", task.getErrorMessage() != null ? task.getErrorMessage() : "未知错误");
        // collab WebSocketNotificationConsumer 通过此字段决定是否持久化并定向推送
        if (task.getCreatorId() != null) {
            data.put("targetUserId", task.getCreatorId());
        }

        WebSocketMessage message = WebSocketMessage.builder()
                .type(WebSocketMessage.Type.TASK_FAILED)
                .domain(WebSocketMessage.Domain.TASK)
                .entityType("task")
                .entityId(task.getId())
                .workspaceId(workspaceId)
                .scriptId(task.getScriptId())
                .data(data)
                .timestamp(System.currentTimeMillis())
                .eventId(WebSocketMessage.deterministicEventId(
                        WebSocketMessage.Type.TASK_FAILED, task.getId(), workspaceId))
                .build();

        sendNotification(workspaceId, message);
        log.info("已发送任务失败通知: workspaceId={}, taskId={}, creatorId={}, error={}",
                workspaceId, task.getId(), task.getCreatorId(), task.getErrorMessage());
    }

    /**
     * 向特定用户发送通知
     */
    public void notifyUser(String workspaceId, String userId, String type, Map<String, Object> data) {
        Map<String, Object> extendedData = new HashMap<>(data);
        extendedData.put("targetUserId", userId);

        WebSocketMessage message = WebSocketMessage.of(type, WebSocketMessage.Domain.TASK, workspaceId, extendedData);

        sendNotification(workspaceId, message);
    }

    /**
     * 发送通知到 MQ
     */
    private void sendNotification(String workspaceId, WebSocketMessage message) {
        try {
            messageProducer.send(
                    MqConstants.EXCHANGE_DIRECT,
                    MqConstants.Ws.ROUTING_TASK_STATUS,
                    MqConstants.Ws.MSG_TASK_STATUS,
                    message
            );
        } catch (Exception e) {
            log.error("发送任务通知失败: workspaceId={}, type={}, error={}",
                    workspaceId, message.getType(), e.getMessage(), e);
        }
    }
}
