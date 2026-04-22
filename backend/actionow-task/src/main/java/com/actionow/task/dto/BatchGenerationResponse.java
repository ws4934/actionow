package com.actionow.task.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量生成任务响应
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchGenerationResponse {

    /**
     * 批量任务ID
     */
    private String batchId;

    /**
     * 批量任务名称
     */
    private String batchName;

    /**
     * 任务总数
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
     * 是否串行执行
     */
    private boolean sequential;

    /**
     * 各任务响应
     */
    private List<TaskSubmitResult> tasks;

    /**
     * 冻结积分总数
     */
    private int totalFrozenCredits;

    /**
     * 单个任务提交结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskSubmitResult {
        /**
         * 任务索引
         */
        private int index;

        /**
         * 是否提交成功
         */
        private boolean success;

        /**
         * 任务ID（成功时）
         */
        private String taskId;

        /**
         * 素材ID（成功时）
         */
        private String assetId;

        /**
         * 错误信息（失败时）
         */
        private String errorMessage;

        /**
         * 冻结积分
         */
        private Integer frozenCredits;
    }
}
