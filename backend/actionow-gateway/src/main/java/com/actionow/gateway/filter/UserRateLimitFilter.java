package com.actionow.gateway.filter;

import com.actionow.common.core.constant.CommonConstants;
import com.actionow.common.core.constant.RedisKeyConstants;
import com.actionow.gateway.config.GatewayRuntimeConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 用户级限流过滤器
 * 在认证过滤器（Order: -100）之后执行，确保 userId 来源于已验证的 JWT claims。
 * 使用固定窗口计数器算法（INCR + EXPIRE）
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserRateLimitFilter implements GlobalFilter, Ordered {

    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
    private final GatewayRuntimeConfigService gatewayRuntimeConfig;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!gatewayRuntimeConfig.isRateLimitEnabled()) {
            return chain.filter(exchange);
        }

        // 此时 AuthenticationFilter 已执行，X-User-Id 来自已验证的 JWT
        String userId = exchange.getRequest().getHeaders().getFirst(CommonConstants.HEADER_USER_ID);
        if (userId == null || userId.isEmpty()) {
            return chain.filter(exchange);
        }

        int userLimit = gatewayRuntimeConfig.getRateLimitUserLimit();
        int userWindow = gatewayRuntimeConfig.getRateLimitUserWindow();
        String userKey = RedisKeyConstants.RATE_LIMIT_USER + userId;
        String path = exchange.getRequest().getPath().value();

        return reactiveRedisTemplate.execute(
                        RateLimitFilter.RATE_LIMIT_SCRIPT,
                        List.of(userKey),
                        List.of(String.valueOf(userLimit), String.valueOf(userWindow))
                )
                .next()
                .defaultIfEmpty(0L)
                .onErrorResume(e -> {
                    log.error("用户限流检查失败: userId={}", userId, e);
                    return Mono.just(0L);
                })
                .flatMap(count -> {
                    if (count > userLimit) {
                        log.warn("用户限流触发: userId={}, path={}", userId, path);
                        return RateLimitFilter.tooManyRequests(exchange, userWindow, "user");
                    }
                    return chain.filter(exchange);
                });
    }

    @Override
    public int getOrder() {
        // 在认证过滤器（-100）之后、日志过滤器（-50）之前执行
        return -90;
    }
}
