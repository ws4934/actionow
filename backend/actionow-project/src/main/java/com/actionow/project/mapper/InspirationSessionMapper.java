package com.actionow.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.project.entity.InspirationSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 灵感会话 Mapper。
 *
 * <p><b>已 deprecated</b>：随 {@link InspirationSession} 一同冻结。
 *
 * @author Actionow
 */
@Deprecated(since = "3.0", forRemoval = true)
@Mapper
public interface InspirationSessionMapper extends BaseMapper<InspirationSession> {

    /**
     * 原子递增记录数
     */
    @Update("UPDATE t_inspiration_session SET record_count = record_count + 1, " +
            "last_active_at = now(), updated_at = now() WHERE id = #{sessionId} AND deleted = 0")
    int incrementRecordCount(@Param("sessionId") String sessionId);

    /**
     * 原子递减记录数
     */
    @Update("UPDATE t_inspiration_session SET record_count = GREATEST(record_count - 1, 0), " +
            "updated_at = now() WHERE id = #{sessionId} AND deleted = 0")
    int decrementRecordCount(@Param("sessionId") String sessionId);

    /**
     * 原子累加积分消耗
     */
    @Update("UPDATE t_inspiration_session SET total_credits = total_credits + #{credits}, " +
            "updated_at = now() WHERE id = #{sessionId} AND deleted = 0")
    int addCredits(@Param("sessionId") String sessionId, @Param("credits") BigDecimal credits);
}
