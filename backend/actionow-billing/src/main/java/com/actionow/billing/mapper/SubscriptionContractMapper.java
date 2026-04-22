package com.actionow.billing.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.billing.entity.SubscriptionContract;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 订阅合约 Mapper
 * 使用 LambdaQueryWrapper 确保 JacksonTypeHandler 正确处理 JSONB 字段（meta）。
 * 注意: SubscriptionContract 无 @TableLogic 和 deleted 字段。
 */
@Mapper
public interface SubscriptionContractMapper extends BaseMapper<SubscriptionContract> {

    default SubscriptionContract selectActiveByWorkspaceId(String workspaceId) {
        return selectOne(new LambdaQueryWrapper<SubscriptionContract>()
                .eq(SubscriptionContract::getWorkspaceId, workspaceId)
                .in(SubscriptionContract::getStatus, List.of("TRIALING", "ACTIVE", "PAST_DUE"))
                .orderByDesc(SubscriptionContract::getUpdatedAt)
                .last("LIMIT 1"));
    }

    default SubscriptionContract selectByProviderSubscriptionId(String provider, String providerSubscriptionId) {
        return selectOne(new LambdaQueryWrapper<SubscriptionContract>()
                .eq(SubscriptionContract::getProvider, provider)
                .eq(SubscriptionContract::getProviderSubscriptionId, providerSubscriptionId)
                .last("LIMIT 1"));
    }

    default SubscriptionContract selectByContractId(String id) {
        return selectById(id);
    }
}
