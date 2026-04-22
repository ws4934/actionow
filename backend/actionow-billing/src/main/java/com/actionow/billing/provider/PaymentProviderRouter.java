package com.actionow.billing.provider;

import com.actionow.billing.enums.BillingErrorCode;
import com.actionow.billing.enums.PaymentProvider;
import com.actionow.common.core.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 支付渠道路由
 */
@Component
public class PaymentProviderRouter {

    private final Map<PaymentProvider, PaymentProviderAdapter> adapterMap = new EnumMap<>(PaymentProvider.class);

    public PaymentProviderRouter(List<PaymentProviderAdapter> adapters) {
        for (PaymentProviderAdapter adapter : adapters) {
            adapterMap.put(adapter.provider(), adapter);
        }
    }

    public PaymentProviderAdapter getAdapter(PaymentProvider provider) {
        PaymentProviderAdapter adapter = adapterMap.get(provider);
        if (adapter == null) {
            throw new BusinessException(BillingErrorCode.PROVIDER_NOT_SUPPORTED);
        }
        return adapter;
    }
}
