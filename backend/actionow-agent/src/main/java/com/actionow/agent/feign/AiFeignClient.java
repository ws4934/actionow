package com.actionow.agent.feign;

import com.actionow.agent.feign.dto.AvailableProviderResponse;
import com.actionow.agent.feign.dto.LlmCredentialsResponse;
import com.actionow.agent.feign.dto.LlmProviderResponse;
import com.actionow.agent.feign.dto.ProviderExecutionResultResponse;
import com.actionow.common.api.ai.ProviderExecuteRequest;
import com.actionow.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * AI 服务 Feign 客户端
 * Agent 服务调用 AI 服务执行 AI 工具和获取 LLM 配置
 *
 * 使用独立的断路器和超时配置，适应 AI 服务的长响应时间特性
 *
 * @author Actionow
 */
@FeignClient(name = "actionow-ai", path = "/internal/ai",
        fallbackFactory = AiFeignClientFallbackFactory.class,
        configuration = AiFeignConfiguration.class)
public interface AiFeignClient {

    // ==================== Provider 执行 API ====================

    /**
     * 执行 AI Provider
     *
     * @param request 执行请求
     * @return 执行结果
     */
    @PostMapping("/provider/execute")
    Result<ProviderExecutionResultResponse> executeProvider(@RequestBody ProviderExecuteRequest request);

    /**
     * 获取可用 AI Provider 列表
     *
     * @param providerType Provider 类型（IMAGE/VIDEO/AUDIO/TEXT）
     * @return Provider 列表
     */
    @GetMapping("/provider/available")
    Result<List<AvailableProviderResponse>> getAvailableProviders(
            @RequestParam("providerType") String providerType);

    /**
     * 获取 Provider 详情
     *
     * @param providerId Provider ID
     * @return Provider 详情
     */
    @GetMapping("/provider/detail")
    Result<AvailableProviderResponse> getProviderDetail(
            @RequestParam("providerId") String providerId);

    /**
     * 查询执行状态
     *
     * @param executionId 执行 ID
     * @return 执行状态
     */
    @GetMapping("/execution/{executionId}/status")
    Result<ProviderExecutionResultResponse> getExecutionStatus(
            @PathVariable("executionId") String executionId);

    /**
     * 取消执行
     *
     * @param executionId 执行 ID
     * @return 取消结果
     */
    @PostMapping("/execution/{executionId}/cancel")
    Result<Void> cancelExecution(
            @PathVariable("executionId") String executionId);

    // ==================== LLM Provider API ====================

    /**
     * 根据 ID 获取 LLM Provider 配置
     *
     * @param id LLM Provider ID
     * @return LLM Provider 配置
     */
    @GetMapping("/llm-provider/{id}")
    Result<LlmProviderResponse> getLlmProviderById(@PathVariable("id") String id);

    /**
     * 获取 LLM Provider 凭证（含解析后的 API Key）
     *
     * @param id LLM Provider ID
     * @return LLM 凭证信息
     */
    @GetMapping("/llm-provider/{id}/credentials")
    Result<LlmCredentialsResponse> getLlmCredentials(@PathVariable("id") String id);

    /**
     * 获取所有启用的 LLM Provider
     *
     * @return LLM Provider 列表
     */
    @GetMapping("/llm-provider/enabled")
    Result<List<LlmProviderResponse>> getEnabledLlmProviders();

    /**
     * 根据提供商获取 LLM Provider 列表
     *
     * @param provider 提供商名称
     * @return LLM Provider 列表
     */
    @GetMapping("/llm-provider/by-provider")
    Result<List<LlmProviderResponse>> getLlmProvidersByProvider(
            @RequestParam("provider") String provider);
}
