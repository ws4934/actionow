package com.actionow.gateway.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.server.ServerWebExchange;

import java.util.List;

/**
 * CORS 跨域配置
 * 使用动态 CorsConfigurationSource，每次请求从 RuntimeConfig 读取最新配置
 *
 * @author Actionow
 */
@Configuration
@RequiredArgsConstructor
public class CorsConfig {

    private final ActionowGatewayProperties gatewayProperties;
    private final GatewayRuntimeConfigService gatewayRuntimeConfig;

    @Bean
    public CorsWebFilter corsWebFilter() {
        return new CorsWebFilter(dynamicCorsConfigurationSource());
    }

    /**
     * 动态 CORS 配置源，每次请求实时读取 RuntimeConfig
     */
    private CorsConfigurationSource dynamicCorsConfigurationSource() {
        return (ServerWebExchange exchange) -> {
            if (!gatewayRuntimeConfig.isCorsEnabled()) {
                return null;
            }

            CorsConfiguration config = new CorsConfiguration();

            // 从动态配置读取允许的源
            List<String> allowedOrigins = gatewayRuntimeConfig.getCorsAllowedOrigins();
            boolean allowCredentials = gatewayRuntimeConfig.isCorsAllowCredentials();

            if (allowedOrigins != null) {
                for (String origin : allowedOrigins) {
                    String trimmed = origin.trim();
                    if ("*".equals(trimmed) && allowCredentials) {
                        config.addAllowedOriginPattern("*");
                    } else {
                        config.addAllowedOrigin(trimmed);
                    }
                }
            }

            // 允许的方法和请求头仍从静态配置读取（变更频率低）
            ActionowGatewayProperties.CorsConfig staticCors = gatewayProperties.getCors();
            if (staticCors.getAllowedMethods() != null) {
                staticCors.getAllowedMethods().forEach(config::addAllowedMethod);
            }
            if (staticCors.getAllowedHeaders() != null) {
                staticCors.getAllowedHeaders().forEach(config::addAllowedHeader);
            }

            config.setAllowCredentials(allowCredentials);
            config.setMaxAge(gatewayRuntimeConfig.getCorsMaxAge());

            // 暴露响应头
            config.addExposedHeader("X-Request-Id");
            config.addExposedHeader("X-RateLimit-Exceeded");
            config.addExposedHeader("Retry-After");

            return config;
        };
    }
}
