package com.actionow.collab.config;

import com.actionow.collab.websocket.CollaborationHub;
import com.actionow.collab.websocket.WebSocketAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置
 * 配置统一 WebSocket 端点
 *
 * @author Actionow
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final CollaborationHub collaborationHub;
    private final WebSocketAuthInterceptor authInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 统一 WebSocket 端点
        // 连接地址: ws://host:port/ws?token=xxx
        registry.addHandler(collaborationHub, "/ws")
                .addInterceptors(authInterceptor)
                .setAllowedOrigins("*");

        // SockJS fallback 支持（可选）
        registry.addHandler(collaborationHub, "/sockjs")
                .addInterceptors(authInterceptor)
                .setAllowedOrigins("*")
                .withSockJS();
    }
}
