package com.actionow.agent.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.agent.entity.AgentMissionEvent;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * Mission 事件 Mapper。
 */
@Mapper
public interface AgentMissionEventMapper extends BaseMapper<AgentMissionEvent> {

    default List<AgentMissionEvent> selectByMissionId(String missionId) {
        return selectList(new LambdaQueryWrapper<AgentMissionEvent>()
                .eq(AgentMissionEvent::getMissionId, missionId)
                .orderByAsc(AgentMissionEvent::getCreatedAt));
    }
}
