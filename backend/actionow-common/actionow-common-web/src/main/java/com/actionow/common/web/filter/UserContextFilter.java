package com.actionow.common.web.filter;

import com.actionow.common.core.constant.CommonConstants;
import com.actionow.common.core.context.UserContext;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.id.UuidGenerator;
import com.actionow.common.core.security.InternalAuthTokenClaims;
import com.actionow.common.core.security.InternalAuthProperties;
import com.actionow.common.core.security.InternalAuthUtils;
import com.actionow.common.core.util.WebUtils;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户上下文过滤器
 * 从请求头中提取用户信息并设置到上下文中
 * 验证内部短JWT后才信任上下文头
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class UserContextFilter extends OncePerRequestFilter {

    /**
     * 请求属性键：保存初始请求构建的 UserContext，供 async completion dispatch 复用。
     * SSE/DeferredResult 等异步端点完成时，Servlet 容器会发起 DispatcherType.ASYNC 的二次 dispatch，
     * 此时原始请求头中的内部 JWT 可能已过期，因此从 request attribute 恢复上下文而非重新解析。
     */
    private static final String SAVED_CONTEXT_ATTR = UserContextFilter.class.getName() + ".SAVED_CONTEXT";

    private final InternalAuthProperties internalAuthProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            UserContext context;
            if (request.getDispatcherType() == DispatcherType.ASYNC) {
                // Async completion dispatch：从初始请求保存的属性中恢复上下文，
                // 避免因内部 JWT 过期导致上下文丢失
                context = (UserContext) request.getAttribute(SAVED_CONTEXT_ATTR);
                if (context == null) {
                    context = new UserContext();
                    context.setRequestId(UuidGenerator.generateShortId());
                }
            } else {
                // 正常请求：从请求头构建上下文，并保存供后续 async dispatch 使用
                context = buildUserContext(request);
                request.setAttribute(SAVED_CONTEXT_ATTR, context);
            }
            UserContextHolder.setContext(context);

            // 设置响应头
            response.setHeader(CommonConstants.HEADER_REQUEST_ID, context.getRequestId());

            filterChain.doFilter(request, response);
        } finally {
            // 清理上下文
            UserContextHolder.clear();
        }
    }

    private UserContext buildUserContext(HttpServletRequest request) {
        UserContext context = new UserContext();

        // 请求ID，优先从请求头获取，没有则生成新的
        String requestId = request.getHeader(CommonConstants.HEADER_REQUEST_ID);
        if (requestId == null || requestId.isBlank()) {
            requestId = UuidGenerator.generateShortId();
        }
        context.setRequestId(requestId);

        // 客户端IP
        context.setClientIp(getClientIp(request));

        // 无需用户上下文的路径（actuator等），直接返回基础上下文
        String path = request.getRequestURI();
        if (path.startsWith("/actuator")) {
            return context;
        }

        // 校验内部令牌，失败时不信任上下文头
        InternalAuthTokenClaims claims = verifyInternalToken(request);
        if (claims == null) {
            if (path.startsWith("/internal/")) {
                log.error("内部服务调用 JWT 验证失败，租户上下文将丢失！" +
                                "请检查 INTERNAL_AUTH_SECRET 配置是否一致。path={}, ip={}, " +
                                "hasToken={}, configuredSecret={}",
                        path, context.getClientIp(),
                        request.getHeader("X-Internal-Auth-Token") != null,
                        internalAuthProperties.isConfigured() ? "yes(len="
                                + internalAuthProperties.getAuthSecret().length() + ")" : "NO");
            } else {
                log.warn("Invalid or missing internal token, clearing context: path={}, ip={}",
                        path, context.getClientIp());
            }
            return context; // 仅返回requestId和clientIp
        }

        // 令牌验证通过，优先信任token claims
        if (claims.userId() != null && !claims.userId().isBlank()) {
            context.setUserId(claims.userId());
        }

        // 会话ID
        String sessionId = request.getHeader(CommonConstants.HEADER_SESSION_ID);
        if (sessionId != null && !sessionId.isBlank()) {
            context.setSessionId(sessionId);
        }

        // 用户名
        String username = request.getHeader(CommonConstants.HEADER_USERNAME);
        if (username != null && !username.isBlank()) {
            context.setUsername(username);
        }

        // 用户角色
        String role = request.getHeader(CommonConstants.HEADER_USER_ROLE);
        if (role != null && !role.isBlank()) {
            context.setRoles(Arrays.stream(role.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet()));
        }

        // 工作空间ID（来自token claims）
        if (claims.workspaceId() != null && !claims.workspaceId().isBlank()) {
            context.setWorkspaceId(claims.workspaceId());
        }

        // 租户Schema（来自token claims）
        if (claims.tenantSchema() != null && !claims.tenantSchema().isBlank()) {
            context.setTenantSchema(claims.tenantSchema());
        }

        return context;
    }

    /**
     * 验证内部服务短JWT
     */
    private InternalAuthTokenClaims verifyInternalToken(HttpServletRequest request) {
        if (!internalAuthProperties.isConfigured()) {
            log.error("Internal auth secret not configured, rejecting context trust");
            return null;
        }

        String internalToken = request.getHeader(CommonConstants.HEADER_INTERNAL_AUTH_TOKEN);
        if (internalToken == null || internalToken.isBlank()) {
            return null;
        }

        return InternalAuthUtils.verifyAndParseInternalToken(
                internalAuthProperties.getAuthSecret(),
                internalToken
        );
    }

    private String getClientIp(HttpServletRequest request) {
        return WebUtils.getClientIp(
                request.getHeader("X-Forwarded-For"),
                request.getHeader("X-Real-IP"),
                request.getRemoteAddr()
        );
    }
}
