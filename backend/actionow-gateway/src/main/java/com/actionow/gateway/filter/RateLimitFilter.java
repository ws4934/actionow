package com.actionow.gateway.filter;

import com.actionow.common.core.constant.RedisKeyConstants;
import com.actionow.common.core.result.ResultCode;
import com.actionow.gateway.util.FilterResponseUtils;
import com.actionow.gateway.util.ReactiveWebUtils;
import com.actionow.gateway.config.GatewayRuntimeConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 限流过滤器
 * 支持全局限流、IP限流、API特定限流
 * 使用固定窗口计数器算法（INCR + EXPIRE）
 *
 * 注意：用户级限流由 {@link UserRateLimitFilter} 在认证之后执行，
 * 以确保 userId 来源于已验证的 JWT claims 而非客户端伪造的 header。
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
    private final GatewayRuntimeConfigService gatewayRuntimeConfig;

    /**
     * Lua脚本：固定窗口限流
     * 使用INCR + EXPIRE实现固定窗口计数
     */
    static final RedisScript<Long> RATE_LIMIT_SCRIPT = RedisScript.of(
            """
            local key = KEYS[1]
            local limit = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local current = redis.call('INCR', key)
            if current == 1 then
                redis.call('EXPIRE', key, window)
            end
            return current
            """,
            Long.class
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!gatewayRuntimeConfig.isRateLimitEnabled()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String clientIp = ReactiveWebUtils.getClientIp(request);
        String path = request.getPath().value();

        int globalLimit = gatewayRuntimeConfig.getRateLimitGlobal();
        int ipLimit = gatewayRuntimeConfig.getRateLimitIpLimit();
        int ipWindow = gatewayRuntimeConfig.getRateLimitIpWindow();

        // 1. 全局限流（每秒）
        return checkRateLimitWithScript(RedisKeyConstants.RATE_LIMIT_GLOBAL, globalLimit, 1)
                .map(count -> count <= globalLimit)
                .defaultIfEmpty(true)
                .flatMap(allowed -> {
                    if (!allowed) {
                        log.warn("全局限流触发: path={}, ip={}", path, clientIp);
                        return tooManyRequests(exchange, 1, "global");
                    }

                    // 2. IP 限流
                    String ipKey = RedisKeyConstants.RATE_LIMIT_IP + clientIp;
                    return checkRateLimitWithScript(ipKey, ipLimit, ipWindow)
                            .map(count -> count <= ipLimit)
                            .defaultIfEmpty(true)
                            .flatMap(ipAllowed -> {
                                if (!ipAllowed) {
                                    log.warn("IP限流触发: ip={}, path={}", clientIp, path);
                                    return tooManyRequests(exchange, ipWindow, "ip");
                                }

                                // 3. API 特定限流（从动态配置读取）
                                return checkApiRateLimit(exchange, chain, clientIp, path);
                            });
                });
    }

    /**
     * 检查API特定限流（从动态配置读取）
     */
    private Mono<Void> checkApiRateLimit(ServerWebExchange exchange, GatewayFilterChain chain,
                                          String clientIp, String path) {
        Map<String, Integer> apiLimits = gatewayRuntimeConfig.getApiLimits();

        if (apiLimits.isEmpty()) {
            return chain.filter(exchange);
        }

        // 查找匹配的API限流配置
        for (Map.Entry<String, Integer> entry : apiLimits.entrySet()) {
            String pathPrefix = entry.getKey();
            if (path.startsWith(pathPrefix)) {
                int limit = entry.getValue();
                String apiKey = RedisKeyConstants.RATE_LIMIT_API + pathPrefix.replace("/", "_") + ":" + clientIp;

                return checkRateLimitWithScript(apiKey, limit, 1)
                        .map(count -> count <= limit)
                        .defaultIfEmpty(true)
                        .flatMap(allowed -> {
                            if (!allowed) {
                                log.warn("API限流触发: path={}, ip={}, limit={}/s", path, clientIp, limit);
                                return tooManyRequests(exchange, 1, "api");
                            }
                            return chain.filter(exchange);
                        });
            }
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -200; // 在认证过滤器之前执行
    }

    /**
     * 使用Lua脚本检查限流（原子操作）
     *
     * @param key    限流键
     * @param limit  限制次数
     * @param window 时间窗口（秒）
     * @return 当前计数值
     */
    private Mono<Long> checkRateLimitWithScript(String key, int limit, int window) {
        return reactiveRedisTemplate.execute(
                RATE_LIMIT_SCRIPT,
                List.of(key),
                List.of(String.valueOf(limit), String.valueOf(window))
        )
        .next()
        .defaultIfEmpty(0L)
        .onErrorResume(e -> {
            log.error("Rate limit check failed for key: {}", key, e);
            // 限流检查失败时默认放行（返回0），避免影响正常请求
            return Mono.just(0L);
        });
    }

    /**
     * 返回 429 响应
     *
     * @param exchange    交换对象
     * @param retryAfter  重试等待时间（秒）
     * @param limitType   限流类型（用于响应头）
     */
    static Mono<Void> tooManyRequests(ServerWebExchange exchange, int retryAfter, String limitType) {
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().add("X-RateLimit-Exceeded", "true");
        response.getHeaders().add("X-RateLimit-Type", limitType);
        response.getHeaders().add("Retry-After", String.valueOf(retryAfter));
        return FilterResponseUtils.writeErrorResponse(exchange, HttpStatus.TOO_MANY_REQUESTS,
                ResultCode.RATE_LIMITED);
    }
}
