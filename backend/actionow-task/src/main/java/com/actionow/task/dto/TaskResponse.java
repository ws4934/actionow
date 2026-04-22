package com.actionow.task.dto;

import com.actionow.task.entity.Task;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 任务响应
 *
 * @author Actionow
 */
@Data
public class TaskResponse {

    private String id;
    private String workspaceId;
    private String type;
    private String title;
    private String status;
    private Integer priority;
    private Integer progress;

    // 实体上下文
    private String scriptId;
    private String scriptName;
    private String entityId;
    private String entityType;
    private String entityName;

    // 生成上下文
    private String providerId;
    private String providerName;
    private String generationType;
    private String thumbnailUrl;

    // 费用与来源
    private Integer creditCost;
    private String source;

    private Map<String, Object> inputParams;
    private Map<String, Object> outputResult;
    private String errorMessage;
    private Integer retryCount;
    private Integer maxRetry;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Integer timeoutSeconds;
    private String errorCode;
    private LocalDateTime timeoutAt;
    private String creatorId;
    private String creatorName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static TaskResponse fromEntity(Task task) {
        TaskResponse response = new TaskResponse();
        response.setId(task.getId());
        response.setWorkspaceId(task.getWorkspaceId());
        response.setType(task.getType());
        response.setTitle(task.getTitle());
        response.setStatus(task.getStatus());
        response.setPriority(task.getPriority());
        response.setProgress(task.getProgress());
        // 实体上下文
        response.setScriptId(task.getScriptId());
        response.setEntityId(task.getEntityId());
        response.setEntityType(task.getEntityType());
        response.setEntityName(task.getEntityName());
        // 生成上下文
        response.setProviderId(task.getProviderId());
        response.setGenerationType(task.getGenerationType());
        response.setThumbnailUrl(task.getThumbnailUrl());
        // 费用与来源
        response.setCreditCost(task.getCreditCost());
        response.setSource(task.getSource());

        response.setInputParams(task.getInputParams());
        response.setOutputResult(task.getOutputResult());
        response.setErrorMessage(task.getErrorMessage());
        response.setRetryCount(task.getRetryCount());
        response.setMaxRetry(task.getMaxRetry());
        response.setStartedAt(task.getStartedAt());
        response.setCompletedAt(task.getCompletedAt());
        response.setTimeoutSeconds(task.getTimeoutSeconds());
        response.setErrorCode(task.getErrorCode());
        response.setTimeoutAt(task.getTimeoutAt());
        response.setCreatorId(task.getCreatorId());
        response.setCreatedAt(task.getCreatedAt());
        response.setUpdatedAt(task.getUpdatedAt());
        return response;
    }
}
