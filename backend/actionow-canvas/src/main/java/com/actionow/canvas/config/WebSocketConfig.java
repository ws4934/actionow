package com.actionow.canvas.config;

import com.actionow.canvas.websocket.CanvasWebSocketHandler;
import com.actionow.canvas.websocket.WebSocketAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置
 * 配置画布实时同步的 WebSocket 端点
 *
 * @author Actionow
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final CanvasWebSocketHandler canvasWebSocketHandler;
    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 支持两种 URL 格式:
        // 1. /ws/canvas/{canvasId} - 路径参数
        // 2. /ws/canvas?canvasId=xxx - 查询参数
        registry.addHandler(canvasWebSocketHandler, "/ws/canvas/{canvasId}", "/ws/canvas")
                .addInterceptors(webSocketAuthInterceptor)
                .setAllowedOrigins("*");
    }
}
