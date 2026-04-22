package com.actionow.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.user.entity.AuthSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 认证会话 Mapper
 */
@Mapper
public interface AuthSessionMapper extends BaseMapper<AuthSession> {

    @Update("""
            UPDATE t_auth_session
            SET status = 'REVOKED',
                revoked_at = #{revokedAt},
                updated_at = #{revokedAt}
            WHERE user_id = #{userId}
              AND deleted = 0
              AND status = 'ACTIVE'
            """)
    int revokeActiveSessionsByUserId(@Param("userId") String userId, @Param("revokedAt") LocalDateTime revokedAt);

    @Update("""
            UPDATE t_auth_session
            SET status = 'REVOKED',
                revoked_at = #{revokedAt},
                updated_at = #{revokedAt}
            WHERE id = #{sessionId}
              AND deleted = 0
              AND status = 'ACTIVE'
            """)
    int revokeSessionById(@Param("sessionId") String sessionId, @Param("revokedAt") LocalDateTime revokedAt);
}

