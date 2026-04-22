package com.actionow.agent.billing.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.agent.billing.entity.AgentBillingSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 计费会话 Mapper
 * 使用 LambdaQueryWrapper 确保 JacksonTypeHandler 正确处理 JSONB 字段（pricingSnapshot）。
 * 注意: AgentBillingSession 无 @TableLogic 和 deleted 字段。
 *
 * @author Actionow
 */
@Mapper
public interface AgentBillingSessionMapper extends BaseMapper<AgentBillingSession> {

    /**
     * 根据会话 ID 查询计费会话
     * 优先返回 ACTIVE 状态的会话，如果没有则返回最新的会话
     */
    default AgentBillingSession selectByConversationId(String conversationId) {
        return selectOne(new LambdaQueryWrapper<AgentBillingSession>()
                .eq(AgentBillingSession::getConversationId, conversationId)
                .last("ORDER BY CASE status WHEN 'ACTIVE' THEN 0 WHEN 'SETTLING' THEN 1 ELSE 2 END, created_at DESC LIMIT 1"));
    }

    /**
     * 根据会话 ID 查询 ACTIVE 状态的计费会话
     */
    default AgentBillingSession selectActiveByConversationId(String conversationId) {
        return selectOne(new LambdaQueryWrapper<AgentBillingSession>()
                .eq(AgentBillingSession::getConversationId, conversationId)
                .eq(AgentBillingSession::getStatus, "ACTIVE")
                .last("LIMIT 1"));
    }

    /**
     * 查询活跃的计费会话（用于定时结算）
     */
    default List<AgentBillingSession> selectIdleSessions(LocalDateTime idleThreshold, int limit) {
        return selectList(new LambdaQueryWrapper<AgentBillingSession>()
                .eq(AgentBillingSession::getStatus, "ACTIVE")
                .lt(AgentBillingSession::getLastActivityAt, idleThreshold)
                .orderByAsc(AgentBillingSession::getLastActivityAt)
                .last("LIMIT " + limit));
    }

    /**
     * 查询失败的计费会话（用于补偿重试）
     */
    default List<AgentBillingSession> selectFailedSessions(int limit) {
        return selectList(new LambdaQueryWrapper<AgentBillingSession>()
                .eq(AgentBillingSession::getStatus, "FAILED")
                .orderByAsc(AgentBillingSession::getUpdatedAt)
                .last("LIMIT " + limit));
    }

    /**
     * 查询用户的计费会话列表
     */
    default List<AgentBillingSession> selectByUserId(String userId, int limit) {
        return selectList(new LambdaQueryWrapper<AgentBillingSession>()
                .eq(AgentBillingSession::getUserId, userId)
                .orderByDesc(AgentBillingSession::getCreatedAt)
                .last("LIMIT " + limit));
    }

    /**
     * 查询工作空间的计费会话列表
     */
    default List<AgentBillingSession> selectByWorkspaceId(String workspaceId, int limit) {
        return selectList(new LambdaQueryWrapper<AgentBillingSession>()
                .eq(AgentBillingSession::getWorkspaceId, workspaceId)
                .orderByDesc(AgentBillingSession::getCreatedAt)
                .last("LIMIT " + limit));
    }

    /**
     * 更新 Token 消费统计
     */
    @Update("UPDATE t_agent_billing_session SET " +
            "total_input_tokens = total_input_tokens + #{inputTokens}, " +
            "total_output_tokens = total_output_tokens + #{outputTokens}, " +
            "total_thought_tokens = total_thought_tokens + #{thoughtTokens}, " +
            "total_cached_tokens = total_cached_tokens + #{cachedTokens}, " +
            "llm_cost = llm_cost + #{cost}, " +
            "total_cost = llm_cost + ai_tool_cost + #{cost}, " +
            "last_activity_at = #{now}, " +
            "updated_at = #{now} " +
            "WHERE id = #{sessionId}")
    int updateTokenUsage(@Param("sessionId") String sessionId,
                         @Param("inputTokens") int inputTokens,
                         @Param("outputTokens") int outputTokens,
                         @Param("thoughtTokens") int thoughtTokens,
                         @Param("cachedTokens") int cachedTokens,
                         @Param("cost") long cost,
                         @Param("now") LocalDateTime now);

    /**
     * 更新 AI 工具消费统计
     */
    @Update("UPDATE t_agent_billing_session SET " +
            "ai_tool_calls = ai_tool_calls + 1, " +
            "ai_tool_cost = ai_tool_cost + #{cost}, " +
            "total_cost = llm_cost + ai_tool_cost + #{cost}, " +
            "last_activity_at = #{now}, " +
            "updated_at = #{now} " +
            "WHERE id = #{sessionId}")
    int updateAiToolUsage(@Param("sessionId") String sessionId,
                          @Param("cost") long cost,
                          @Param("now") LocalDateTime now);

    /**
     * 更新冻结金额
     */
    @Update("UPDATE t_agent_billing_session SET " +
            "frozen_amount = frozen_amount + #{amount}, " +
            "updated_at = #{now} " +
            "WHERE id = #{sessionId}")
    int updateFrozenAmount(@Param("sessionId") String sessionId,
                           @Param("amount") long amount,
                           @Param("now") LocalDateTime now);

    /**
     * 更新状态为结算中
     */
    @Update("UPDATE t_agent_billing_session SET " +
            "status = 'SETTLING', " +
            "updated_at = #{now} " +
            "WHERE id = #{sessionId} AND status = 'ACTIVE'")
    int updateStatusToSettling(@Param("sessionId") String sessionId,
                               @Param("now") LocalDateTime now);

    /**
     * 更新状态为已结算
     */
    @Update("UPDATE t_agent_billing_session SET " +
            "status = 'SETTLED', " +
            "settled_amount = #{settledAmount}, " +
            "settled_at = #{now}, " +
            "updated_at = #{now} " +
            "WHERE id = #{sessionId}")
    int updateStatusToSettled(@Param("sessionId") String sessionId,
                              @Param("settledAmount") long settledAmount,
                              @Param("now") LocalDateTime now);

    /**
     * 更新状态为失败
     */
    @Update("UPDATE t_agent_billing_session SET " +
            "status = 'FAILED', " +
            "settle_error = #{error}, " +
            "updated_at = #{now} " +
            "WHERE id = #{sessionId}")
    int updateStatusToFailed(@Param("sessionId") String sessionId,
                             @Param("error") String error,
                             @Param("now") LocalDateTime now);

    /**
     * 更新结算错误信息（不改变状态）
     */
    @Update("UPDATE t_agent_billing_session SET " +
            "settle_error = #{error}, " +
            "updated_at = #{now} " +
            "WHERE id = #{sessionId}")
    int updateSettleError(@Param("sessionId") String sessionId,
                          @Param("error") String error,
                          @Param("now") LocalDateTime now);

    /**
     * 统计用户当天的 AI 工具调用次数
     */
    @Select("SELECT COALESCE(SUM(ai_tool_calls), 0) FROM t_agent_billing_session " +
            "WHERE user_id = #{userId} " +
            "AND DATE(created_at) = CURRENT_DATE")
    int countTodayAiToolCalls(@Param("userId") String userId);
}
