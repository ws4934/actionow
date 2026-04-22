package com.actionow.agent.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.agent.entity.AgentMissionTask;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * Mission 异步任务 Mapper。
 */
@Mapper
public interface AgentMissionTaskMapper extends BaseMapper<AgentMissionTask> {

    default AgentMissionTask selectByExternalTaskId(String externalTaskId) {
        return selectOne(new LambdaQueryWrapper<AgentMissionTask>()
                .eq(AgentMissionTask::getExternalTaskId, externalTaskId)
                .eq(AgentMissionTask::getDeleted, 0)
                .last("LIMIT 1"));
    }

    default AgentMissionTask selectByBatchJobId(String batchJobId) {
        return selectOne(new LambdaQueryWrapper<AgentMissionTask>()
                .eq(AgentMissionTask::getBatchJobId, batchJobId)
                .eq(AgentMissionTask::getDeleted, 0)
                .last("LIMIT 1"));
    }

    default List<AgentMissionTask> selectByMissionId(String missionId) {
        return selectList(new LambdaQueryWrapper<AgentMissionTask>()
                .eq(AgentMissionTask::getMissionId, missionId)
                .eq(AgentMissionTask::getDeleted, 0)
                .orderByAsc(AgentMissionTask::getCreatedAt));
    }

    default long countByMissionAndStatuses(String missionId, List<String> statuses) {
        return selectCount(new LambdaQueryWrapper<AgentMissionTask>()
                .eq(AgentMissionTask::getMissionId, missionId)
                .eq(AgentMissionTask::getDeleted, 0)
                .in(statuses != null && !statuses.isEmpty(), AgentMissionTask::getStatus, statuses));
    }
}
