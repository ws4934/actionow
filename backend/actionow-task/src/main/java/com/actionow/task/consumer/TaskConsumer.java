package com.actionow.task.consumer;

import com.actionow.common.core.context.UserContext;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.mq.constant.MqConstants;
import com.actionow.common.mq.consumer.ConsumerRetryHelper;
import com.actionow.common.mq.message.MessageWrapper;
import com.actionow.task.constant.TaskConstants;
import com.actionow.task.dto.TaskResponse;
import com.actionow.task.entity.Task;
import com.actionow.task.config.TaskRuntimeConfigService;
import com.actionow.task.service.AiGenerationFacade;
import com.actionow.task.service.TaskService;
import com.actionow.task.websocket.TaskNotificationService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * 任务消息消费者
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskConsumer {

    private final TaskService taskService;
    private final AiGenerationFacade aiGenerationFacade;
    private final TaskNotificationService notificationService;
    private final ConsumerRetryHelper retryHelper;

    /**
     * 处理任务创建消息
     * 将任务加入执行队列
     */
    @RabbitListener(id = TaskRuntimeConfigService.TASK_EXECUTOR_LISTENER_ID, queues = MqConstants.Task.QUEUE, containerFactory = "taskExecutorContainerFactory")
    public void handleTaskCreated(MessageWrapper<TaskResponse> message, Channel channel,
                                  @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            // 恢复上下文
            restoreContext(message);

            TaskResponse task = message.getPayload();
            log.info("收到任务创建消息: taskId={}, type={}", task.getId(), task.getType());

            // 根据任务类型分发处理
            switch (task.getType()) {
                case "IMAGE_GENERATION":
                case "VIDEO_GENERATION":
                case "TEXT_GENERATION":
                case "AUDIO_GENERATION":
                case "TTS_GENERATION":
                    // AI 生成任务：调用 AiGenerationFacade 执行
                    Task taskEntity = taskService.getEntityById(task.getId());
                    aiGenerationFacade.executeTask(taskEntity);
                    log.info("AI生成任务已执行: taskId={}", task.getId());
                    break;
                case "BATCH_EXPORT":
                case "FILE_PROCESSING":
                    // 其他类型任务：直接处理
                    log.info("后台任务已加入队列: taskId={}", task.getId());
                    break;
                default:
                    log.warn("未知任务类型: {}", task.getType());
            }

            // 通知前端任务已创建
            notificationService.notifyTaskStatusChange(task.getWorkspaceId(), task);

            // 确认消息
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("处理任务创建消息失败: {}", e.getMessage(), e);
            try {
                retryHelper.retryOrDlq(message, channel, deliveryTag, 3,
                        MqConstants.EXCHANGE_DIRECT, MqConstants.Task.ROUTING_CREATED);
            } catch (Exception ex) {
                log.error("消息重试失败", ex);
            }
        } finally {
            UserContextHolder.clear();
        }
    }

    /**
     * 处理任务状态变更消息
     * 根据状态分发到具体的通知类型
     */
    @RabbitListener(queues = MqConstants.Task.QUEUE_NOTIFICATION)
    public void handleTaskStatusChange(MessageWrapper<TaskResponse> message, Channel channel,
                                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            TaskResponse task = message.getPayload();
            log.info("收到任务状态变更: taskId={}, status={}", task.getId(), task.getStatus());

            // 根据状态分发具体通知类型
            String status = task.getStatus();
            if (TaskConstants.TaskStatus.COMPLETED.equals(status)) {
                notificationService.notifyTaskCompleted(task.getWorkspaceId(), task);
            } else if (TaskConstants.TaskStatus.FAILED.equals(status)) {
                notificationService.notifyTaskFailed(task.getWorkspaceId(), task);
            } else {
                notificationService.notifyTaskStatusChange(task.getWorkspaceId(), task);
            }

            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("处理任务状态变更消息失败（通知丢失）: taskId={}, status={}, messageId={}, error={}",
                    message.getPayload() != null ? message.getPayload().getId() : "unknown",
                    message.getPayload() != null ? message.getPayload().getStatus() : "unknown",
                    message.getMessageId(), e.getMessage(), e);
            try {
                // 通知类消息不重试，避免重复推送；但异常已记录可通过日志告警发现
                channel.basicAck(deliveryTag, false);
            } catch (Exception ex) {
                log.error("消息确认失败", ex);
            }
        }
    }

    /**
     * 恢复上下文
     */
    private void restoreContext(MessageWrapper<?> message) {
        UserContext context = new UserContext();
        context.setUserId(message.getSenderId());
        context.setWorkspaceId(message.getWorkspaceId());
        context.setTenantSchema(message.getTenantSchema());
        context.setRequestId(message.getTraceId());
        UserContextHolder.setContext(context);
    }
}
