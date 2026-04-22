package com.actionow.billing.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.billing.entity.PaymentOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付订单 Mapper
 * 使用 LambdaQueryWrapper 确保 JacksonTypeHandler 正确处理 JSONB 字段（meta）。
 * 注意: PaymentOrder 无 @TableLogic 和 deleted 字段。
 */
@Mapper
public interface PaymentOrderMapper extends BaseMapper<PaymentOrder> {

    default PaymentOrder selectByOrderNo(String orderNo) {
        return selectOne(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getOrderNo, orderNo));
    }

    @Update("UPDATE public.t_payment_order SET provider_session_id = #{sessionId}, status = 'PENDING', " +
            "updated_at = NOW(), version = version + 1 " +
            "WHERE order_no = #{orderNo} AND status IN ('INIT', 'PENDING')")
    int markPendingWithSession(@Param("orderNo") String orderNo,
                               @Param("sessionId") String sessionId);

    @Update("UPDATE public.t_payment_order SET status = 'PAID', paid_at = NOW(), " +
            "provider_payment_id = COALESCE(#{providerPaymentId}, provider_payment_id), " +
            "provider_session_id = COALESCE(#{providerSessionId}, provider_session_id), " +
            "updated_at = NOW(), version = version + 1 " +
            "WHERE order_no = #{orderNo} AND status <> 'PAID'")
    int markPaid(@Param("orderNo") String orderNo,
                 @Param("providerPaymentId") String providerPaymentId,
                 @Param("providerSessionId") String providerSessionId);

    @Update("UPDATE public.t_payment_order SET status = 'FAILED', fail_code = #{failCode}, fail_message = #{failMessage}, " +
            "updated_at = NOW(), version = version + 1 " +
            "WHERE order_no = #{orderNo} AND status IN ('INIT', 'PENDING')")
    int markFailed(@Param("orderNo") String orderNo,
                   @Param("failCode") String failCode,
                   @Param("failMessage") String failMessage);

    /**
     * 查询指定渠道的待处理订单（用于轮询查单）
     */
    default List<PaymentOrder> selectPendingByProvider(String provider, LocalDateTime createdBefore) {
        return selectList(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getProvider, provider)
                .eq(PaymentOrder::getStatus, "PENDING")
                .lt(PaymentOrder::getCreatedAt, createdBefore)
                .orderByAsc(PaymentOrder::getCreatedAt)
                .last("LIMIT 50"));
    }

    /**
     * 查询所有渠道的待处理订单
     */
    default List<PaymentOrder> selectAllPending(LocalDateTime createdBefore) {
        return selectList(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getStatus, "PENDING")
                .lt(PaymentOrder::getCreatedAt, createdBefore)
                .orderByAsc(PaymentOrder::getCreatedAt)
                .last("LIMIT 50"));
    }
}
