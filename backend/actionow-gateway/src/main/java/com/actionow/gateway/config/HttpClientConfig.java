package com.actionow.gateway.config;

import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gateway HttpClient 配置
 * 优化 SSE 流式传输的性能
 *
 * @author Actionow
 */
@Slf4j
@Configuration
public class HttpClientConfig {

    /**
     * 自定义 HttpClient 配置
     * 确保 SSE 响应能够流式传输
     */
    @Bean
    public HttpClientCustomizer httpClientCustomizer() {
        return httpClient -> {
            log.info("Customizing Gateway HttpClient for SSE streaming support");

            return httpClient
                    // 禁用响应压缩，确保 SSE 事件不被缓冲
                    .compress(false)
                    // 禁用响应体聚合，确保流式传输
                    .responseTimeout(java.time.Duration.ofMinutes(5))
                    // TCP 配置：禁用 Nagle 算法，减少延迟
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .doOnConnected(connection -> {
                        log.debug("HttpClient connection established");
                    })
                    .doOnResponseError((response, error) -> {
                        log.error("HttpClient response error: {}", error.getMessage());
                    });
        };
    }
}
