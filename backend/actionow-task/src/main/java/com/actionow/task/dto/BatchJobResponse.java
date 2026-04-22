package com.actionow.task.dto;

import com.actionow.task.entity.BatchJob;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 批量作业响应
 *
 * @author Actionow
 */
@Data
public class BatchJobResponse {

    private String id;
    private String workspaceId;
    private String creatorId;
    private String name;
    private String description;
    private String batchType;
    private String scriptId;
    private String scopeEntityType;
    private String scopeEntityId;
    private String errorStrategy;
    private Integer maxConcurrency;
    private Integer priority;
    private Map<String, Object> sharedParams;
    private String providerId;
    private String generationType;
    private String status;
    private Integer totalItems;
    private Integer completedItems;
    private Integer failedItems;
    private Integer skippedItems;
    private Integer progress;
    private Long estimatedCredits;
    private Long actualCredits;
    private String missionId;
    private String source;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static BatchJobResponse fromEntity(BatchJob job) {
        BatchJobResponse response = new BatchJobResponse();
        response.setId(job.getId());
        response.setWorkspaceId(job.getWorkspaceId());
        response.setCreatorId(job.getCreatorId());
        response.setName(job.getName());
        response.setDescription(job.getDescription());
        response.setBatchType(job.getBatchType());
        response.setScriptId(job.getScriptId());
        response.setScopeEntityType(job.getScopeEntityType());
        response.setScopeEntityId(job.getScopeEntityId());
        response.setErrorStrategy(job.getErrorStrategy());
        response.setMaxConcurrency(job.getMaxConcurrency());
        response.setPriority(job.getPriority());
        response.setSharedParams(job.getSharedParams());
        response.setProviderId(job.getProviderId());
        response.setGenerationType(job.getGenerationType());
        response.setStatus(job.getStatus());
        response.setTotalItems(job.getTotalItems());
        response.setCompletedItems(job.getCompletedItems());
        response.setFailedItems(job.getFailedItems());
        response.setSkippedItems(job.getSkippedItems());
        response.setProgress(job.getProgress());
        response.setEstimatedCredits(job.getEstimatedCredits());
        response.setActualCredits(job.getActualCredits());
        response.setMissionId(job.getMissionId());
        response.setSource(job.getSource());
        response.setStartedAt(job.getStartedAt());
        response.setCompletedAt(job.getCompletedAt());
        response.setCreatedAt(job.getCreatedAt());
        response.setUpdatedAt(job.getUpdatedAt());
        return response;
    }
}
