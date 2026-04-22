package com.actionow.task.service;

import com.actionow.task.dto.ProviderExecutionResult;
import com.actionow.task.entity.Task;

import java.util.Map;

/**
 * 任务完成回调接口
 * 用于 TaskExecutionService 通知编排器处理任务完成/失败后的后续逻辑
 * （积分确认/解冻、MQ 消息、批量作业回调等），避免循环依赖。
 *
 * @author Actionow
 */
public interface TaskCompletionHandler {

    /**
     * 任务执行成功
     *
     * @param task   任务实体
     * @param result 提供商执行结果
     */
    void onSuccess(Task task, ProviderExecutionResult result);

    /**
     * 任务执行失败
     *
     * @param task         任务实体
     * @param errorCode    错误码（可为 null）
     * @param errorMessage 错误消息
     * @param errorDetail  错误详情
     */
    void onFailure(Task task, String errorCode, String errorMessage, Map<String, Object> errorDetail);
}
