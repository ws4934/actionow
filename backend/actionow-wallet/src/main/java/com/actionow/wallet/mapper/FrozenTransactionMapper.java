package com.actionow.wallet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.wallet.entity.FrozenTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 冻结流水 Mapper
 * 对应表: t_frozen_transaction
 *
 * @author Actionow
 */
@Mapper
public interface FrozenTransactionMapper extends BaseMapper<FrozenTransaction> {

    /**
     * 根据工作空间ID查询冻结记录
     */
    @Select("SELECT * FROM public.t_frozen_transaction WHERE workspace_id = #{workspaceId} " +
            "AND status = 'FROZEN' ORDER BY created_at DESC")
    List<FrozenTransaction> selectFrozenByWorkspaceId(@Param("workspaceId") String workspaceId);

    /**
     * 根据关联任务ID查询活跃的冻结记录（状态为FROZEN）
     */
    @Select("SELECT * FROM public.t_frozen_transaction WHERE related_task_id = #{taskId} AND status = 'FROZEN' LIMIT 1")
    FrozenTransaction selectByTaskId(@Param("taskId") String taskId);

    /**
     * 根据关联任务ID查询所有活跃的冻结记录（状态为FROZEN，支持多次追加冻结场景）
     */
    @Select("SELECT * FROM public.t_frozen_transaction WHERE related_task_id = #{taskId} AND status = 'FROZEN' ORDER BY created_at ASC")
    List<FrozenTransaction> selectAllByTaskId(@Param("taskId") String taskId);

    /**
     * 批量将指定任务的所有冻结记录更新为已解冻
     */
    @Update("UPDATE public.t_frozen_transaction SET status = 'UNFROZEN', unfrozen_at = NOW(), updated_at = NOW() " +
            "WHERE related_task_id = #{taskId} AND status = 'FROZEN'")
    int unfreezeAllByTaskId(@Param("taskId") String taskId);

    /**
     * 批量将指定任务的所有冻结记录更新为已消费
     */
    @Update("UPDATE public.t_frozen_transaction SET status = 'CONSUMED', unfrozen_at = NOW(), updated_at = NOW() " +
            "WHERE related_task_id = #{taskId} AND status = 'FROZEN'")
    int consumeAllByTaskId(@Param("taskId") String taskId);

    /**
     * 更新冻结状态为已解冻
     */
    @Update("UPDATE public.t_frozen_transaction SET status = 'UNFROZEN', unfrozen_at = NOW(), updated_at = NOW() " +
            "WHERE id = #{id} AND status = 'FROZEN'")
    int unfreezeById(@Param("id") String id);

    /**
     * 更新冻结状态为已消费
     */
    @Update("UPDATE public.t_frozen_transaction SET status = 'CONSUMED', unfrozen_at = NOW(), updated_at = NOW() " +
            "WHERE id = #{id} AND status = 'FROZEN'")
    int consumeById(@Param("id") String id);

    /**
     * 批量更新过期的冻结记录
     */
    @Update("UPDATE public.t_frozen_transaction SET status = 'EXPIRED', updated_at = NOW() " +
            "WHERE status = 'FROZEN' AND expires_at IS NOT NULL AND expires_at < #{now}")
    int expireFrozenTransactions(@Param("now") LocalDateTime now);

    /**
     * 统计工作空间当前冻结总额
     */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM public.t_frozen_transaction " +
            "WHERE workspace_id = #{workspaceId} AND status = 'FROZEN'")
    Long sumFrozenByWorkspace(@Param("workspaceId") String workspaceId);
}
