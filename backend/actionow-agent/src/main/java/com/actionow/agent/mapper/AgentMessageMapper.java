package com.actionow.agent.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.agent.entity.AgentMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 消息 Mapper
 *
 * 重要：使用 MyBatis-Plus 的 selectList 方法而非 @Select 注解，
 * 以确保 @TableField(typeHandler) 配置的 JacksonTypeHandler 正确工作。
 * @Select 注解会绕过 autoResultMap，导致 JSON 字段无法正确反序列化。
 *
 * @author Actionow
 */
@Mapper
public interface AgentMessageMapper extends BaseMapper<AgentMessage> {

    /**
     * 查询会话的消息列表（按序号排序）
     * 使用 MyBatis-Plus 内置方法，确保 JacksonTypeHandler 正确工作
     */
    default List<AgentMessage> selectBySessionId(String sessionId) {
        return selectList(new LambdaQueryWrapper<AgentMessage>()
                .eq(AgentMessage::getSessionId, sessionId)
                .eq(AgentMessage::getDeleted, 0)
                .orderByAsc(AgentMessage::getSequence));
    }

    /**
     * 查询会话的最新消息
     * 使用 MyBatis-Plus 内置方法，确保 JacksonTypeHandler 正确工作
     */
    default List<AgentMessage> selectLatestBySessionId(String sessionId, int limit) {
        return selectList(new LambdaQueryWrapper<AgentMessage>()
                .eq(AgentMessage::getSessionId, sessionId)
                .eq(AgentMessage::getDeleted, 0)
                .orderByDesc(AgentMessage::getSequence)
                .last("LIMIT " + limit));
    }

    /**
     * 取 session 当前 generating 状态的 assistant 占位消息。
     * 正常流程下同一时刻只会有一条；若残留多条（历史脏数据），取 sequence 最大者。
     */
    default AgentMessage selectInFlightPlaceholder(String sessionId) {
        return selectList(new LambdaQueryWrapper<AgentMessage>()
                .eq(AgentMessage::getSessionId, sessionId)
                .eq(AgentMessage::getRole, "assistant")
                .eq(AgentMessage::getStatus, "generating")
                .eq(AgentMessage::getDeleted, 0)
                .orderByDesc(AgentMessage::getSequence)
                .last("LIMIT 1"))
                .stream().findFirst().orElse(null);
    }

    /**
     * 获取会话的最大序号
     */
    @Select("SELECT COALESCE(MAX(sequence), 0) FROM t_agent_message WHERE session_id = #{sessionId}")
    int getMaxSequence(@Param("sessionId") String sessionId);

    /**
     * 为会话获取事务级 PostgreSQL 咨询锁，防止同一 session 的并发消息写入产生重复 sequence。
     * <p>必须在 {@code @Transactional} 方法内调用；锁会在事务提交 / 回滚时自动释放。
     * <p>双参数版 {@code pg_advisory_xact_lock(int, int)}：第一个 int 是 session 维度哈希，第二个 int 是
     * 业务命名空间哈希，避免与其它功能偶然共享锁键。{@code hashtext} 返回 int，无需额外转换。
     * <p>返回 {@code Object}（实际是 PostgreSQL void 列，JDBC 映射为 null）；
     * MyBatis 不能映射结果行到 {@code void} 返回类型，调用方忽略返回值即可。
     */
    @Select("SELECT pg_advisory_xact_lock(hashtext(#{sessionId}), hashtext('agent_msg_seq'))")
    Object acquireSessionSeqLock(@Param("sessionId") String sessionId);

    /**
     * 统计会话消息数
     */
    @Select("SELECT COUNT(*) FROM t_agent_message WHERE session_id = #{sessionId} AND deleted = 0")
    int countBySessionId(@Param("sessionId") String sessionId);

    /**
     * 批量更新一组占位消息的 lastHeartbeatAt —— 心跳调度器每 tick 把本轮需要打 heartbeat
     * 的 messageIds 汇总为一次 {@code UPDATE ... WHERE id IN (?)}，避免 N 个 session 引发
     * N 次独立 UPDATE。
     *
     * @return 实际更新行数（MyBatis-Plus 返回值）
     */
    default int touchHeartbeatBatch(List<String> messageIds, LocalDateTime heartbeatAt) {
        if (messageIds == null || messageIds.isEmpty()) return 0;
        LambdaUpdateWrapper<AgentMessage> wrapper = new LambdaUpdateWrapper<>();
        wrapper.in(AgentMessage::getId, messageIds)
               .set(AgentMessage::getLastHeartbeatAt, heartbeatAt);
        return update(null, wrapper);
    }

    /**
     * 清理残留的 generating 状态消息
     * 将超时的 generating 消息标记为 failed
     *
     * @param threshold 超时阈值（早于此时间的 generating 消息将被标记为 failed）
     * @return 清理的消息数
     */
    @Update("UPDATE t_agent_message SET status = 'failed', " +
            "content = CASE WHEN content = '' THEN '生成中断' ELSE content END " +
            "WHERE status = 'generating' AND updated_at < #{threshold}")
    int cleanupStaleGeneratingMessages(@Param("threshold") LocalDateTime threshold);
}
