package com.actionow.agent.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.agent.entity.AgentSessionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 会话 Mapper
 *
 * @author Actionow
 */
@Mapper
public interface AgentSessionMapper extends BaseMapper<AgentSessionEntity> {

    /**
     * 根据 standaloneEnabled 和 scriptId 查询用户会话
     * 通过 JOIN t_agent_config 表获取 standaloneEnabled 状态
     *
     * @param userId     用户 ID（必填）
     * @param standalone 是否独立 Agent 会话
     *                   true: 只查询独立 Agent 会话
     *                   false: 只查询非独立 Agent 会话（协调者会话）
     *                   null: 不筛选
     * @param scriptId   剧本 ID（可选，null 表示不筛选）
     * @param size       每页大小
     * @param offset     偏移量
     * @return 会话列表
     */
    List<AgentSessionEntity> selectByStandalone(@Param("userId") String userId,
                                                 @Param("standalone") Boolean standalone,
                                                 @Param("scriptId") String scriptId,
                                                 @Param("size") int size,
                                                 @Param("offset") int offset);

    /**
     * 统计会话数量（用于分页）
     */
    long countByStandalone(@Param("userId") String userId,
                           @Param("standalone") Boolean standalone,
                           @Param("scriptId") String scriptId);

    /**
     * 查询工作空间的活跃会话列表
     * 使用 LambdaQueryWrapper 确保 JacksonTypeHandler 正确处理 JSONB 字段
     */
    default List<AgentSessionEntity> selectActiveByWorkspace(String workspaceId, int limit) {
        return selectList(new LambdaQueryWrapper<AgentSessionEntity>()
                .eq(AgentSessionEntity::getWorkspaceId, workspaceId)
                .eq(AgentSessionEntity::getStatus, "active")
                .apply("COALESCE(extras->>'internalSession', 'false') <> 'true'")
                .orderByDesc(AgentSessionEntity::getLastActiveAt)
                .last("LIMIT " + limit));
    }

    /**
     * 查询用户的会话列表（包含活跃和归档）
     */
    default List<AgentSessionEntity> selectByUserId(String userId, int limit) {
        return selectList(new LambdaQueryWrapper<AgentSessionEntity>()
                .eq(AgentSessionEntity::getUserId, userId)
                .apply("COALESCE(extras->>'internalSession', 'false') <> 'true'")
                .orderByDesc(AgentSessionEntity::getLastActiveAt)
                .last("LIMIT " + limit));
    }

    /**
     * 查询用户的活跃会话列表
     */
    default List<AgentSessionEntity> selectActiveByUserId(String userId, int limit) {
        return selectList(new LambdaQueryWrapper<AgentSessionEntity>()
                .eq(AgentSessionEntity::getUserId, userId)
                .eq(AgentSessionEntity::getStatus, "active")
                .apply("COALESCE(extras->>'internalSession', 'false') <> 'true'")
                .orderByDesc(AgentSessionEntity::getLastActiveAt)
                .last("LIMIT " + limit));
    }

    /**
     * 查询用户的归档会话列表
     */
    default List<AgentSessionEntity> selectArchivedByUserId(String userId, int limit) {
        return selectList(new LambdaQueryWrapper<AgentSessionEntity>()
                .eq(AgentSessionEntity::getUserId, userId)
                .eq(AgentSessionEntity::getStatus, "archived")
                .apply("COALESCE(extras->>'internalSession', 'false') <> 'true'")
                .orderByDesc(AgentSessionEntity::getArchivedAt)
                .last("LIMIT " + limit));
    }

    /**
     * 统计用户活跃会话数
     */
    @Select("SELECT COUNT(*) FROM t_agent_session WHERE user_id = #{userId} AND status = 'active' " +
            "AND deleted = 0 AND COALESCE(extras->>'internalSession', 'false') <> 'true'")
    int countActiveByUserId(@Param("userId") String userId);

    /**
     * 统计用户在指定作用域（剧本）下的活跃会话数
     */
    @Select("SELECT COUNT(*) FROM t_agent_session WHERE user_id = #{userId} " +
            "AND scope_context->>'scriptId' = #{scriptId} AND status = 'active' AND deleted = 0 " +
            "AND COALESCE(extras->>'internalSession', 'false') <> 'true'")
    int countActiveByUserAndScript(@Param("userId") String userId, @Param("scriptId") String scriptId);

    /**
     * 统计用户全局作用域的活跃会话数
     */
    @Select("SELECT COUNT(*) FROM t_agent_session WHERE user_id = #{userId} " +
            "AND scope_context->>'scriptId' IS NULL AND status = 'active' AND deleted = 0 " +
            "AND COALESCE(extras->>'internalSession', 'false') <> 'true'")
    int countActiveGlobalByUser(@Param("userId") String userId);

    /**
     * 查询需要自动归档的空闲会话
     * 超过指定时间未活跃的活跃会话
     */
    @Select("SELECT * FROM t_agent_session WHERE status = 'active' AND deleted = 0 " +
            "AND last_active_at < #{threshold} " +
            "AND COALESCE(extras->>'internalSession', 'false') <> 'true' " +
            "ORDER BY last_active_at ASC LIMIT #{limit}")
    List<AgentSessionEntity> selectIdleSessionsForArchive(@Param("threshold") LocalDateTime threshold,
                                                           @Param("limit") int limit);

