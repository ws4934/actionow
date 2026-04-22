package com.actionow.ai.llm.service;

import com.actionow.ai.llm.dto.LlmCredentialsResponse;
import com.actionow.ai.llm.dto.LlmProviderRequest;
import com.actionow.ai.llm.dto.LlmProviderResponse;
import com.actionow.ai.llm.dto.LlmTestRequest;
import com.actionow.ai.llm.dto.LlmTestResponse;
import com.actionow.ai.llm.entity.LlmProvider;
import com.actionow.common.core.result.PageResult;

import java.util.List;
import java.util.Optional;

/**
 * LLM Provider 服务接口
 *
 * @author Actionow
 */
public interface LlmProviderService {

    /**
     * 创建 LLM Provider
     *
     * @param request 创建请求
     * @return 创建的 Provider
     */
    LlmProviderResponse create(LlmProviderRequest request);

    /**
     * 更新 LLM Provider
     *
     * @param id      Provider ID
     * @param request 更新请求
     * @return 更新后的 Provider
     */
    LlmProviderResponse update(String id, LlmProviderRequest request);

    /**
     * 根据 ID 查询
     *
     * @param id Provider ID
     * @return Provider
     */
    Optional<LlmProviderResponse> findById(String id);

    /**
     * 根据 ID 查询（必须存在）
     *
     * @param id Provider ID
     * @return Provider
     * @throws IllegalArgumentException 如果不存在
     */
    LlmProviderResponse getById(String id);

    /**
     * 根据 ID 查询实体（必须存在）
     *
     * @param id Provider ID
     * @return Provider 实体
     */
    LlmProvider getEntityById(String id);

    /**
     * 删除 LLM Provider
     *
     * @param id Provider ID
     */
    void delete(String id);

    /**
     * 查询所有启用的 Provider
     *
     * @return Provider 列表
     */
    List<LlmProviderResponse> findAllEnabled();

    /**
     * 分页查询
     *
     * @param current   当前页码
     * @param size      每页大小
     * @param provider  厂商（可选）
     * @param enabled   是否启用（可选）
     * @param modelName 名称模糊搜索（可选）
     * @return 分页结果
     */
    PageResult<LlmProviderResponse> findPage(Long current, Long size, String provider, Boolean enabled, String modelName);

    /**
     * 根据厂商查询启用的 Provider
     *
     * @param provider 厂商
     * @return Provider 列表
     */
    List<LlmProviderResponse> findEnabledByProvider(String provider);

    /**
     * 根据厂商和模型ID查询
     *
     * @param provider 厂商
     * @param modelId  模型ID
     * @return Provider
     */
    Optional<LlmProviderResponse> findByProviderAndModelId(String provider, String modelId);

    /**
     * 启用 Provider
     *
     * @param id Provider ID
     */
    void enable(String id);

    /**
     * 禁用 Provider
     *
     * @param id Provider ID
     */
    void disable(String id);

    /**
     * 切换启用状态
     *
     * @param id      Provider ID
     * @param enabled 是否启用
     */
    void toggleEnabled(String id, Boolean enabled);

    /**
     * 刷新缓存
     */
    void refreshCache();

    /**
     * 刷新指定 Provider 缓存
     *
     * @param id Provider ID
     */
    void refreshCache(String id);

    /**
     * 获取 LLM 凭证（包含解析后的 API Key）
     * 供 Agent 模块创建 LLM 客户端使用
     *
     * @param id Provider ID
     * @return 凭证信息（含 API Key）
     */
    Optional<LlmCredentialsResponse> getCredentials(String id);

    /**
     * 测试 LLM 可用性
     * 发送测试消息验证模型是否正常工作
     *
     * @param id      Provider ID
     * @param request 测试请求（可选）
     * @return 测试结果
     */
    LlmTestResponse testLlm(String id, LlmTestRequest request);

    /**
     * 批量测试所有启用的 LLM
     *
     * @param request 测试请求（可选）
     * @return 测试结果列表
     */
    List<LlmTestResponse> testAllEnabled(LlmTestRequest request);
}
