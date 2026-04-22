package com.actionow.gateway.util;

import com.actionow.common.core.util.WebUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * Reactive 环境的 Web 工具类
 * 适用于 Spring Cloud Gateway（WebFlux）
 *
 * @author Actionow
 */
public final class ReactiveWebUtils {

    private ReactiveWebUtils() {
    }

    /**
     * 获取客户端真实IP地址（Reactive）
     *
     * @param request ServerHttpRequest
     * @return 客户端IP地址
     */
    public static String getClientIp(ServerHttpRequest request) {
        if (request == null) {
            return "unknown";
        }

        String remoteAddr = request.getRemoteAddress() != null
                ? request.getRemoteAddress().getAddress().getHostAddress()
                : null;

        return WebUtils.getClientIp(
                request.getHeaders().getFirst("X-Forwarded-For"),
                request.getHeaders().getFirst("X-Real-IP"),
                remoteAddr
        );
    }
}
