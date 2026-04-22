package com.actionow.billing.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 回调确认响应
 */
@Data
@Builder
public class CallbackAckResponse {
    private boolean accepted;
    private String eventId;
    private String provider;
}
