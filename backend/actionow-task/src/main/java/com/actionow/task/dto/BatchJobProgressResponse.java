package com.actionow.task.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 批量作业进度响应（SSE 推送用）
 *
 * @author Actionow
 */
@Data
@Builder
public class BatchJobProgressResponse {

    private String batchJobId;
    private String status;
    private Integer totalItems;
    private Integer completedItems;
    private Integer failedItems;
    private Integer skippedItems;
    private Integer progressPct;
    private Long actualCredits;
}
