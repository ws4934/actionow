package com.actionow.billing.provider;

import lombok.Builder;
import lombok.Data;

/**
 * 标准化渠道回调事件
 */
@Data
@Builder
public class ProviderWebhookEvent {
    private String provider;
    private String eventId;
    private String eventType;
    private String resourceId;
    private Object rawEvent;
}
