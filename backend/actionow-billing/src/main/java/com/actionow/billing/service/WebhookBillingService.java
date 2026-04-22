package com.actionow.billing.service;

import com.actionow.billing.dto.CallbackAckResponse;

import java.util.Map;

public interface WebhookBillingService {

    CallbackAckResponse handleCallback(String provider, String payload, Map<String, String> headers);
}
