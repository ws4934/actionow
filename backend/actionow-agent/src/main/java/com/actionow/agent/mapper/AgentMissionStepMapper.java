package com.actionow.agent.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.agent.entity.AgentMissionStep;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * Agent Mission Step Mapper
 *
 * @author Actionow
 */
@Mapper
public interface AgentMissionStepMapper extends BaseMapper<AgentMissionStep> {

    /**
     * 查询 Mission 的所有步骤（按步骤编号排序）
     */
    default List<AgentMissionStep> selectByMissionId(String missionId) {
        return selectList(new LambdaQueryWrapper<AgentMissionStep>()
                .eq(AgentMissionStep::getMissionId, missionId)
                .orderByAsc(AgentMissionStep::getStepNumber));
    }

    /**
     * 查询 Mission 最新的步骤
     */
    default AgentMissionStep selectLatestByMissionId(String missionId) {
        return selectOne(new LambdaQueryWrapper<AgentMissionStep>()
                .eq(AgentMissionStep::getMissionId, missionId)
                .orderByDesc(AgentMissionStep::getStepNumber)
                .last("LIMIT 1"));
    }

    /**
     * 统计 Mission 的步骤数
     */
    default long countByMissionId(String missionId) {
        return selectCount(new LambdaQueryWrapper<AgentMissionStep>()
                .eq(AgentMissionStep::getMissionId, missionId));
    }
}
