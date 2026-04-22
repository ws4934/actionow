package com.actionow.task.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 执行状态响应
 * 用于查询 POLLING 模式下的任务执行状态
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
     * 执行状态：PENDING, RUNNING, SUCCEEDED, FAILED, TIMEOUT, CANCELLED, NOT_FOUND
     */
    private String status;

    /**
     * 是否已完成（终态）
     */
    private boolean completed;

    /**
     * 状态消息
     */
    private String message;
}
