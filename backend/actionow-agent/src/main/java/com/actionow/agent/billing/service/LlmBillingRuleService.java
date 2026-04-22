package com.actionow.agent.billing.service;

import com.actionow.agent.billing.dto.LlmBillingRuleRequest;
import com.actionow.agent.billing.dto.LlmBillingRuleResponse;
import com.actionow.agent.billing.entity.LlmBillingRule;
import com.actionow.common.core.result.PageResult;

import java.util.List;
import java.util.Optional;

/**
 * LLM 计费规则服务接口
 *
 * @author Actionow
 */
public interface LlmBillingRuleService {

    /**
     * 获取 LLM Provider 当前有效的计费规则
     *
     * @param llmProviderId LLM Provider ID
     * @return 有效的计费规则
     */
    Optional<LlmBillingRuleResponse> getEffectiveRule(String llmProviderId);

    /**
     * 获取 LLM Provider 当前有效的计费规则实体
     *
     * @param llmProviderId LLM Provider ID
     * @return 有效的计费规则实体
     */
    Optional<LlmBillingRule> getEffectiveRuleEntity(String llmProviderId);

    /**
     * 获取所有当前有效的计费规则
     *
     * @return 有效的计费规则列表
     */
    List<LlmBillingRuleResponse> getAllEffectiveRules();

    /**
     * 分页获取所有计费规则
     *
     * @param current       当前页码
     * @param size          每页大小
     * @param llmProviderId LLM Provider ID（可选）
     * @param enabled       是否启用（可选）
     * @return 分页结果
     */
    PageResult<LlmBillingRuleResponse> findPage(Long current, Long size, String llmProviderId, Boolean enabled);

    /**
     * 获取计费规则详情
     *
     * @param id 规则 ID
     * @return 计费规则
     */
    LlmBillingRuleResponse getById(String id);

    /**
     * 获取指定 LLM Provider 的所有计费规则
     *
     * @param llmProviderId LLM Provider ID
     * @return 计费规则列表
     */
    List<LlmBillingRuleResponse> getByProviderId(String llmProviderId);

    /**
     * 创建计费规则
     *
     * @param request 创建请求
     * @return 创建的规则
     */
    LlmBillingRuleResponse create(LlmBillingRuleRequest request);

    /**
     * 更新计费规则
     *
     * @param id      规则 ID
     * @param request 更新请求
     * @return 更新后的规则
     */
    LlmBillingRuleResponse update(String id, LlmBillingRuleRequest request);

    /**
     * 删除计费规则
     *
     * @param id 规则 ID
     */
    void delete(String id);

    /**
     * 启用/禁用计费规则
     *
     * @param id      规则 ID
     * @param enabled 是否启用
     */
    void toggleEnabled(String id, Boolean enabled);

    /**
     * 刷新缓存
     *
     * @param llmProviderId LLM Provider ID
     */
    void refreshCache(String llmProviderId);

    /**
     * 刷新所有缓存
     */
    void refreshAllCaches();
}
