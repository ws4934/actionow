package com.actionow.agent.mapper;

import com.actionow.agent.entity.AgentAskHistory;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * HITL ask/answer 历史 Mapper。
 *
 * <p>走 MyBatis-Plus {@code selectList}，保留 JacksonTypeHandler 对 JSON 字段的反序列化。
 *
 * @author Actionow
 */
@Mapper
public interface AgentAskHistoryMapper extends BaseMapper<AgentAskHistory> {

    default List<AgentAskHistory> selectBySessionId(String sessionId) {
        return selectList(new LambdaQueryWrapper<AgentAskHistory>()
                .eq(AgentAskHistory::getSessionId, sessionId)
                .eq(AgentAskHistory::getDeleted, 0)
                .orderByAsc(AgentAskHistory::getCreatedAt));
    }

    default AgentAskHistory selectByAskId(String askId) {
        return selectOne(new LambdaQueryWrapper<AgentAskHistory>()
                .eq(AgentAskHistory::getAskId, askId)
                .eq(AgentAskHistory::getDeleted, 0));
    }

    /**
     * 取 session 当前 PENDING 的那一条 ask（理论上同一时刻只会有一条）。
     * 若上游异常留下多条脏 PENDING，按 {@code createdAt DESC} 返回最新的一条。
     */
    default AgentAskHistory selectLatestPendingBySession(String sessionId) {
        return selectList(new LambdaQueryWrapper<AgentAskHistory>()
                .eq(AgentAskHistory::getSessionId, sessionId)
                .eq(AgentAskHistory::getStatus, "PENDING")
                .eq(AgentAskHistory::getDeleted, 0)
                .orderByDesc(AgentAskHistory::getCreatedAt)
                .last("LIMIT 1"))
                .stream().findFirst().orElse(null);
    }
}
