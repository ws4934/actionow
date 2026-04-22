package com.actionow.task.feign;

import com.actionow.common.api.ai.ProviderExecuteRequest;
import com.actionow.common.core.result.Result;
import com.actionow.task.dto.AvailableProviderResponse;
import com.actionow.task.dto.ExecutionStatusResponse;
import com.actionow.task.dto.ProviderExecutionResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * AI 服务 Feign 客户端
 * Task 服务调用 AI 服务执行模型提供商
 *
 * @author Actionow
 */
@FeignClient(name = "actionow-ai", path = "/internal/ai",
        configuration = AiFeignClientConfig.class,
        fallbackFactory = AiFeignClientFallbackFactory.class)
public interface AiFeignClient {

    /**
     * 执行模型提供商
     *
     * @param request 执行请求
     * @return 执行结果
     */
    @PostMapping("/provider/execute")
    Result<ProviderExecutionResult> executeProvider(@RequestBody ProviderExecuteRequest request);

    /**
     * 获取可用模型提供商列表
     *
     * @param providerType 生成类型（IMAGE/VIDEO/AUDIO/TEXT）
     * @return 提供商列表
     */
    @GetMapping("/provider/available")
    Result<List<AvailableProviderResponse>> getAvailableProviders(
            @RequestParam("providerType") String providerType);

    /**
     * 获取模型提供商详情（含费用信息）
     *
     * @param providerId 提供商 ID
     * @return 提供商详情
     */
    @GetMapping("/provider/detail")
    Result<AvailableProviderResponse> getProviderDetail(
            @RequestParam("providerId") String providerId);

    /**
     * 预估积分消耗
     * 根据模型提供商的定价规则和用户参数动态计算积分
     *
     * @param providerId 提供商 ID
     * @param params     用户输入参数
     * @return 积分预估结果（含 finalCost, baseCost, discountRate, breakdown 等）
     */
    @PostMapping("/provider/estimate-cost")
    Result<Map<String, Object>> estimateCost(
            @RequestParam("providerId") String providerId,
            @RequestBody(required = false) Map<String, Object> params);

    /**
     * 查询执行状态（非阻塞）
     * 用于 POLLING 模式下查询任务状态
     *
     * @param executionId 执行 ID
     * @return 执行状态
     */
    @GetMapping("/execution/{executionId}/status")
    Result<ExecutionStatusResponse> getExecutionStatus(
            @PathVariable("executionId") String executionId);

    /**
     * 获取执行结果（阻塞等待）
     * 用于 POLLING 模式下获取最终结果
     *
     * @param executionId 执行 ID
     * @param timeout     超时时间（秒）
     * @return 执行结果
     */
    @GetMapping("/execution/{executionId}/result")
    Result<ProviderExecutionResult> getExecutionResult(
            @PathVariable("executionId") String executionId,
            @RequestParam(value = "timeout", defaultValue = "300") long timeout);

    /**
     * 取消执行
     *
     * @param executionId    执行 ID
     * @param pluginId       插件 ID
     * @param externalTaskId 外部任务 ID
     * @param userId         用户 ID
     * @return 取消结果
     */
    @PostMapping("/execution/{executionId}/cancel")
    Result<Void> cancelExecution(
            @PathVariable("executionId") String executionId,
            @RequestParam(value = "pluginId", required = false) String pluginId,
            @RequestParam(value = "externalTaskId", required = false) String externalTaskId,
            @RequestParam(value = "userId", required = false) String userId);
}
