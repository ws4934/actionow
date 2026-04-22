package com.actionow.common.mq.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox 消息数据访问层
 * <p>
 * 使用 JdbcTemplate 直接操作 t_outbox_message 表，
 * 避免对 MyBatis 的强依赖，保持 common-mq 模块的轻量级。
 *
 * @author Actionow
 */
public class OutboxMessageRepository {

    private static final Logger log = LoggerFactory.getLogger(OutboxMessageRepository.class);

    private final JdbcTemplate jdbcTemplate;

    private static final String TABLE = "t_outbox_message";

    public OutboxMessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 插入 outbox 消息（与业务操作在同一事务中执行）
     */
    public void insert(OutboxMessage message) {
        String sql = "INSERT INTO " + TABLE +
                " (id, exchange, routing_key, message_type, message_json, status, retry_count, max_retries, created_at) " +
                "VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)";

        jdbcTemplate.update(sql,
                message.getId(),
                message.getExchange(),
                message.getRoutingKey(),
                message.getMessageType(),
                message.getMessageJson(),
                message.getStatus(),
                message.getRetryCount(),
                message.getMaxRetries(),
                Timestamp.valueOf(message.getCreatedAt())
        );
    }

    /**
     * 查询待发送的消息
     * <p>
     * 使用 SELECT ... FOR UPDATE SKIP LOCKED 实现行级锁，
     * 支持多实例并发消费而不阻塞。
     *
     * @param limit 批量大小
     * @return 待发送消息列表
     */
    public List<OutboxMessage> findPendingMessages(int limit) {
        String sql = "SELECT * FROM " + TABLE +
                " WHERE status = ? " +
                "AND (next_retry_at IS NULL OR next_retry_at <= NOW()) " +
                "ORDER BY created_at ASC " +
                "LIMIT ? " +
                "FOR UPDATE SKIP LOCKED";

        return jdbcTemplate.query(sql, OUTBOX_ROW_MAPPER, OutboxMessage.STATUS_PENDING, limit);
    }

    /**
     * 标记消息为已发送
     */
    public int markSent(String messageId) {
        String sql = "UPDATE " + TABLE +
                " SET status = ?, sent_at = NOW() " +
                "WHERE id = ?";

        return jdbcTemplate.update(sql, OutboxMessage.STATUS_SENT, messageId);
    }

    /**
     * 标记消息发送失败，设置下次重试时间（指数退避）
     */
    public int markRetryFailed(String messageId, String error, LocalDateTime nextRetryAt) {
        String sql = "UPDATE " + TABLE +
                " SET retry_count = retry_count + 1, last_error = ?, next_retry_at = ?, " +
                "status = CASE WHEN retry_count + 1 >= max_retries THEN ? ELSE ? END " +
                "WHERE id = ?";

        return jdbcTemplate.update(sql,
                error,
                Timestamp.valueOf(nextRetryAt),
                OutboxMessage.STATUS_FAILED, OutboxMessage.STATUS_PENDING,
                messageId
        );
    }

    /**
     * 清理已发送的旧消息
     *
     * @param retentionHours 保留时长（小时）
     * @return 清理的记录数
     */
    public int cleanupSentMessages(int retentionHours) {
        String sql = "DELETE FROM " + TABLE +
                " WHERE status = ? AND sent_at < NOW() - INTERVAL '" + retentionHours + " hours'";

        return jdbcTemplate.update(sql, OutboxMessage.STATUS_SENT);
    }

    /**
     * 统计各状态的消息数
     */
    public int countByStatus(String status) {
        String sql = "SELECT COUNT(*) FROM " + TABLE + " WHERE status = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, status);
        return count != null ? count : 0;
    }

    // ==================== RowMapper ====================

    private static final RowMapper<OutboxMessage> OUTBOX_ROW_MAPPER = new RowMapper<>() {
        @Override
        public OutboxMessage mapRow(ResultSet rs, int rowNum) throws SQLException {
            OutboxMessage msg = new OutboxMessage();
            msg.setId(rs.getString("id"));
            msg.setExchange(rs.getString("exchange"));
            msg.setRoutingKey(rs.getString("routing_key"));
            msg.setMessageType(rs.getString("message_type"));
            msg.setMessageJson(rs.getString("message_json"));
            msg.setStatus(rs.getString("status"));
            msg.setRetryCount(rs.getInt("retry_count"));
            msg.setMaxRetries(rs.getInt("max_retries"));
            msg.setLastError(rs.getString("last_error"));

            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) msg.setCreatedAt(createdAt.toLocalDateTime());

            Timestamp sentAt = rs.getTimestamp("sent_at");
            if (sentAt != null) msg.setSentAt(sentAt.toLocalDateTime());

            Timestamp nextRetryAt = rs.getTimestamp("next_retry_at");
            if (nextRetryAt != null) msg.setNextRetryAt(nextRetryAt.toLocalDateTime());

            return msg;
        }
    };
}
