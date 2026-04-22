package com.actionow.project.dto.inspiration;

import com.actionow.project.entity.InspirationRecord;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 灵感生成记录响应
 *
 * @author Actionow
 */
@Data
public class InspirationRecordResponse {

    private String id;
    private String sessionId;
    private String prompt;
    private String negativePrompt;
    private String generationType;
    private String providerId;
    private String providerName;
    private String providerIconUrl;
    private Map<String, Object> params;
    private String status;
    private List<InspirationAssetResponse> assets;
    private List<InspirationAssetResponse> refAssets;
    private String taskId;
    private BigDecimal creditCost;
    private Integer progress;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    public static InspirationRecordResponse fromEntity(InspirationRecord record) {
        InspirationRecordResponse response = new InspirationRecordResponse();
        response.setId(record.getId());
        response.setSessionId(record.getSessionId());
        response.setPrompt(record.getPrompt());
        response.setNegativePrompt(record.getNegativePrompt());
        response.setGenerationType(record.getGenerationType());
        response.setProviderId(record.getProviderId());
        response.setProviderName(record.getProviderName());
        response.setProviderIconUrl(record.getProviderIconUrl());
        response.setParams(record.getParams());
        response.setStatus(record.getStatus());
        response.setTaskId(record.getTaskId());
        response.setCreditCost(record.getCreditCost());
        response.setProgress(record.getProgress());
        response.setErrorMessage(record.getErrorMessage());
        response.setCreatedAt(record.getCreatedAt());
        response.setCompletedAt(record.getCompletedAt());
        return response;
    }
}
