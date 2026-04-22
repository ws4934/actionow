package com.actionow.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.user.entity.InvitationCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Map;

/**
 * 邀请码 Mapper
 *
 * @author Actionow
 */
@Mapper
public interface InvitationCodeMapper extends BaseMapper<InvitationCode> {

    /**
     * 根据邀请码查询
     */
    @Select("SELECT * FROM t_invitation_code WHERE code = #{code} AND deleted = 0")
    InvitationCode selectByCode(@Param("code") String code);

    /**
     * 检查邀请码是否存在
     */
    @Select("SELECT COUNT(*) FROM t_invitation_code WHERE code = #{code} AND deleted = 0")
    int countByCode(@Param("code") String code);

    /**
     * 根据用户ID查询有效的用户邀请码
     */
    @Select("SELECT * FROM t_invitation_code WHERE owner_id = #{ownerId} AND type = #{type} AND status = 'ACTIVE' AND deleted = 0 LIMIT 1")
    InvitationCode selectActiveByOwnerAndType(@Param("ownerId") String ownerId, @Param("type") String type);

    /**
     * 将用户的旧邀请码状态更新为已替换
     */
    @Update("UPDATE t_invitation_code SET status = 'REPLACED', updated_at = CURRENT_TIMESTAMP WHERE owner_id = #{ownerId} AND type = #{type} AND status = 'ACTIVE' AND deleted = 0")
    int updateStatusToReplacedByOwner(@Param("ownerId") String ownerId, @Param("type") String type);

    /**
     * 增加使用计数
     */
    @Update("UPDATE t_invitation_code SET used_count = used_count + 1, updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND deleted = 0")
    int incrementUsedCount(@Param("id") String id);

    /**
     * 更新邀请码状态
     */
    @Update("UPDATE t_invitation_code SET status = #{status}, updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND deleted = 0")
    int updateStatus(@Param("id") String id, @Param("status") String status);

    /**
     * 统计批次内的邀请码数量
     */
    @Select("SELECT COUNT(*) FROM t_invitation_code WHERE batch_id = #{batchId} AND deleted = 0")
    int countByBatchId(@Param("batchId") String batchId);

    /**
     * 统计用户邀请码总使用次数
     */
    @Select("SELECT COALESCE(SUM(used_count), 0) FROM t_invitation_code WHERE owner_id = #{ownerId} AND type = 'User' AND deleted = 0")
    int sumUsedCountByOwner(@Param("ownerId") String ownerId);

    /**
     * 聚合统计（一次查询替代多次COUNT）
     */
    @Select("""
            SELECT
                COUNT(*) AS total,
                COUNT(CASE WHEN status = 'ACTIVE' THEN 1 END) AS active_count,
                COUNT(CASE WHEN status = 'DISABLED' THEN 1 END) AS disabled_count,
                COUNT(CASE WHEN status = 'EXHAUSTED' THEN 1 END) AS exhausted_count,
                COUNT(CASE WHEN status = 'EXPIRED' THEN 1 END) AS expired_count,
                COUNT(CASE WHEN type = 'System' THEN 1 END) AS system_count,
                COUNT(CASE WHEN type = 'User' THEN 1 END) AS user_count
            FROM t_invitation_code WHERE deleted = 0
            """)
    Map<String, Object> selectAggregatedStatistics();
}
