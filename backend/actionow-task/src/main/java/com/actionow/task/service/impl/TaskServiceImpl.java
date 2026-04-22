package com.actionow.task.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.common.core.constant.CommonConstants;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.id.UuidGenerator;
import com.actionow.common.core.result.PageResult;
import com.actionow.common.core.result.ResultCode;
import com.actionow.common.mq.constant.MqConstants;
import com.actionow.common.mq.message.MessageWrapper;
import com.actionow.common.mq.outbox.TransactionalMessageProducer;
import com.actionow.common.mq.producer.MessageProducer;
import com.actionow.task.config.TaskRuntimeConfigService;
import com.actionow.task.constant.TaskConstants;
import com.actionow.task.dto.*;
import com.actionow.task.entity.Task;
import com.actionow.task.mapper.TaskMapper;
import com.actionow.task.service.TaskResponseEnricher;
import com.actionow.task.service.TaskService;
import com.actionow.task.websocket.TaskNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 任务服务实现
 * 提供通用任务管理功能（CRUD、状态流转）
 * AI 生成相关功能请使用 {@link AiGenerationOrchestrator}
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final TaskMapper taskMapper;
    private final MessageProducer messageProducer;
    private final TransactionalMessageProducer transactionalMessageProducer;
    private final TaskResponseEnricher taskResponseEnricher;
    private final TaskRuntimeConfigService runtimeConfig;
    private final TaskNotificationService notificationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskResponse create(CreateTaskRequest request, String workspaceId, String userId) {
        Task task = new Task();
        task.setId(UuidGenerator.generateUuidV7());
        task.setWorkspaceId(workspaceId);
        task.setType(request.getType());
        task.setTitle(request.getTitle() != null ? request.getTitle() : "Task-" + task.getId().substring(0, 8));
        task.setStatus(TaskConstants.TaskStatus.PENDING);
        task.setPriority(request.getPriority() != null ? request.getPriority() : TaskConstants.Priority.NORMAL);
        task.setProgress(0);
        // 实体上下文
        task.setScriptId(request.getScriptId());
        task.setEntityId(request.getEntityId());
        task.setEntityType(request.getEntityType());
        task.setEntityName(request.getEntityName());
        // 生成上下文
        task.setProviderId(request.getProviderId());
        task.setGenerationType(request.getGenerationType());
        // 来源
        task.setSource(request.getSource() != null ? request.getSource() : TaskConstants.TaskSource.MANUAL);
        task.setCreditCost(0);
        task.setInputParams(request.getInputParams());
        task.setRetryCount(0);
        task.setMaxRetry(runtimeConfig.getDefaultMaxRetry());
        task.setTimeoutSeconds(request.getTimeoutSeconds() != null ? request.getTimeoutSeconds() : runtimeConfig.getDefaultTimeoutSeconds());
        task.setCreatorId(userId);

        taskMapper.insert(task);

        // 发送任务创建消息到队列
        sendTaskCreatedMessage(task);

        log.info("任务创建成功: taskId={}, type={}, workspaceId={}", task.getId(), task.getType(), workspaceId);

        return TaskResponse.fromEntity(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(String taskId, String userId) {
        Task task = getTaskOrThrow(taskId);

        // 只有 PENDING 状态的任务可以取消（RUNNING 应走 cancel-ai 路径）
        if (!TaskConstants.TaskStatus.PENDING.equals(task.getStatus())) {
            throw new BusinessException(ResultCode.TASK_ALREADY_RUNNING);
        }

        task.setStatus(TaskConstants.TaskStatus.CANCELLED);
        task.setCompletedAt(LocalDateTime.now());
        taskMapper.updateById(task);

        notificationService.notifyTaskStatusChange(task.getWorkspaceId(), TaskResponse.fromEntity(task));

        log.info("任务取消成功: taskId={}", taskId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskResponse retry(String taskId, String userId) {
        Task task = getTaskOrThrow(taskId);

        // 只有失败的任务可以重试
        if (!TaskConstants.TaskStatus.FAILED.equals(task.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "只有失败的任务可以重试");
        }

        // 检查重试次数
        if (task.getRetryCount() >= task.getMaxRetry()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "已达到最大重试次数");
        }

        task.setStatus(TaskConstants.TaskStatus.PENDING);
        task.setProgress(0);
        task.setRetryCount(task.getRetryCount() + 1);
        task.setErrorCode(null);
        task.setErrorMessage(null);
        task.setErrorDetail(null);
        task.setStartedAt(null);
        task.setCompletedAt(null);
        task.setTimeoutAt(null);
        taskMapper.updateById(task);

        // 发送任务重试消息
        sendTaskCreatedMessage(task);

        log.info("任务重试成功: taskId={}, retryCount={}", taskId, task.getRetryCount());

        return TaskResponse.fromEntity(task);
    }

    @Override
    public TaskResponse getById(String taskId) {
        Task task = getTaskOrThrow(taskId);
        TaskResponse response = TaskResponse.fromEntity(task);
        taskResponseEnricher.enrich(response);
        return response;
    }

    @Override
    public Task getEntityById(String taskId) {
        return getTaskOrThrow(taskId);
    }

    @Override
    public List<TaskResponse> listByWorkspace(String workspaceId) {
        List<Task> tasks = taskMapper.selectByWorkspaceId(workspaceId);
        if (tasks.size() >= 500) {
            log.warn("全量查询命中硬上限，结果可能被截断: workspaceId={}, count={}", workspaceId, tasks.size());
        }
        List<TaskResponse> responses = tasks.stream()
                .map(TaskResponse::fromEntity)
                .collect(Collectors.toList());
        taskResponseEnricher.enrich(responses);
        return responses;
    }

    @Override
    public PageResult<TaskResponse> listByWorkspacePage(String workspaceId, Long current, Long size,
                                                         String status, String type,
                                                         String scriptId, String entityType) {
        // 参数校验
        if (current == null || current < 1) {
            current = 1L;
        }
        if (size == null || size < 1) {
            size = 20L;
        }
        if (size > 100) {
            size = 100L;
        }

        Page<Task> page = new Page<>(current, size);
        IPage<Task> taskPage = taskMapper.selectPageByWorkspaceId(page, workspaceId, status, type, scriptId, entityType);

        if (taskPage.getRecords().isEmpty()) {
            return PageResult.empty(current, size);
        }

        List<TaskResponse> records = taskPage.getRecords().stream()
                .map(TaskResponse::fromEntity)
                .collect(Collectors.toList());
        taskResponseEnricher.enrich(records);

        return PageResult.of(taskPage.getCurrent(), taskPage.getSize(), taskPage.getTotal(), records);
    }

    @Override
    public List<TaskResponse> listByWorkspaceAndStatus(String workspaceId, String status) {
        List<Task> tasks = taskMapper.selectByWorkspaceIdAndStatus(workspaceId, status);
        List<TaskResponse> responses = tasks.stream()
                .map(TaskResponse::fromEntity)
                .collect(Collectors.toList());
        taskResponseEnricher.enrich(responses);
        return responses;
    }

    @Override
    public List<TaskResponse> listByCreator(String creatorId) {
        List<Task> tasks = taskMapper.selectByCreatorId(creatorId);
        if (tasks.size() >= 500) {
            log.warn("全量查询命中硬上限，结果可能被截断: creatorId={}, count={}", creatorId, tasks.size());
        }
        List<TaskResponse> responses = tasks.stream()
                .map(TaskResponse::fromEntity)
                .collect(Collectors.toList());
        taskResponseEnricher.enrich(responses);
        return responses;
    }

    @Override
    public PageResult<TaskResponse> listByCreatorPage(String creatorId, Long current, Long size, String status) {
        // 参数校验
        if (current == null || current < 1) {
            current = 1L;
        }
        if (size == null || size < 1) {
            size = 20L;
        }
        if (size > 100) {
            size = 100L;
        }

        Page<Task> page = new Page<>(current, size);
        IPage<Task> taskPage = taskMapper.selectPageByCreatorId(page, creatorId, status);

        if (taskPage.getRecords().isEmpty()) {
            return PageResult.empty(current, size);
        }

        List<TaskResponse> records = taskPage.getRecords().stream()
                .map(TaskResponse::fromEntity)
                .collect(Collectors.toList());
        taskResponseEnricher.enrich(records);

        return PageResult.of(taskPage.getCurrent(), taskPage.getSize(), taskPage.getTotal(), records);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProgress(String taskId, int progress) {
        if (progress < 0 || progress > 100) {
            throw new BusinessException(ResultCode.PARAM_OUT_OF_RANGE);
        }
        taskMapper.updateProgress(taskId, progress);

        Task task = taskMapper.selectById(taskId);
        if (task != null) {
            notificationService.notifyTaskProgress(task.getWorkspaceId(), taskId, progress, task.getScriptId());
        }

        log.debug("任务进度更新: taskId={}, progress={}", taskId, progress);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startTask(String taskId) {
        Task task = getTaskOrThrow(taskId);

        if (!TaskConstants.TaskStatus.PENDING.equals(task.getStatus())) {
            throw new BusinessException(ResultCode.TASK_ALREADY_RUNNING);
        }

        LocalDateTime startedAt = LocalDateTime.now();
        task.setStatus(TaskConstants.TaskStatus.RUNNING);
        task.setStartedAt(startedAt);
        task.setTimeoutAt(calculateTimeoutAt(startedAt, task.getTimeoutSeconds()));
        taskMapper.updateById(task);

        notificationService.notifyTaskStatusChange(task.getWorkspaceId(), TaskResponse.fromEntity(task));

        log.info("任务开始执行: taskId={}", taskId);
    }

    private LocalDateTime calculateTimeoutAt(LocalDateTime startedAt, Integer timeoutSeconds) {
        int timeout = timeoutSeconds != null && timeoutSeconds > 0
                ? timeoutSeconds
                : runtimeConfig.getDefaultTimeoutSeconds();
        return startedAt.plusSeconds(timeout);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeTask(String taskId, Map<String, Object> result) {
        Task task = getTaskOrThrow(taskId);

        task.setStatus(TaskConstants.TaskStatus.COMPLETED);
        task.setProgress(100);
        task.setOutputResult(result);
        task.setCompletedAt(LocalDateTime.now());
        taskMapper.updateById(task);

        // 发送任务完成消息
        sendTaskStatusChangedMessage(task);

        log.info("任务完成: taskId={}", taskId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void failTask(String taskId, String errorMessage, Map<String, Object> errorDetail) {
        Task task = getTaskOrThrow(taskId);

        task.setStatus(TaskConstants.TaskStatus.FAILED);
        task.setErrorMessage(errorMessage);
        task.setErrorDetail(errorDetail);
        task.setCompletedAt(LocalDateTime.now());
        taskMapper.updateById(task);

        // 发送任务失败消息
        sendTaskStatusChangedMessage(task);

        log.info("任务失败: taskId={}, error={}", taskId, errorMessage);
    }

    @Override
    public List<TaskResponse> getPendingTasks(int limit) {
        List<Task> tasks = taskMapper.selectPendingTasks(limit);
        return tasks.stream()
                .map(TaskResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public int getRunningTaskCount(String workspaceId) {
        return taskMapper.countRunningTasks(workspaceId);
    }

    // ==================== 私有辅助方法 ====================

    private Task getTaskOrThrow(String taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null || task.getDeleted() == CommonConstants.DELETED) {
            throw new BusinessException(ResultCode.TASK_NOT_FOUND);
        }
        return task;
    }

    /**
     * 事务性发送任务创建消息（Outbox 模式）
     * 消息写入 outbox 表，与 task INSERT 同一事务，保证原子性。
     */
    private void sendTaskCreatedMessage(Task task) {
        MessageWrapper<TaskResponse> message = MessageWrapper.wrap(
                MqConstants.Task.MSG_CREATED,
                TaskResponse.fromEntity(task)
        );
        transactionalMessageProducer.sendInTransaction(
                MqConstants.EXCHANGE_DIRECT, MqConstants.Task.ROUTING_CREATED, message
        );
    }

    /**
     * 事务性发送任务状态变更消息（Outbox 模式）
     */
    private void sendTaskStatusChangedMessage(Task task) {
        MessageWrapper<TaskResponse> message = MessageWrapper.wrap(
                MqConstants.Task.MSG_STATUS_CHANGED,
                TaskResponse.fromEntity(task)
        );
        transactionalMessageProducer.sendInTransaction(
                MqConstants.EXCHANGE_DIRECT, MqConstants.Task.ROUTING_COMPLETED, message
        );

        // 发送 Mission 任务回调（如果任务关联了 Mission，由 Mission 模块自行判断）
        sendMissionTaskCallback(task);
    }

    /**
     * 事务性发送 Mission 任务完成回调（Outbox 模式）
     * Mission 模块通过 pending_task_ids 判断任务是否关联
     */
    private void sendMissionTaskCallback(Task task) {
        Map<String, Object> payload = Map.of(
                "taskId", task.getId(),
                "status", task.getStatus(),
                "entityType", task.getEntityType() != null ? task.getEntityType() : "",
                "entityId", task.getEntityId() != null ? task.getEntityId() : ""
        );
        MessageWrapper<Map<String, Object>> message = MessageWrapper.wrap(
                MqConstants.Mission.MSG_TASK_CALLBACK,
                payload
        );
        transactionalMessageProducer.sendInTransaction(
                MqConstants.EXCHANGE_DIRECT, MqConstants.Mission.ROUTING_TASK_CALLBACK, message
        );
    }
}
