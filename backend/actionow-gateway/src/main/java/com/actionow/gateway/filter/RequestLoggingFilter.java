package com.actionow.gateway.filter;

import com.actionow.common.core.constant.CommonConstants;
import com.actionow.gateway.util.ReactiveWebUtils;
import com.actionow.gateway.config.ActionowGatewayProperties;
import com.actionow.gateway.config.GatewayRuntimeConfigService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 请求日志过滤器
 * 记录请求和响应的日志信息，支持请求体脱敏记录
 *
 * 日志格式: [${requestId}] ${method} ${path} ${status} ${duration}ms
 *          User: ${userId} IP: ${clientIp}
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final int MAX_BODY_LOG_SIZE = 2048; // 最大记录请求体大小

    private final ActionowGatewayProperties gatewayProperties;
    private final GatewayRuntimeConfigService gatewayRuntimeConfig;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ActionowGatewayProperties.LogConfig logConfig = gatewayProperties.getLog();

        // 日志开关优先从动态配置读取
        if (!gatewayRuntimeConfig.isLogEnabled()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        long startTime = System.currentTimeMillis();

        String requestId = request.getHeaders().getFirst(CommonConstants.HEADER_REQUEST_ID);
        String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";
        String path = request.getPath().value();
        String clientIp = ReactiveWebUtils.getClientIp(request);
        String userId = request.getHeaders().getFirst(CommonConstants.HEADER_USER_ID);

        // 记录请求头信息（脱敏处理，开关从动态配置读取）
        if (gatewayRuntimeConfig.isLogHeaders()) {
            String headers = formatHeaders(request.getHeaders(), logConfig.getSensitiveHeaders());
            log.debug("[{}] >>> {} {} Headers: {}", requestId, method, path, headers);
        }

        // 检查是否需要记录请求体（开关从动态配置读取，路径列表仍从静态配置）
        if (gatewayRuntimeConfig.isLogBody() && shouldLogBody(path, logConfig.getLogBodyPaths())
                && isJsonContent(request)) {
            return logRequestBody(exchange, chain, logConfig, requestId, method, path, clientIp, userId, startTime);
        }

        return chain.filter(exchange)
                .then(Mono.fromRunnable(() -> logResponse(exchange, requestId, method, path, clientIp, userId, startTime)));
    }

    /**
     * 记录请求体并继续过滤链
     */
    private Mono<Void> logRequestBody(ServerWebExchange exchange, GatewayFilterChain chain,
                                       ActionowGatewayProperties.LogConfig logConfig,
                                       String requestId, String method, String path,
                                       String clientIp, String userId, long startTime) {
        return DataBufferUtils.join(exchange.getRequest().getBody())
                .defaultIfEmpty(exchange.getResponse().bufferFactory().wrap(new byte[0]))
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);

                    String body = new String(bytes, StandardCharsets.UTF_8);
                    String sanitizedBody = sanitizeBody(body, logConfig.getSensitiveFields());

                    // 截断过长的请求体
                    if (sanitizedBody.length() > MAX_BODY_LOG_SIZE) {
                        sanitizedBody = sanitizedBody.substring(0, MAX_BODY_LOG_SIZE) + "...(truncated)";
                    }

                    log.info("[{}] >>> {} {} Body: {}", requestId, method, path, sanitizedBody);

                    // 重建请求，因为body只能读取一次
                    ServerHttpRequest mutatedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
                        @Override
                        public Flux<DataBuffer> getBody() {
                            return Flux.just(exchange.getResponse().bufferFactory().wrap(bytes));
                        }
                    };

                    ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();

                    return chain.filter(mutatedExchange)
                            .then(Mono.fromRunnable(() -> logResponse(exchange, requestId, method, path, clientIp, userId, startTime)));
                });
    }

    /**
     * 记录响应信息
     */
    private void logResponse(ServerWebExchange exchange, String requestId, String method,
                             String path, String clientIp, String userId, long startTime) {
        ServerHttpResponse response = exchange.getResponse();
        long duration = System.currentTimeMillis() - startTime;
        int status = response.getStatusCode() != null ? response.getStatusCode().value() : 0;

        // 根据状态码选择日志级别
        if (status >= 500) {
            log.error("[{}] {} {} {} {}ms User: {} IP: {}",
                    requestId, method, path, status, duration, userId, clientIp);
        } else if (status >= 400) {
            log.warn("[{}] {} {} {} {}ms User: {} IP: {}",
                    requestId, method, path, status, duration, userId, clientIp);
        } else {
            log.info("[{}] {} {} {} {}ms User: {} IP: {}",
                    requestId, method, path, status, duration, userId, clientIp);
        }
    }

    @Override
    public int getOrder() {
        // 在认证过滤器之后执行，确保requestId和userId已注入
        return -50;
    }

    /**
     * 格式化请求头（脱敏处理）
     */
    private String formatHeaders(HttpHeaders headers, List<String> sensitiveHeaders) {
        return headers.entrySet().stream()
                .map(entry -> {
                    String key = entry.getKey();
                    String value;
                    if (sensitiveHeaders.stream()
                            .anyMatch(s -> s.equalsIgnoreCase(key))) {
                        value = "***";
                    } else {
                        value = String.join(",", entry.getValue());
                    }
                    return key + "=" + value;
                })
                .collect(Collectors.joining(", "));
    }

    /**
     * 检查是否需要记录请求体
     */
    private boolean shouldLogBody(String path, List<String> logBodyPaths) {
        if (logBodyPaths == null || logBodyPaths.isEmpty()) {
            return false;
        }
        return logBodyPaths.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    /**
     * 检查是否为JSON内容类型
     */
    private boolean isJsonContent(ServerHttpRequest request) {
        MediaType contentType = request.getHeaders().getContentType();
        return contentType != null && contentType.isCompatibleWith(MediaType.APPLICATION_JSON);
    }

    /**
     * 对请求体进行脱敏处理
     */
    private String sanitizeBody(String body, List<String> sensitiveFields) {
        if (body == null || body.isEmpty() || sensitiveFields == null || sensitiveFields.isEmpty()) {
            return body;
        }

        try {
            JsonNode rootNode = objectMapper.readTree(body);
            if (rootNode.isObject()) {
                sanitizeJsonNode((ObjectNode) rootNode, sensitiveFields);
                return objectMapper.writeValueAsString(rootNode);
            }
        } catch (JsonProcessingException e) {
            // 不是有效的JSON，返回原始内容（可能需要截断）
            log.debug("Failed to parse body as JSON for sanitization: {}", e.getMessage());
        }

        return body;
    }

    /**
     * 递归脱敏JSON节点
     */
    private void sanitizeJsonNode(ObjectNode node, List<String> sensitiveFields) {
        Iterator<String> fieldNames = node.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            JsonNode childNode = node.get(fieldName);

            // 检查是否为敏感字段
            if (sensitiveFields.stream().anyMatch(sf -> sf.equalsIgnoreCase(fieldName))) {
                node.put(fieldName, "***");
            } else if (childNode.isObject()) {
                // 递归处理嵌套对象
                sanitizeJsonNode((ObjectNode) childNode, sensitiveFields);
            } else if (childNode.isArray()) {
                // 处理数组中的对象
                childNode.forEach(arrayElement -> {
                    if (arrayElement.isObject()) {
                        sanitizeJsonNode((ObjectNode) arrayElement, sensitiveFields);
                    }
                });
            }
        }
    }
}
