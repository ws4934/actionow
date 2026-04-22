package com.actionow.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.user.entity.RefreshTokenRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * Refresh Token轮换 Mapper
 */
@Mapper
public interface RefreshTokenMapper extends BaseMapper<RefreshTokenRecord> {

    @Update("""
            UPDATE t_refresh_token
            SET status = 'USED',
                used_at = #{usedAt},
                replaced_by_jti = #{replacedByJti},
                updated_at = #{usedAt}
            WHERE token_jti = #{tokenJti}
              AND deleted = 0
              AND status = 'ACTIVE'
            """)
    int markTokenUsed(@Param("tokenJti") String tokenJti,
                      @Param("replacedByJti") String replacedByJti,
                      @Param("usedAt") LocalDateTime usedAt);

    @Update("""
            UPDATE t_refresh_token
            SET status = 'REVOKED',
                revoked_at = #{revokedAt},
                reason = #{reason},
                updated_at = #{revokedAt}
            WHERE family_id = #{familyId}
              AND deleted = 0
              AND status <> 'REVOKED'
            """)
    int revokeFamily(@Param("familyId") String familyId,
                     @Param("reason") String reason,
                     @Param("revokedAt") LocalDateTime revokedAt);

    @Update("""
            UPDATE t_refresh_token
            SET reuse_detected = TRUE,
                status = 'REVOKED',
                revoked_at = #{revokedAt},
                reason = #{reason},
                updated_at = #{revokedAt}
            WHERE token_jti = #{tokenJti}
              AND deleted = 0
            """)
    int markReuseDetected(@Param("tokenJti") String tokenJti,
                          @Param("reason") String reason,
                          @Param("revokedAt") LocalDateTime revokedAt);
}

