package com.actionow.agent.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.agent.entity.AgentMission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * Agent Mission Mapper
 *
 * @author Actionow
 */
@Mapper
public interface AgentMissionMapper extends BaseMapper<AgentMission> {

    /**
     * 按工作空间和状态查询 Mission 列表
     */
    default List<AgentMission> selectByWorkspaceAndStatus(String workspaceId, String status) {
        return selectList(new LambdaQueryWrapper<AgentMission>()
                .eq(AgentMission::getWorkspaceId, workspaceId)
                .eq(status != null, AgentMission::getStatus, status)
                .orderByDesc(AgentMission::getCreatedAt));
    }

    /**
     * 分页查询工作空间的 Mission 列表
     */
    default IPage<AgentMission> selectPageByWorkspace(Page<AgentMission> page, String workspaceId, String status) {
        return selectPage(page, new LambdaQueryWrapper<AgentMission>()
                .eq(AgentMission::getWorkspaceId, workspaceId)
                .eq(status != null, AgentMission::getStatus, status)
                .orderByDesc(AgentMission::getCreatedAt));
    }

    /**
     * 按运行时会话 ID 查询 Mission
     */
    default AgentMission selectByRuntimeSessionId(String runtimeSessionId) {
        return selectOne(new LambdaQueryWrapper<AgentMission>()
                .eq(AgentMission::getRuntimeSessionId, runtimeSessionId)
                .last("LIMIT 1"));
    }

    /**
     * 统计工作空间中活跃的 Mission 数量
     */
    default int countActiveByWorkspace(String workspaceId) {
        return Math.toIntExact(selectCount(new LambdaQueryWrapper<AgentMission>()
                .eq(AgentMission::getWorkspaceId, workspaceId)
                .in(AgentMission::getStatus, "CREATED", "EXECUTING", "WAITING")));
    }

    /**
     * 原子更新进度
     */
    @Update("UPDATE t_agent_mission SET progress = #{progress}, updated_at = now() WHERE id = #{id} AND deleted = 0")
    int updateProgress(@Param("id") String id, @Param("progress") int progress);
}
