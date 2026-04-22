package com.actionow.task.feign;

import com.actionow.common.api.ai.ProviderExecuteRequest;
import com.actionow.common.core.result.Result;
import com.actionow.task.dto.AvailableProviderResponse;
import com.actionow.task.dto.ExecutionStatusResponse;
import com.actionow.task.dto.ProviderExecutionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * AI 服务 Feign 客户端降级工厂
 *
 * @author Actionow
 */
@Slf4j
@Component
public class AiFeignClientFallbackFactory implements FallbackFactory<AiFeignClient> {

    @Override
    public AiFeignClient create(Throwable cause) {
        log.error("调用 AI 服务失败: {}", cause.getMessage());
        return new AiFeignClient() {
            @Override
            public Result<ProviderExecutionResult> executeProvider(ProviderExecuteRequest request) {
                log.warn("执行模型提供商降级: taskId={}", request.getTaskId());
                return Result.fail("50001", "AI 服务暂时不可用");
            }

            @Override
            public Result<List<AvailableProviderResponse>> getAvailableProviders(String providerType) {
                log.warn("获取可用模型提供商降级: providerType={}", providerType);
                return Result.success(Collections.emptyList());
            }

            @Override
            public Result<AvailableProviderResponse> getProviderDetail(String providerId) {
                log.warn("获取模型提供商详情降级: providerId={}", providerId);
                return Result.fail("50001", "AI 服务暂时不可用");
            }

            @Override
            public Result<Map<String, Object>> estimateCost(String providerId, Map<String, Object> params) {
                log.warn("预估积分消耗降级: providerId={}", providerId);
                return Result.fail("50001", "AI 服务暂时不可用");
            }

            @Override
            public Result<ExecutionStatusResponse> getExecutionStatus(String executionId) {
                log.warn("查询执行状态降级: executionId={}", executionId);
                return Result.fail("50001", "AI 服务暂时不可用");
            }

            @Override
            public Result<ProviderExecutionResult> getExecutionResult(String executionId, long timeout) {
                log.warn("获取执行结果降级: executionId={}, timeout={}", executionId, timeout);
                return Result.fail("50001", "AI 服务暂时不可用");
            }

            @Override
            public Result<Void> cancelExecution(String executionId, String pluginId,
                                                 String externalTaskId, String userId) {
                log.warn("取消执行降级: executionId={}", executionId);
                return Result.fail("50001", "AI 服务暂时不可用");
            }
        };
    }
}
