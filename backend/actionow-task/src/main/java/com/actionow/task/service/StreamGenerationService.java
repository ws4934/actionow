package com.actionow.task.service;

import com.actionow.task.dto.StreamGenerationEvent;
import com.actionow.task.dto.StreamGenerationRequest;
import reactor.core.publisher.Flux;

/**
 * 流式生成服务接口
 *
 * @author Actionow
 */
public interface StreamGenerationService {

    /**
     * 流式执行AI生成任务
     *
     * @param request     请求
     * @param workspaceId 工作空间ID
     * @param userId      用户ID
     * @return 事件流
     */
    Flux<StreamGenerationEvent> streamGenerate(StreamGenerationRequest request,
                                                String workspaceId, String userId);

    /**
     * 检查提供商是否支持流式响应
     *
     * @param providerId 提供商ID
     * @return 是否支持
     */
    boolean isStreamingSupported(String providerId);
}
