package com.actionow.agent.config.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.agent.config.entity.AgentConfigVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Agent 配置版本 Mapper
 * 使用 LambdaQueryWrapper 确保 JacksonTypeHandler 正确处理 JSONB 字段（includes）。
 *
 * @author Actionow
 */
@Mapper
public interface AgentConfigVersionMapper extends BaseMapper<AgentConfigVersion> {

    /**
     * 查询配置的所有版本
     */
    default List<AgentConfigVersion> selectByConfigId(String agentConfigId) {
        return selectList(new LambdaQueryWrapper<AgentConfigVersion>()
                .eq(AgentConfigVersion::getAgentConfigId, agentConfigId)
                .orderByDesc(AgentConfigVersion::getVersionNumber));
    }

    /**
     * 查询配置的指定版本
     */
    default AgentConfigVersion selectByConfigIdAndVersion(String agentConfigId, Integer versionNumber) {
        return selectOne(new LambdaQueryWrapper<AgentConfigVersion>()
                .eq(AgentConfigVersion::getAgentConfigId, agentConfigId)
                .eq(AgentConfigVersion::getVersionNumber, versionNumber));
    }

    /**
     * 查询配置的最新版本号
     */
    @Select("SELECT MAX(version_number) FROM t_agent_config_version WHERE agent_config_id = #{agentConfigId}")
    Integer selectMaxVersionNumber(@Param("agentConfigId") String agentConfigId);
}
