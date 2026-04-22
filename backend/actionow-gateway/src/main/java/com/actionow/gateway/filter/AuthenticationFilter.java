package com.actionow.gateway.filter;

import com.actionow.common.core.constant.CommonConstants;
import com.actionow.common.core.id.UuidGenerator;
import com.actionow.common.core.result.ResultCode;
import com.actionow.common.core.security.InternalAuthProperties;
import com.actionow.common.core.security.InternalAuthUtils;
import com.actionow.gateway.util.FilterResponseUtils;
import com.actionow.gateway.util.ReactiveWebUtils;
import com.actionow.gateway.config.ActionowGatewayProperties;
import com.actionow.gateway.service.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 认证过滤器
 * 负责JWT Token验证、黑名单检查和用户信息注入
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationFilter implements GlobalFilter, Ordered {

    private static final Pattern WORKSPACE_PATH_PATTERN = Pattern.compile("/workspaces?/([0-9a-fA-F\\-]{36})");
    private static final Pattern WORKSPACE_SWITCH_PATH_PATTERN =
            Pattern.compile("^/api/user/auth/workspaces/[0-9a-fA-F\\-]{36}/switch$");

    private final ActionowGatewayProperties gatewayProperties;
    private final TokenBlacklistService tokenBlacklistService;
    private final InternalAuthProperties internalAuthProperties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // 显式拦截内部服务接口，无论来自直接路由还是 Discovery Locator
        // 形如 /internal/** 或 /服务名/internal/** 均拒绝访问
        if (isInternalPath(path)) {
            log.warn("外部请求尝试访问内部接口，已拦截: path={}, ip={}",
                    path, ReactiveWebUtils.getClientIp(request));
            return forbidden(exchange, "Access to internal API is not allowed");
        }

        // 生成请求ID
        String requestId = UuidGenerator.generateShortId();
        // 白名单路径直接放行（但仍注入基础请求头、内部认证token，并清除敏感头）
        if (isWhitelisted(path)) {
            if (!internalAuthProperties.isConfigured()) {
                log.error("内部认证密钥未配置，拒绝转发白名单请求: path={}", path);
                return internalServerError(exchange, "Internal service authentication is not configured");
            }

            String internalToken = InternalAuthUtils.generateInternalToken(
                    internalAuthProperties.getAuthSecret(),
                    null,
                    null,
                    null,
                    internalAuthProperties.getInternalTokenExpireSeconds()
            );

            ServerHttpRequest modifiedRequest = request.mutate()
                    .headers(headers -> stripSensitiveHeaders(headers))
                    .header(CommonConstants.HEADER_REQUEST_ID, requestId)
                    .header(CommonConstants.HEADER_INTERNAL_AUTH_TOKEN, internalToken)
                    .build();
            return chain.filter(exchange.mutate().request(modifiedRequest).build());
        }

        // 获取 Token
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(gatewayProperties.getJwt().getPrefix())) {
            log.warn("请求未携带Token: path={}, ip={}", path, ReactiveWebUtils.getClientIp(request));
            return unauthorized(exchange, "Missing or invalid authorization header");
        }

        String token = authHeader.substring(gatewayProperties.getJwt().getPrefix().length());

        // 验证 Token
        Claims claims;
        try {
            claims = parseToken(token);
        } catch (ExpiredJwtException e) {
            log.info("Token已过期: path={}, expiredAt={}", path, e.getClaims().getExpiration());
            return unauthorized(exchange, "Token expired");
        } catch (Exception e) {
            log.warn("Token验证失败: path={}, error={}", path, e.getMessage());
            return unauthorized(exchange, "Invalid token");
        }

        // 从Token中提取必要信息
        String jti = claims.getId(); // JWT ID，用于黑名单检查
        String userId = claims.get("userId", String.class);
        String sessionId = claims.get("sessionId", String.class);
        Long issuedAtMillis = extractIssuedAtMillis(claims);

        // 检查Token是否在黑名单中（JTI → Session → User 三层检查）
        return checkTokenBlacklist(jti, userId, sessionId, issuedAtMillis)
                .flatMap(isBlacklisted -> {
                    if (isBlacklisted) {
                        log.warn("Token已被撤销: jti={}, userId={}, path={}", jti, userId, path);
                        return unauthorized(exchange, "Token has been revoked");
                    }

                    // Token有效，继续处理
                    return proceedWithValidToken(exchange, chain, request, claims, requestId, path);
                });
    }

    /**
     * 检查Token是否在黑名单中（三层检查：JTI → Session → User）
     */
    private Mono<Boolean> checkTokenBlacklist(String jti, String userId, String sessionId, Long issuedAtMillis) {
        // 1. 检查JTI黑名单（单个Token撤销）
        Mono<Boolean> jtiCheck = (jti != null && !jti.isEmpty())
                ? tokenBlacklistService.isBlacklisted(jti)
                : Mono.just(false);

        // 2. 检查会话级撤销（单个会话的Token被撤销）
        Mono<Boolean> sessionCheck = (sessionId != null && !sessionId.isEmpty() && issuedAtMillis != null)
                ? tokenBlacklistService.isSessionTokenRevoked(sessionId, issuedAtMillis)
                : Mono.just(false);

        // 3. 检查用户级撤销（用户所有Token被撤销）
        Mono<Boolean> userCheck = (userId != null && !userId.isEmpty() && issuedAtMillis != null)
                ? tokenBlacklistService.isUserTokenRevoked(userId, issuedAtMillis)
                : Mono.just(false);

        // 任一检查命中则视为Token被撤销
        return Mono.zip(jtiCheck, sessionCheck, userCheck)
                .map(tuple -> tuple.getT1() || tuple.getT2() || tuple.getT3());
    }

    private Long extractIssuedAtMillis(Claims claims) {
        Long iatMs = parseLongClaim(claims.get("iatMs"));
        if (iatMs != null) {
            return iatMs;
        }
        Date issuedAt = claims.getIssuedAt();
        return issuedAt != null ? issuedAt.getTime() : null;
    }

    private Long parseLongClaim(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Long.parseLong(stringValue);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * Token验证通过后的处理逻辑
     */
    private Mono<Void> proceedWithValidToken(ServerWebExchange exchange, GatewayFilterChain chain,
                                              ServerHttpRequest request, Claims claims,
                                              String requestId, String path) {
        // 从Token中提取用户信息
        String userId = claims.get("userId", String.class);
        String username = claims.get("username", String.class);
        String email = claims.get("email", String.class);
        String role = claims.get("role", String.class);
        String sessionId = claims.get("sessionId", String.class);

        // 工作空间和租户信息：仅信任JWT claims，不再信任query/header中的workspace参数
        String workspaceId = claims.get("workspaceId", String.class);
        String tenantSchema = claims.get("tenantSchema", String.class);

        // 检测客户端伪造workspace参数（query/header）
        String queryWorkspaceId = request.getQueryParams().getFirst("workspaceId");
        String headerWorkspaceId = request.getHeaders().getFirst(CommonConstants.HEADER_WORKSPACE_ID);

        if (isWorkspaceSpoofed(workspaceId, queryWorkspaceId, headerWorkspaceId)) {
            log.warn("检测到workspaceId伪造请求: userId={}, tokenWorkspaceId={}, queryWorkspaceId={}, headerWorkspaceId={}, path={}",
                    userId, workspaceId, queryWorkspaceId, headerWorkspaceId, path);
            return forbidden(exchange, "Workspace context must come from token claim only");
        }

        String pathWorkspaceId = extractWorkspaceIdFromPath(path);
        if (pathWorkspaceId != null && !pathWorkspaceId.isBlank() && !isWorkspaceSwitchPath(path)) {
            if (workspaceId == null || workspaceId.isBlank()) {
                log.warn("路径包含workspaceId但token缺失workspace上下文: userId={}, pathWorkspaceId={}, path={}",
                        userId, pathWorkspaceId, path);
                return forbidden(exchange, "Workspace context missing in token");
            }
            if (!pathWorkspaceId.equals(workspaceId)) {
                log.warn("路径workspaceId与token不一致: userId={}, tokenWorkspaceId={}, pathWorkspaceId={}, path={}",
                        userId, workspaceId, pathWorkspaceId, path);
                return forbidden(exchange, "Workspace mismatch between path and token");
            }
        }

        // 系统工作空间使用固定 schema；其他工作空间不再从 workspaceId 推导 schema，
        // 避免与真实 schemaName 不一致（schemaName 应由下游基于 workspace 元数据解析）
        if (CommonConstants.SYSTEM_WORKSPACE_ID.equals(workspaceId)
                && (tenantSchema == null || tenantSchema.isBlank())) {
            tenantSchema = CommonConstants.SYSTEM_TENANT_SCHEMA;
            log.debug("使用系统工作空间默认tenantSchema: workspaceId={}, tenantSchema={}", workspaceId, tenantSchema);
        } else if (workspaceId != null && !workspaceId.isBlank()
                && (tenantSchema == null || tenantSchema.isBlank())) {
            log.debug("tenantSchema缺失，保持为空并由下游服务按schemaName解析: workspaceId={}", workspaceId);
        }

        if (!internalAuthProperties.isConfigured()) {
            log.error("内部认证密钥未配置，拒绝转发认证请求: userId={}, path={}", userId, path);
            return internalServerError(exchange, "Internal service authentication is not configured");
        }
        String internalToken = InternalAuthUtils.generateInternalToken(
                internalAuthProperties.getAuthSecret(),
                userId,
                workspaceId,
                tenantSchema,
                internalAuthProperties.getInternalTokenExpireSeconds()
        );

        // 构建新请求：先清除所有敏感头，再注入经过验证的值
        ServerHttpRequest.Builder requestBuilder = request.mutate()
                .headers(headers -> stripSensitiveHeaders(headers))
                .header(CommonConstants.HEADER_REQUEST_ID, requestId)
                .header(CommonConstants.HEADER_INTERNAL_AUTH_TOKEN, internalToken);

        // 注入用户基础信息
        if (userId != null) {
            requestBuilder.header(CommonConstants.HEADER_USER_ID, userId);
        }
        if (username != null) {
            requestBuilder.header(CommonConstants.HEADER_USERNAME, username);
        }
        if (email != null) {
            requestBuilder.header(CommonConstants.HEADER_USER_EMAIL, email);
        }

        // 注入工作空间和租户信息
        if (workspaceId != null && !workspaceId.isBlank()) {
            requestBuilder.header(CommonConstants.HEADER_WORKSPACE_ID, workspaceId);
        }
        if (tenantSchema != null && !tenantSchema.isBlank()) {
            requestBuilder.header(CommonConstants.HEADER_TENANT_SCHEMA, tenantSchema);
        }

        // 注入用户角色
        if (role != null) {
            requestBuilder.header(CommonConstants.HEADER_USER_ROLE, role);
        }

        // 注入会话ID
        if (sessionId != null && !sessionId.isBlank()) {
            requestBuilder.header(CommonConstants.HEADER_SESSION_ID, sessionId);
        }

        ServerHttpRequest modifiedRequest = requestBuilder.build();

        log.debug("用户认证成功: userId={}, username={}, workspaceId={}, tenantSchema={}, path={}",
                userId, username, workspaceId, tenantSchema, path);
        return chain.filter(exchange.mutate().request(modifiedRequest).build());
    }

    private boolean isWorkspaceSpoofed(String tokenWorkspaceId, String queryWorkspaceId, String headerWorkspaceId) {
        if (queryWorkspaceId != null && !queryWorkspaceId.isBlank()
                && !queryWorkspaceId.equals(tokenWorkspaceId)) {
            return true;
        }
        return headerWorkspaceId != null && !headerWorkspaceId.isBlank()
                && !headerWorkspaceId.equals(tokenWorkspaceId);
    }

    private String extractWorkspaceIdFromPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        Matcher matcher = WORKSPACE_PATH_PATTERN.matcher(path);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * 切换工作空间接口属于“上下文变更”入口，允许路径中的 workspaceId 与当前 token 上下文不同或为空。
     */
    private boolean isWorkspaceSwitchPath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        return WORKSPACE_SWITCH_PATH_PATTERN.matcher(path).matches();
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private boolean isWhitelisted(String path) {
        if (gatewayProperties.getWhitelist() == null) {
            return false;
        }
        return gatewayProperties.getWhitelist().stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    /**
     * 清除客户端可能伪造的敏感请求头
     */
    private void stripSensitiveHeaders(HttpHeaders headers) {
        headers.remove(CommonConstants.HEADER_USER_ID);
        headers.remove(CommonConstants.HEADER_USERNAME);
        headers.remove(CommonConstants.HEADER_USER_EMAIL);
        headers.remove(CommonConstants.HEADER_USER_ROLE);
        headers.remove(CommonConstants.HEADER_WORKSPACE_ID);
        headers.remove(CommonConstants.HEADER_TENANT_SCHEMA);
        headers.remove(CommonConstants.HEADER_GATEWAY_VERSION);
        headers.remove(CommonConstants.HEADER_GATEWAY_TIME);
        headers.remove(CommonConstants.HEADER_GATEWAY_SIGNATURE);
        headers.remove(CommonConstants.HEADER_INTERNAL_AUTH_TOKEN);
        headers.remove(CommonConstants.HEADER_SESSION_ID);
    }

    /**
     * 检查是否是内部接口路径
     * 拦截直接访问 /internal/** 的请求，以及通过 Discovery Locator
     * 访问的 /服务名/internal/** 形式的请求
     */
    private boolean isInternalPath(String path) {
        if (path == null) {
            return false;
        }
        // 直接以 /internal/ 开头
        if (path.startsWith("/internal/") || path.equals("/internal")) {
            return true;
        }
        // 通过 Discovery Locator: /{service-name}/internal/...
        // 形如 /actionow-wallet/internal/wallet/...
        int secondSlash = path.indexOf('/', 1);
        if (secondSlash > 0) {
            String remainder = path.substring(secondSlash);
            return remainder.startsWith("/internal/") || remainder.equals("/internal");
        }
        return false;
    }

    private Mono<Void> forbidden(ServerWebExchange exchange, String reason) {
        return FilterResponseUtils.writeErrorResponse(exchange, HttpStatus.FORBIDDEN,
                ResultCode.FORBIDDEN, reason);
    }

    private Claims parseToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(
                gatewayProperties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8)
        );
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String reason) {
        ResultCode code = "Token expired".equals(reason) ? ResultCode.TOKEN_EXPIRED : ResultCode.UNAUTHORIZED;
        return FilterResponseUtils.writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED,
                code, reason);
    }

    private Mono<Void> internalServerError(ServerWebExchange exchange, String reason) {
        return FilterResponseUtils.writeErrorResponse(exchange, HttpStatus.INTERNAL_SERVER_ERROR,
                ResultCode.INTERNAL_ERROR, reason);
    }
}
