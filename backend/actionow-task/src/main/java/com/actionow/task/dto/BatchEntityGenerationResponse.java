package com.actionow.task.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量实体生成响应
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchEntityGenerationResponse {

    /**
     * 批次 ID
     */
    private String batchId;

    /**
     * 批次名称
     */
    private String batchName;

    /**
     * 总请求数
     */
    private int totalCount;

    /**
     * 成功提交数
     */
    private int submittedCount;

    /**
     * 失败数
     */
    private int failedCount;

    /**
     * 是否并行处理
     */
    private boolean parallel;

    /**
     * 总冻结积分
     */
    private long totalFrozenCredits;

    /**
     * 各请求结果列表
     */
    private List<EntityGenerationResponse> results;
}
