package com.actionow.agent.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.agent.entity.AgentMissionTrace;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * Mission 轨迹 Mapper。
 */
@Mapper
public interface AgentMissionTraceMapper extends BaseMapper<AgentMissionTrace> {

    default List<AgentMissionTrace> selectByMissionId(String missionId) {
        return selectList(new LambdaQueryWrapper<AgentMissionTrace>()
                .eq(AgentMissionTrace::getMissionId, missionId)
                .orderByAsc(AgentMissionTrace::getCreatedAt));
    }
}
