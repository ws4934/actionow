package com.actionow.common.web.filter;

import com.actionow.common.core.constant.CommonConstants;
import com.actionow.common.core.result.ResultCode;
import com.actionow.common.core.security.InternalAuthTokenClaims;
import com.actionow.common.core.security.InternalAuthProperties;
import com.actionow.common.core.security.InternalAuthUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 内部API保护过滤器
 * 拦截所有 /internal/** 请求，验证内部短JWT
 * 确保内部API仅可由受信服务间调用访问
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class InternalApiProtectionFilter extends OncePerRequestFilter {

    private final InternalAuthProperties internalAuthProperties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/internal/") && !path.equals("/internal");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!internalAuthProperties.isConfigured()) {
            log.error("Internal API access denied - internal auth secret is not configured: path={}",
                    request.getRequestURI());
            sendForbidden(response, "Internal service authentication is not configured");
            return;
        }

        String internalToken = request.getHeader(CommonConstants.HEADER_INTERNAL_AUTH_TOKEN);

        if (internalToken == null || internalToken.isBlank()) {
            log.warn("Internal API access denied - missing internal token: path={}, remoteAddr={}",
                    request.getRequestURI(), request.getRemoteAddr());
            sendForbidden(response, "Access to internal API requires valid internal token");
            return;
        }

        InternalAuthTokenClaims claims = InternalAuthUtils.verifyAndParseInternalToken(
                internalAuthProperties.getAuthSecret(),
                internalToken
        );

        if (claims == null) {
            log.warn("Internal API access denied - invalid internal token: path={}, remoteAddr={}",
                    request.getRequestURI(), request.getRemoteAddr());
            sendForbidden(response, "Invalid internal service token");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void sendForbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String body = "{\"code\":\"" + ResultCode.FORBIDDEN.getCode() + "\",\"message\":\"" + message + "\"}";
        response.getWriter().write(body);
    }
}
