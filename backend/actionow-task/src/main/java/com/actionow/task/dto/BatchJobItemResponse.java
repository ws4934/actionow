package com.actionow.task.dto;

import com.actionow.task.entity.BatchJobItem;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 批量作业子项响应
 *
 * @author Actionow
 */
@Data
public class BatchJobItemResponse {

    private String id;
    private String batchJobId;
    private Integer sequenceNumber;
    private String entityType;
    private String entityId;
    private String entityName;
    private Map<String, Object> params;
    private String providerId;
    private String generationType;
    private String pipelineStepId;
    private String taskId;
    private String assetId;
    private String relationId;
    private String skipCondition;
    private Boolean skipped;
    private String skipReason;
    private Integer variantIndex;
    private Long variantSeed;
    private String status;
    private String errorMessage;
    private Long creditCost;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static BatchJobItemResponse fromEntity(BatchJobItem item) {
        BatchJobItemResponse response = new BatchJobItemResponse();
        response.setId(item.getId());
        response.setBatchJobId(item.getBatchJobId());
        response.setSequenceNumber(item.getSequenceNumber());
        response.setEntityType(item.getEntityType());
        response.setEntityId(item.getEntityId());
        response.setEntityName(item.getEntityName());
        response.setParams(item.getParams());
        response.setProviderId(item.getProviderId());
        response.setGenerationType(item.getGenerationType());
        response.setPipelineStepId(item.getPipelineStepId());
        response.setTaskId(item.getTaskId());
        response.setAssetId(item.getAssetId());
        response.setRelationId(item.getRelationId());
        response.setSkipCondition(item.getSkipCondition());
        response.setSkipped(item.getSkipped());
        response.setSkipReason(item.getSkipReason());
        response.setVariantIndex(item.getVariantIndex());
        response.setVariantSeed(item.getVariantSeed());
        response.setStatus(item.getStatus());
        response.setErrorMessage(item.getErrorMessage());
        response.setCreditCost(item.getCreditCost());
        response.setCreatedAt(item.getCreatedAt());
        response.setUpdatedAt(item.getUpdatedAt());
        return response;
    }
}
