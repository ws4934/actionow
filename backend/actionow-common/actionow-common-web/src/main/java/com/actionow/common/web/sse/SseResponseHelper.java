package com.actionow.common.web.sse;

import jakarta.servlet.http.HttpServletResponse;

/**
 * SSE 响应头工具类
 * 用于在控制器中设置标准的 SSE 响应头
 *
 * @author Actionow
 */
public final class SseResponseHelper {

    private SseResponseHelper() {
        // 工具类，禁止实例化
    }

    /**
     * 设置标准 SSE 响应头
     * 禁用各种代理/缓冲，确保实时流式传输
     *
     * @param response HttpServletResponse
     */
    public static void configureSseHeaders(HttpServletResponse response) {
        // 禁用 Nginx 代理缓冲
        response.setHeader("X-Accel-Buffering", "no");
        // 禁用缓存
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");
        // 保持连接
        response.setHeader("Connection", "keep-alive");
        // 设置 Content-Type（通常由 produces 注解设置，这里作为备份）
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
    }

    /**
     * 设置 SSE 响应头（简化版，仅设置关键头）
     *
     * @param response HttpServletResponse
     */
    public static void configureSseHeadersSimple(HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Connection", "keep-alive");
    }
}
