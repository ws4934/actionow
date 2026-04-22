package com.actionow.billing.controller;

import com.actionow.billing.dto.CallbackAckResponse;
import com.actionow.billing.service.WebhookBillingService;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.IgnoreAuth;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付渠道回调入口
 * 注意: 不使用 @RequestBody，直接从 HttpServletRequest 读取原始 body，
 * 避免 Jackson 将 JSON 对象反序列化为 String 时报错。
 */
@Slf4j
@RestController
@RequestMapping("/billing/callback")
@RequiredArgsConstructor
public class BillingCallbackController {

    private final WebhookBillingService webhookBillingService;

    @IgnoreAuth
    @PostMapping("/{provider}")
    public Result<CallbackAckResponse> callback(@PathVariable String provider,
                                                HttpServletRequest request) throws IOException {
        String payload = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> headers = extractHeaders(request);
        CallbackAckResponse response = webhookBillingService.handleCallback(provider, payload, headers);
        return Result.success(response);
    }

    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, request.getHeader(name));
        }
        return headers;
    }
}
