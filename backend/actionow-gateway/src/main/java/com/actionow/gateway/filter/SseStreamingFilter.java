package com.actionow.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * SSE 流式传输过滤器（全局）
 * 解决 Spring Cloud Gateway 默认缓冲响应体导致 SSE 事件无法实时推送的问题
 *
 * 优化点：
 * 1. 使用精确的路径匹配代替模糊匹配
 * 2. 移除生产环境不必要的 buffer 内容日志
 * 3. 使用高效的 writeAndFlushWith 实现逐块刷新
 *
 * @author Actionow
 */
@Slf4j
@Component
public class SseStreamingFilter implements GlobalFilter, Ordered {

    private static final String SSE_STREAMING_ATTR = "SSE_STREAMING_ENABLED";

    /**
     * SSE 流式端点路径后缀（精确匹配）
     */
    private static final Set<String> SSE_PATH_SUFFIXES = Set.of(
            "/messages/stream",  // Agent 模块
            "/ai/stream"         // Task 模块
    );

    /**
     * 在 NettyWriteResponseFilter (-1) 之前执行
     */
    @Override
    public int getOrder() {
        return -2;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 如果请求已被其他过滤器处理，跳过
        Boolean alreadyRouted = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ALREADY_ROUTED_ATTR);
        if (Boolean.TRUE.equals(alreadyRouted)) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getPath().value();
        String acceptHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.ACCEPT);

        // 检测 SSE 请求（使用精确路径匹配或 Accept 头）
        boolean isSseRequest = isSsePath(path)
                || (acceptHeader != null && acceptHeader.contains(MediaType.TEXT_EVENT_STREAM_VALUE));

        if (!isSseRequest) {
            return chain.filter(exchange);
        }

        log.info("SSE streaming filter enabled for path: {}", path);

        // 标记为 SSE 请求
        exchange.getAttributes().put(SSE_STREAMING_ATTR, true);

        // 包装响应
        ServerHttpResponse originalResponse = exchange.getResponse();
        ServerHttpResponseDecorator decoratedResponse = new SseResponseDecorator(originalResponse, path);

        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }

    /**
     * 检查路径是否为 SSE 端点
     */
    private boolean isSsePath(String path) {
        for (String suffix : SSE_PATH_SUFFIXES) {
            if (path.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * SSE 响应装饰器
     * 设置正确的响应头并使用 writeAndFlushWith 实现逐块刷新
     */
    private static class SseResponseDecorator extends ServerHttpResponseDecorator {

        private final String path;

        public SseResponseDecorator(ServerHttpResponse delegate, String path) {
            super(delegate);
            this.path = path;
        }

        @Override
        public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
            // 设置 SSE 响应头（禁用代理缓冲）
            HttpHeaders headers = getHeaders();
            headers.set("X-Accel-Buffering", "no");
            headers.set("Cache-Control", "no-cache, no-store, must-revalidate");
            headers.set("Connection", "keep-alive");

            log.debug("SSE writeWith for path: {}", path);

            // 转换为流式输出：每个 buffer 立即刷新
            if (body instanceof Flux) {
                Flux<? extends DataBuffer> fluxBody = (Flux<? extends DataBuffer>) body;
                // 将每个 buffer 包装为单独的 Flux，实现逐块刷新
                Flux<Flux<DataBuffer>> flushingBody = fluxBody.map(Flux::just);
                return super.writeAndFlushWith(flushingBody);
            }

            // Mono 情况
            if (body instanceof Mono) {
                Mono<? extends DataBuffer> monoBody = (Mono<? extends DataBuffer>) body;
                Flux<Flux<DataBuffer>> flushingBody = monoBody.flux().map(Flux::just);
                return super.writeAndFlushWith(flushingBody);
            }

            // 其他 Publisher 情况
            Flux<Flux<DataBuffer>> flushingBody = Flux.from(body).map(Flux::just);
            return super.writeAndFlushWith(flushingBody);
        }

        @Override
        public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
            // 设置响应头
            HttpHeaders headers = getHeaders();
            headers.set("X-Accel-Buffering", "no");
            headers.set("Cache-Control", "no-cache, no-store, must-revalidate");

            log.debug("SSE writeAndFlushWith for path: {}", path);

            return super.writeAndFlushWith(body);
        }
    }
}