    /**
     * 批量归档空闲会话
     */
    @Update("UPDATE t_agent_session SET status = 'archived', archived_at = #{archivedAt}, " +
            "updated_at = #{archivedAt} WHERE status = 'active' AND deleted = 0 " +
            "AND last_active_at < #{threshold}")
    int archiveIdleSessions(@Param("threshold") LocalDateTime threshold,
                            @Param("archivedAt") LocalDateTime archivedAt);

    /**
     * 归档用户最旧的活跃会话（当超出活跃会话数限制时）
     */
    @Update("UPDATE t_agent_session SET status = 'archived', archived_at = NOW(), updated_at = NOW() " +
            "WHERE id = (SELECT id FROM t_agent_session WHERE user_id = #{userId} " +
            "AND scope_context->>'scriptId' = #{scriptId} AND status = 'active' AND deleted = 0 " +
            "AND COALESCE(extras->>'internalSession', 'false') <> 'true' " +
            "ORDER BY last_active_at ASC LIMIT 1)")
    int archiveOldestSessionByScript(@Param("userId") String userId, @Param("scriptId") String scriptId);

    /**
     * 归档用户最旧的全局活跃会话
     */
    @Update("UPDATE t_agent_session SET status = 'archived', archived_at = NOW(), updated_at = NOW() " +
            "WHERE id = (SELECT id FROM t_agent_session WHERE user_id = #{userId} " +
            "AND scope_context->>'scriptId' IS NULL AND status = 'active' AND deleted = 0 " +
            "AND COALESCE(extras->>'internalSession', 'false') <> 'true' " +
            "ORDER BY last_active_at ASC LIMIT 1)")
    int archiveOldestGlobalSession(@Param("userId") String userId);

    /**
     * 查询需要物理删除的会话（软删除超过指定天数）
     */
    @Select("SELECT * FROM t_agent_session WHERE deleted = 1 AND deleted_at < #{threshold} LIMIT #{limit}")
    List<AgentSessionEntity> selectSessionsForPermanentDelete(@Param("threshold") LocalDateTime threshold,
                                                               @Param("limit") int limit);

    /**
     * 统计用户的会话总数（用于限制历史会话数量）
     */
    @Select("SELECT COUNT(*) FROM t_agent_session WHERE user_id = #{userId} AND deleted = 0 " +
            "AND COALESCE(extras->>'internalSession', 'false') <> 'true'")
    int countTotalByUserId(@Param("userId") String userId);

    /**
     * 原子递增消息计数并累加 token（单条 UPDATE，替代 N+1 selectById+updateById）
     * tokenDelta 为 null 时按 0 处理
     */
    @Update("UPDATE t_agent_session " +
            "SET message_count = message_count + 1, " +
            "    total_tokens  = COALESCE(total_tokens, 0) + COALESCE(#{tokenDelta}, 0), " +
            "    last_active_at = NOW(), " +
            "    updated_at    = NOW() " +
            "WHERE id = #{sessionId}")
    int incrementMessageStats(@Param("sessionId") String sessionId,
                              @Param("tokenDelta") Integer tokenDelta);

    /**
     * 原子累加 token（仅更新 token，不递增消息计数）
     */
    @Update("UPDATE t_agent_session " +
            "SET total_tokens = COALESCE(total_tokens, 0) + #{tokenDelta}, " +
            "    updated_at   = NOW() " +
            "WHERE id = #{sessionId} AND #{tokenDelta} > 0")
    int addTokens(@Param("sessionId") String sessionId,
                  @Param("tokenDelta") int tokenDelta);

    /**
     * 标记 session 进入"正在生成"状态。skip-placeholder 路径下替代空 placeholder 行。
     * generating_since 置为 now，last_heartbeat_at 同步设置避免刚开始就显示 stale。
     */
    @Update("UPDATE t_agent_session " +
            "SET generating_since = NOW(), " +
            "    last_heartbeat_at = NOW(), " +
            "    updated_at = NOW() " +
            "WHERE id = #{sessionId}")
    int markGenerating(@Param("sessionId") String sessionId);

    /**
     * 清除 session 的"正在生成"标记。Teardown 所有终态分支（success / cancelled / error）
     * 都应调用；幂等 —— generating_since 已为 NULL 时也是一次无害的 UPDATE。
     */
    @Update("UPDATE t_agent_session " +
            "SET generating_since = NULL, " +
            "    last_heartbeat_at = NULL, " +
            "    updated_at = NOW() " +
            "WHERE id = #{sessionId}")
    int clearGenerating(@Param("sessionId") String sessionId);

    /**
     * 批量刷新 session 级心跳。心跳调度器每 tick 汇总本轮所有 in-flight session，
     * 一次 UPDATE ... WHERE id IN (...) 把写放大从 N 降到 1。
     */
    @Update("<script>" +
            "UPDATE t_agent_session SET last_heartbeat_at = NOW(), updated_at = NOW() " +
            "WHERE id IN " +
            "<foreach collection='sessionIds' item='sid' open='(' separator=',' close=')'>" +
            "#{sid}" +
            "</foreach>" +
            " AND generating_since IS NOT NULL" +
            "</script>")
    int touchSessionHeartbeatBatch(@Param("sessionIds") List<String> sessionIds);
}
