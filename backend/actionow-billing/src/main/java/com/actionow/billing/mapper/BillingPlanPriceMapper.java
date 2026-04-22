package com.actionow.billing.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.billing.entity.BillingPlanPrice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 套餐价格目录 Mapper
 * 使用 LambdaQueryWrapper 确保 JacksonTypeHandler 正确处理 JSONB 字段（meta）。
 * 注意: BillingPlanPrice 无 @TableLogic，需显式过滤 deleted = 0。
 */
@Mapper
public interface BillingPlanPriceMapper extends BaseMapper<BillingPlanPrice> {

    default BillingPlanPrice selectByNaturalKey(String provider, String planCode,
                                                 String billingCycle, String currency) {
        return selectOne(new LambdaQueryWrapper<BillingPlanPrice>()
                .eq(BillingPlanPrice::getProvider, provider)
                .eq(BillingPlanPrice::getPlanCode, planCode)
                .eq(BillingPlanPrice::getBillingCycle, billingCycle)
                .eq(BillingPlanPrice::getCurrency, currency)
                .eq(BillingPlanPrice::getDeleted, 0)
                .last("LIMIT 1"));
    }

    default List<BillingPlanPrice> listByProvider(String provider) {
        return selectList(new LambdaQueryWrapper<BillingPlanPrice>()
                .eq(BillingPlanPrice::getProvider, provider)
                .eq(BillingPlanPrice::getDeleted, 0)
                .orderByAsc(BillingPlanPrice::getPlanCode)
                .orderByAsc(BillingPlanPrice::getBillingCycle)
                .orderByDesc(BillingPlanPrice::getUpdatedAt));
    }

    default List<BillingPlanPrice> listAll() {
        return selectList(new LambdaQueryWrapper<BillingPlanPrice>()
                .eq(BillingPlanPrice::getDeleted, 0)
                .orderByAsc(BillingPlanPrice::getProvider)
                .orderByAsc(BillingPlanPrice::getPlanCode)
                .orderByAsc(BillingPlanPrice::getBillingCycle)
                .orderByDesc(BillingPlanPrice::getUpdatedAt));
    }

    @Select("SELECT stripe_price_id FROM public.t_billing_plan_price " +
            "WHERE provider = #{provider} AND plan_code = #{planCode} AND billing_cycle = #{billingCycle} " +
            "AND status = 'ACTIVE' AND deleted = 0 " +
            "ORDER BY CASE WHEN currency = #{currency} THEN 0 ELSE 1 END, updated_at DESC LIMIT 1")
    String selectActiveStripePriceId(@Param("provider") String provider,
                                     @Param("planCode") String planCode,
                                     @Param("billingCycle") String billingCycle,
                                     @Param("currency") String currency);
}
