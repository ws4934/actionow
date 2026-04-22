package com.actionow.agent.config.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.agent.config.entity.AgentConfigEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * Agent 配置 Mapper
 * 使用 LambdaQueryWrapper 确保 JacksonTypeHandler 正确处理 JSONB 字段
 * （includes, aiProviderTypes, defaultSkillNames, allowedSkillNames, subAgentTypes, tags）。
 * 注意: @Select 原生 SQL 会绕过 autoResultMap，导致 JSONB 列返回 null。
 *
 * @author Actionow
 */
@Mapper
public interface AgentConfigMapper extends BaseMapper<AgentConfigEntity> {

    /**
     * 根据 Agent 类型查询
     */
    default AgentConfigEntity selectByAgentType(String agentType) {
        return selectOne(new LambdaQueryWrapper<AgentConfigEntity>()
                .eq(AgentConfigEntity::getAgentType, agentType));
    }

    /**
     * 查询所有启用的配置
     */
    default List<AgentConfigEntity> selectAllEnabled() {
        return selectList(new LambdaQueryWrapper<AgentConfigEntity>()
                .eq(AgentConfigEntity::getEnabled, true)
                .orderByAsc(AgentConfigEntity::getAgentType));
    }

    /**
     * 根据 LLM Provider ID 查询
     */
    default List<AgentConfigEntity> selectByLlmProviderId(String llmProviderId) {
        return selectList(new LambdaQueryWrapper<AgentConfigEntity>()
                .eq(AgentConfigEntity::getLlmProviderId, llmProviderId));
    }

    /**
     * 分页查询（支持动态条件）
     */
    default IPage<AgentConfigEntity> selectPage(Page<AgentConfigEntity> page,
                                                 String agentType, Boolean enabled, String llmProviderId) {
        LambdaQueryWrapper<AgentConfigEntity> wrapper = new LambdaQueryWrapper<AgentConfigEntity>()
                .eq(agentType != null && !agentType.isEmpty(), AgentConfigEntity::getAgentType, agentType)
                .eq(enabled != null, AgentConfigEntity::getEnabled, enabled)
                .eq(llmProviderId != null, AgentConfigEntity::getLlmProviderId, llmProviderId)
                .orderByAsc(AgentConfigEntity::getAgentType);
        return selectPage(page, wrapper);
    }
}
