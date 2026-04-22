package com.actionow.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 执行状态响应
 * 用于轮询模式查询任务执行状态
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionStatusResponse {

    /**
     * 执行 ID
     */
    private String executionId;

    /**
     * 执行状态
     * PENDING - 等待中
     * RUNNING - 执行中
     * SUCCEEDED - 成功
     * FAILED - 失败
     * CANCELLED - 已取消
     * NOT_FOUND - 未找到
     */
    private String status;

    /**
     * 是否已完成（终态）
     */
    private boolean completed;

    /**
     * 状态描述信息
     */
    private String message;
}
