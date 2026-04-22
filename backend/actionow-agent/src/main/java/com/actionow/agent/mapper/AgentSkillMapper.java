package com.actionow.agent.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.agent.entity.AgentSkillEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * Agent Skill Mapper
 * 使用 LambdaQueryWrapper 确保 JacksonTypeHandler 正确处理 JSONB 字段。
 *
 * @author Actionow
 */
@Mapper
public interface AgentSkillMapper extends BaseMapper<AgentSkillEntity> {

    /**
     * 查询所有启用且未删除的 Skill，按名称升序排列
     */
    default List<AgentSkillEntity> selectAllEnabled() {
        return selectList(new LambdaQueryWrapper<AgentSkillEntity>()
                .eq(AgentSkillEntity::getEnabled, true)
                .eq(AgentSkillEntity::getDeleted, 0)
                .orderByAsc(AgentSkillEntity::getName));
    }

    /**
     * 按名称查询启用的 Skill
     */
    default AgentSkillEntity selectByName(String name) {
        return selectOne(new LambdaQueryWrapper<AgentSkillEntity>()
                .eq(AgentSkillEntity::getName, name)
                .eq(AgentSkillEntity::getDeleted, 0)
                .last("LIMIT 1"));
    }

    /**
     * 查询指定工作空间的 WORKSPACE 级 Skill（启用且未删除）
     */
    default List<AgentSkillEntity> selectEnabledByWorkspace(String workspaceId) {
        return selectList(new LambdaQueryWrapper<AgentSkillEntity>()
                .eq(AgentSkillEntity::getEnabled, true)
                .eq(AgentSkillEntity::getDeleted, 0)
                .eq(AgentSkillEntity::getWorkspaceId, workspaceId)
                .orderByAsc(AgentSkillEntity::getName));
    }

    /**
     * 按名称 + workspaceId 查询 WORKSPACE 级 Skill（未删除）
     */
    default AgentSkillEntity selectByNameAndWorkspace(String name, String workspaceId) {
        return selectOne(new LambdaQueryWrapper<AgentSkillEntity>()
                .eq(AgentSkillEntity::getName, name)
                .eq(AgentSkillEntity::getScope, "WORKSPACE")
                .eq(AgentSkillEntity::getWorkspaceId, workspaceId)
                .eq(AgentSkillEntity::getDeleted, 0)
                .last("LIMIT 1"));
    }

    /**
     * 按名称查询对某 workspace 可见的 Skill（优先 WORKSPACE，回退 SYSTEM）
     */
    default AgentSkillEntity selectVisibleByName(String name, String workspaceId) {
        AgentSkillEntity ws = selectByNameAndWorkspace(name, workspaceId);
        return ws != null ? ws : selectByName(name);
    }
}
