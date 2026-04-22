package com.actionow.agent.billing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.agent.billing.entity.LlmBillingRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * LLM 计费规则 Mapper
 *
 * @author Actionow
 */
@Mapper
public interface LlmBillingRuleMapper extends BaseMapper<LlmBillingRule> {

    /**
     * 查询 LLM Provider 当前有效的计费规则
     */
    @Select("SELECT * FROM t_llm_billing_rule WHERE llm_provider_id = #{llmProviderId} " +
            "AND enabled = TRUE AND deleted = 0 " +
            "AND effective_from <= CURRENT_TIMESTAMP " +
            "AND (effective_to IS NULL OR effective_to > CURRENT_TIMESTAMP) " +
            "ORDER BY priority DESC, effective_from DESC LIMIT 1")
    LlmBillingRule selectEffectiveRule(@Param("llmProviderId") String llmProviderId);

    /**
     * 查询 LLM Provider 的所有计费规则
     */
    @Select("SELECT * FROM t_llm_billing_rule WHERE llm_provider_id = #{llmProviderId} " +
            "AND deleted = 0 ORDER BY effective_from DESC")
    List<LlmBillingRule> selectByProviderId(@Param("llmProviderId") String llmProviderId);

    /**
     * 查询所有启用的计费规则
     */
    @Select("SELECT * FROM t_llm_billing_rule WHERE enabled = TRUE AND deleted = 0 " +
            "ORDER BY llm_provider_id, priority DESC")
    List<LlmBillingRule> selectAllEnabled();

    /**
     * 查询当前有效的所有计费规则
     */
    @Select("SELECT * FROM t_llm_billing_rule WHERE enabled = TRUE AND deleted = 0 " +
            "AND effective_from <= CURRENT_TIMESTAMP " +
            "AND (effective_to IS NULL OR effective_to > CURRENT_TIMESTAMP) " +
            "ORDER BY llm_provider_id, priority DESC")
    List<LlmBillingRule> selectAllEffective();

    /**
     * 分页查询
     */
    @Select("<script>" +
            "SELECT * FROM t_llm_billing_rule WHERE deleted = 0 " +
            "<if test='llmProviderId != null'> AND llm_provider_id = #{llmProviderId} </if>" +
            "<if test='enabled != null'> AND enabled = #{enabled} </if>" +
            "ORDER BY llm_provider_id, priority DESC, effective_from DESC" +
            "</script>")
    IPage<LlmBillingRule> selectPage(Page<LlmBillingRule> page,
                                     @Param("llmProviderId") String llmProviderId,
                                     @Param("enabled") Boolean enabled);
}
