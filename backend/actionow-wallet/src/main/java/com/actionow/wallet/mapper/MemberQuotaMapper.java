package com.actionow.wallet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.wallet.entity.MemberQuota;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 成员配额 Mapper
 * 对应表: t_member_quota
 *
 * @author Actionow
 */
@Mapper
public interface MemberQuotaMapper extends BaseMapper<MemberQuota> {

    /**
     * 根据工作空间和用户查询配额
     */
    @Select("SELECT * FROM public.t_member_quota WHERE workspace_id = #{workspaceId} AND user_id = #{userId} " +
            "AND deleted = 0")
    MemberQuota selectByWorkspaceAndUser(@Param("workspaceId") String workspaceId,
                                         @Param("userId") String userId);

    /**
     * 查询工作空间的所有成员配额
     */
    @Select("SELECT * FROM public.t_member_quota WHERE workspace_id = #{workspaceId} AND deleted = 0 " +
            "ORDER BY created_at DESC")
    List<MemberQuota> selectByWorkspaceId(@Param("workspaceId") String workspaceId);

    /**
     * 增加已使用配额
     */
    @Update("UPDATE public.t_member_quota SET used_amount = used_amount + #{amount}, " +
            "version = version + 1, updated_at = NOW() " +
            "WHERE id = #{quotaId} AND (limit_amount < 0 OR (limit_amount - used_amount) >= #{amount}) " +
            "AND deleted = 0 AND version = #{version}")
    int increaseUsedAmount(@Param("quotaId") String quotaId,
                           @Param("amount") long amount,
                           @Param("version") int version);

    /**
     * 减少已使用配额（退还）
     */
    @Update("UPDATE public.t_member_quota SET used_amount = used_amount - #{amount}, " +
            "version = version + 1, updated_at = NOW() " +
            "WHERE id = #{quotaId} AND used_amount >= #{amount} AND deleted = 0 AND version = #{version}")
    int decreaseUsedAmount(@Param("quotaId") String quotaId,
                           @Param("amount") long amount,
                           @Param("version") int version);

    /**
     * 重置配额使用量（按重置周期）
     */
    @Update("UPDATE public.t_member_quota SET used_amount = 0, last_reset_at = NOW(), " +
            "version = version + 1, updated_at = NOW() " +
            "WHERE reset_cycle = #{resetCycle} AND last_reset_at < #{cutoffTime} AND deleted = 0")
    int resetQuotaByResetCycle(@Param("resetCycle") String resetCycle,
                               @Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * 重置工作空间所有配额
     */
    @Update("UPDATE public.t_member_quota SET used_amount = 0, last_reset_at = NOW(), " +
            "version = version + 1, updated_at = NOW() " +
            "WHERE workspace_id = #{workspaceId} AND deleted = 0")
    int resetQuotaByWorkspace(@Param("workspaceId") String workspaceId);

    /**
     * 批量调整工作空间成员配额上限（计划变更时使用）
     */
    @Update("UPDATE public.t_member_quota SET limit_amount = #{limitAmount}, " +
            "version = version + 1, updated_at = NOW() " +
            "WHERE workspace_id = #{workspaceId} AND deleted = 0")
    int updateLimitByWorkspace(@Param("workspaceId") String workspaceId,
                               @Param("limitAmount") long limitAmount);

    /**
     * 物理删除成员配额（成员移除时使用）
     * 使用物理删除而非软删除，避免 (workspace_id, user_id) 唯一约束
     * 阻止成员重新加入后创建新配额
     */
    @org.apache.ibatis.annotations.Delete(
            "DELETE FROM public.t_member_quota WHERE workspace_id = #{workspaceId} AND user_id = #{userId}")
    int hardDeleteByWorkspaceAndUser(@Param("workspaceId") String workspaceId,
                                     @Param("userId") String userId);
}
