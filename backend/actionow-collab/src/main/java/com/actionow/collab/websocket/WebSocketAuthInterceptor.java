package com.actionow.collab.websocket;

import com.actionow.collab.dto.TokenValidateRequest;
import com.actionow.collab.dto.TokenValidateResponse;
import com.actionow.collab.feign.UserFeignClient;
import com.actionow.collab.feign.WorkspaceFeignClient;
import com.actionow.collab.feign.WorkspaceMembershipInfo;
import com.actionow.common.core.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 认证拦截器
 * 在握手阶段验证用户身份
 * 通过 Feign 调用 user 模块进行 Token 验证
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    private final UserFeignClient userFeignClient;
    private final WorkspaceFeignClient workspaceFeignClient;

    /**
     * WebSocket会话属性键
     */
    public static final String ATTR_USER_ID = "userId";
    public static final String ATTR_USERNAME = "username";
    public static final String ATTR_NICKNAME = "nickname";
    public static final String ATTR_AVATAR = "avatar";
    public static final String ATTR_WORKSPACE_ID = "workspaceId";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        log.debug("WebSocket handshake started: {}", request.getURI());

        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpRequest = servletRequest.getServletRequest();

            // 提取 Token
            String token = extractToken(request);
            if (token == null || token.isBlank()) {
                log.warn("WebSocket handshake failed: No token provided");
                return false;
            }

            try {
                // 调用 user 模块验证 Token
                TokenValidateRequest tokenRequest = new TokenValidateRequest(token);
                Result<TokenValidateResponse> result = userFeignClient.validateToken(tokenRequest);

                if (result == null || !result.isSuccess() || result.getData() == null) {
                    log.warn("WebSocket handshake failed: Token validation failed");
                    return false;
                }

                TokenValidateResponse tokenInfo = result.getData();
                if (!tokenInfo.isValid()) {
                    log.warn("WebSocket handshake failed: {}", tokenInfo.getErrorMessage());
                    return false;
                }

                // 将用户信息存入 WebSocket 会话属性
                String userId = tokenInfo.getUserId();
                attributes.put(ATTR_USER_ID, userId);
                attributes.put(ATTR_USERNAME, tokenInfo.getUsername());
                attributes.put(ATTR_NICKNAME, tokenInfo.getNickname() != null ? tokenInfo.getNickname() : "");
                attributes.put(ATTR_AVATAR, tokenInfo.getAvatar() != null ? tokenInfo.getAvatar() : "");

                // 从请求参数中获取 workspaceId，并验证用户是否为该 workspace 成员
                String workspaceId = httpRequest.getParameter("workspaceId");
                if (workspaceId != null && !workspaceId.isBlank()) {
                    if (!validateWorkspaceMembership(workspaceId, userId)) {
                        log.warn("WebSocket handshake failed: userId={} is not a member of workspaceId={}",
                                userId, workspaceId);
                        return false;
                    }
                    attributes.put(ATTR_WORKSPACE_ID, workspaceId);
                }

                log.info("WebSocket handshake succeeded: userId={}, nickname={}, workspaceId={}",
                        userId, tokenInfo.getNickname(), workspaceId);
                return true;

            } catch (Exception e) {
                log.error("WebSocket handshake failed: Token validation error", e);
                return false;
            }
        }

        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        if (exception != null) {
            log.error("WebSocket 握手失败", exception);
        }
    }

    /**
     * 验证用户是否是 workspace 成员
     * fail-closed：调用失败时拒绝连接，防止服务降级时出现越权
     */
    private boolean validateWorkspaceMembership(String workspaceId, String userId) {
        try {
            Result<WorkspaceMembershipInfo> result = workspaceFeignClient.getMembership(workspaceId, userId);
            if (result == null || !result.isSuccess() || result.getData() == null) {
                log.warn("Workspace membership check failed: workspaceId={}, userId={}", workspaceId, userId);
                return false;
            }
            return result.getData().isMember();
        } catch (Exception e) {
            log.error("Workspace membership check error: workspaceId={}, userId={}", workspaceId, userId, e);
            return false;
        }
    }

    /**
     * 从请求中提取 Token
     * 优先级（安全性从高到低）：
     * 1. Sec-WebSocket-Protocol: Bearer.xxx（不进日志、不进浏览器历史）
     * 2. Authorization: Bearer xxx
     * 3. 查询参数 ?token=xxx / ?access_token=xxx（仅兜底，会打 warn 日志）
     */
    private String extractToken(ServerHttpRequest request) {
        // 优先级 1: Sec-WebSocket-Protocol（最安全）
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String secWebSocketProtocol = servletRequest.getServletRequest()
                    .getHeader("Sec-WebSocket-Protocol");
            if (secWebSocketProtocol != null && secWebSocketProtocol.startsWith("Bearer.")) {
                return secWebSocketProtocol.substring(7);
            }

            // 优先级 2: Authorization Header
            String authHeader = servletRequest.getServletRequest().getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                return authHeader.substring(7);
            }
        }

        // 优先级 3: URL 查询参数（兜底，Token 会出现在日志/浏览器历史中）
        String query = request.getURI().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] keyValue = param.split("=");
                if (keyValue.length == 2) {
                    String key = keyValue[0];
                    String value = keyValue[1];
                    if ("token".equals(key) || "access_token".equals(key)) {
                        log.warn("WebSocket token passed via URL query parameter — consider using Sec-WebSocket-Protocol instead");
                        return value;
                    }
                }
            }
        }

        return null;
    }
}
