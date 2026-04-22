package com.actionow.project.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.project.dto.inspiration.InspirationGenerateRequest;
import com.actionow.project.dto.inspiration.InspirationGenerateResponse;
import com.actionow.project.dto.inspiration.InspirationRecordResponse;

import java.util.Map;

/**
 * 灵感生成记录服务接口。
 *
 * <p><b>已 deprecated</b>：被 Asset + EntityRelation 统一流程取代，详见
 * {@link com.actionow.project.controller.InspirationController}。
 * 仅保留以维持现网功能；新代码请勿引入此接口的依赖。
 *
 * @author Actionow
 */
@Deprecated(since = "3.0", forRemoval = true)
public interface InspirationRecordService {

    Page<InspirationRecordResponse> listRecords(String sessionId, Integer page, Integer size);

    void deleteRecord(String sessionId, String recordId);

    InspirationGenerateResponse submitGeneration(InspirationGenerateRequest request,
                                                  String workspaceId, String userId);

    /**
     * 处理任务完成回调（由 MQ Consumer 调用）
     *
     * @param taskId       任务 ID
     * @param status       任务状态（COMPLETED / FAILED）
     * @param outputResult 输出结果（含 fileUrl/thumbnailUrl 等）
     * @param creditCost   任务上报的积分消耗（可能为 0）
     * @param errorMessage 错误信息
     * @param inputParams  任务的输入参数（含 providerName、costPoints 等）
     */
    void handleTaskCompleted(String taskId, String status, Map<String, Object> outputResult,
                              Number creditCost, String errorMessage, Map<String, Object> inputParams);
}
