package com.actionow.wallet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.wallet.entity.PointTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 积分流水 Mapper
 * 对应表: t_point_transaction
 *
 * @author Actionow
 */
@Mapper
public interface PointTransactionMapper extends BaseMapper<PointTransaction> {

    /**
     * 根据工作空间ID查询交易记录
     */
    @Select("SELECT * FROM public.t_point_transaction WHERE workspace_id = #{workspaceId} " +
            "ORDER BY created_at DESC LIMIT #{limit}")
    List<PointTransaction> selectByWorkspaceId(@Param("workspaceId") String workspaceId,
                                               @Param("limit") int limit);

    /**
     * 分页查询工作空间交易记录
     */
    @Select("<script>" +
            "SELECT * FROM public.t_point_transaction WHERE workspace_id = #{workspaceId}" +
            "<if test='transactionType != null'> AND transaction_type = #{transactionType}</if>" +
            " ORDER BY created_at DESC" +
            "</script>")
    IPage<PointTransaction> selectPageByWorkspaceId(Page<PointTransaction> page,
                                                     @Param("workspaceId") String workspaceId,
                                                     @Param("transactionType") String transactionType);

    /**
     * 根据用户ID查询交易记录
     */
    @Select("SELECT * FROM public.t_point_transaction WHERE workspace_id = #{workspaceId} AND user_id = #{userId} " +
            "ORDER BY created_at DESC LIMIT #{limit}")
    List<PointTransaction> selectByUserId(@Param("workspaceId") String workspaceId,
                                          @Param("userId") String userId,
                                          @Param("limit") int limit);

    /**
     * 根据关联任务ID查询交易记录
     */
    @Select("SELECT * FROM public.t_point_transaction WHERE related_task_id = #{taskId} " +
            "ORDER BY created_at DESC")
    List<PointTransaction> selectByTaskId(@Param("taskId") String taskId);

    /**
     * 查询时间范围内的交易记录
     */
    @Select("SELECT * FROM public.t_point_transaction WHERE workspace_id = #{workspaceId} " +
            "AND created_at >= #{startTime} AND created_at <= #{endTime} " +
            "ORDER BY created_at DESC")
    List<PointTransaction> selectByTimeRange(@Param("workspaceId") String workspaceId,
                                             @Param("startTime") LocalDateTime startTime,
                                             @Param("endTime") LocalDateTime endTime);

    /**
     * 统计工作空间的消费总额
     */
    @Select("SELECT COALESCE(SUM(ABS(amount)), 0) FROM public.t_point_transaction " +
            "WHERE workspace_id = #{workspaceId} AND transaction_type = 'CONSUME'")
    Long sumConsumeByWorkspace(@Param("workspaceId") String workspaceId);

    /**
     * 统计用户在工作空间的消费总额（指定时间范围）
     */
    @Select("SELECT COALESCE(SUM(ABS(amount)), 0) FROM public.t_point_transaction " +
            "WHERE workspace_id = #{workspaceId} AND user_id = #{userId} " +
            "AND transaction_type = 'CONSUME' AND created_at >= #{startTime}")
    Long sumConsumeByUser(@Param("workspaceId") String workspaceId,
                          @Param("userId") String userId,
                          @Param("startTime") LocalDateTime startTime);

    /**
     * 根据支付单号查询充值流水（用于幂等入账）
     */
    @Select("SELECT * FROM public.t_point_transaction " +
            "WHERE workspace_id = #{workspaceId} AND transaction_type = 'TOPUP' " +
            "AND meta->>'paymentId' = #{paymentId} " +
            "ORDER BY created_at DESC LIMIT 1")
    PointTransaction selectTopupByPaymentId(@Param("workspaceId") String workspaceId,
                                            @Param("paymentId") String paymentId);

    /**
     * 按交易类型统计
     */
    @Select("SELECT transaction_type, COALESCE(SUM(amount), 0) as total " +
            "FROM public.t_point_transaction WHERE workspace_id = #{workspaceId} " +
            "AND created_at >= #{startTime} AND created_at <= #{endTime} " +
            "GROUP BY transaction_type")
    List<java.util.Map<String, Object>> sumByType(@Param("workspaceId") String workspaceId,
                                                   @Param("startTime") LocalDateTime startTime,
                                                   @Param("endTime") LocalDateTime endTime);
}
