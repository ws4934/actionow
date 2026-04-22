package com.actionow.common.security.jwt;

import com.actionow.common.core.id.UuidGenerator;
import com.actionow.common.security.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.io.Serializer;
import io.jsonwebtoken.jackson.io.JacksonSerializer;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JWT 工具类
 *
 * 注意: 缓存 SecretKey 和 JwtParser 实例以避免 JJWT ServiceLoader
 * 在 Spring Boot nested JAR 环境中运行一段时间后出现的问题
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtils {

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_NICKNAME = "nickname";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_SESSION_ID = "sessionId";
    private static final String CLAIM_WORKSPACE_ID = "workspaceId";
    private static final String CLAIM_TENANT_SCHEMA = "tenantSchema";
    private static final String CLAIM_PERM_VERSION = "permVersion";
    private static final String CLAIM_DEVICE_ID = "deviceId";
    private static final String CLAIM_TOKEN_TYPE = "tokenType";
    private static final String CLAIM_ISSUED_AT_MS = "iatMs";

    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";

    private final JwtProperties jwtProperties;

    /**
     * 缓存的签名密钥（避免重复创建）
     */
    private SecretKey cachedSigningKey;

    /**
     * 缓存的 JWT 解析器（避免 ServiceLoader 问题）
     */
    private JwtParser cachedJwtParser;

    /**
     * 缓存的 JSON 序列化器（避免 ServiceLoader 问题）
     */
    private Serializer<Map<String, ?>> cachedSerializer;

    /**
     * 初始化时预加载 SecretKey、JwtParser 和 Serializer
     * 这样可以在应用启动时触发 ServiceLoader，避免运行时问题
     */
    @PostConstruct
    public void init() {
        this.cachedSigningKey = createSigningKey();
        this.cachedSerializer = new JacksonSerializer<>();
        this.cachedJwtParser = Jwts.parser()
                .verifyWith(cachedSigningKey)
                .build();
        log.info("JwtUtils initialized: SecretKey, JwtParser and Serializer cached");
    }

    /**
     * 生成 Token 对
     */
    public TokenResponse generateTokenPair(JwtClaims claims) {
        String accessToken = generateAccessToken(claims);
        String refreshToken = generateRefreshToken(claims);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getAccessTokenExpire())
                .refreshExpiresIn(jwtProperties.getRefreshTokenExpire())
                .sessionId(claims.getSessionId())
                .workspaceId(claims.getWorkspaceId())
                .build();
    }

    /**
     * 生成 Access Token
     */
    public String generateAccessToken(JwtClaims claims) {
        return generateToken(claims, jwtProperties.getAccessTokenExpire(), TOKEN_TYPE_ACCESS);
    }

    /**
     * 生成 Refresh Token
     */
    public String generateRefreshToken(JwtClaims claims) {
        return generateToken(claims, jwtProperties.getRefreshTokenExpire(), TOKEN_TYPE_REFRESH);
    }

    private String generateToken(JwtClaims claims, long expireSeconds, String tokenType) {
        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(expireSeconds);
        String tokenId = UuidGenerator.generateShortId();

        return Jwts.builder()
                .json(cachedSerializer)
                .id(tokenId)
                .issuer(jwtProperties.getIssuer())
                .subject(claims.getUserId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .claim(CLAIM_ISSUED_AT_MS, now.toEpochMilli())
                .claim(CLAIM_USER_ID, claims.getUserId())
                .claim(CLAIM_USERNAME, claims.getUsername())
                .claim(CLAIM_NICKNAME, claims.getNickname())
                .claim(CLAIM_EMAIL, claims.getEmail())
                .claim(CLAIM_ROLES, claims.getRoles())
                .claim(CLAIM_SESSION_ID, claims.getSessionId())
                .claim(CLAIM_WORKSPACE_ID, claims.getWorkspaceId())
                .claim(CLAIM_TENANT_SCHEMA, claims.getTenantSchema())
                .claim(CLAIM_PERM_VERSION, claims.getPermVersion())
                .claim(CLAIM_DEVICE_ID, claims.getDeviceId())
                .claim(CLAIM_TOKEN_TYPE, tokenType)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 解析 Token（使用缓存的 JwtParser）
     */
    public JwtClaims parseToken(String token) {
        Claims claims = cachedJwtParser
                .parseSignedClaims(token)
                .getPayload();

        return JwtClaims.builder()
                .userId(claims.get(CLAIM_USER_ID, String.class))
                .username(claims.get(CLAIM_USERNAME, String.class))
                .nickname(claims.get(CLAIM_NICKNAME, String.class))
                .email(claims.get(CLAIM_EMAIL, String.class))
                .roles(parseRoles(claims.get(CLAIM_ROLES)))
                .sessionId(claims.get(CLAIM_SESSION_ID, String.class))
                .workspaceId(claims.get(CLAIM_WORKSPACE_ID, String.class))
                .tenantSchema(claims.get(CLAIM_TENANT_SCHEMA, String.class))
                .permVersion(claims.get(CLAIM_PERM_VERSION, Integer.class))
                .deviceId(claims.get(CLAIM_DEVICE_ID, String.class))
                .tokenId(claims.getId())
                .tokenType(claims.get(CLAIM_TOKEN_TYPE, String.class))
                .issuedAt(parseIssuedAtMillis(claims))
                .expiration(claims.getExpiration().getTime())
                .build();
    }

    private long parseIssuedAtMillis(Claims claims) {
        Long issuedAtMs = parseLongClaim(claims.get(CLAIM_ISSUED_AT_MS));
        if (issuedAtMs != null) {
            return issuedAtMs;
        }
        Date issuedAt = claims.getIssuedAt();
        return issuedAt != null ? issuedAt.getTime() : 0L;
    }

    private Long parseLongClaim(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String str) {
            try {
                return Long.parseLong(str);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Set<String> parseRoles(Object rolesObj) {
        if (rolesObj == null) {
            return new HashSet<>();
        }
        if (rolesObj instanceof List) {
            return new HashSet<>((List<String>) rolesObj);
        }
        return new HashSet<>();
    }

    /**
     * 验证 Token（使用缓存的 JwtParser）
     */
    public boolean validateToken(String token) {
        try {
            cachedJwtParser.parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("Token已过期: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("Token格式错误: {}", e.getMessage());
        } catch (SignatureException e) {
            log.warn("Token签名无效: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("Token验证失败: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 判断是否为 Access Token
     */
    public boolean isAccessToken(String token) {
        try {
            JwtClaims claims = parseToken(token);
            return TOKEN_TYPE_ACCESS.equals(claims.getTokenType());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 判断是否为 Refresh Token
     */
    public boolean isRefreshToken(String token) {
        try {
            JwtClaims claims = parseToken(token);
            return TOKEN_TYPE_REFRESH.equals(claims.getTokenType());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从请求头中提取 Token
     */
    public String extractToken(String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith(jwtProperties.getTokenPrefix())) {
            return authorizationHeader.substring(jwtProperties.getTokenPrefix().length());
        }
        return null;
    }

    /**
     * 获取 Token 剩余有效时间（秒）
     */
    public long getTokenRemainingTime(String token) {
        try {
            JwtClaims claims = parseToken(token);
            long expirationTime = claims.getExpiration();
            long currentTime = System.currentTimeMillis();
            return Math.max(0, (expirationTime - currentTime) / 1000);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 获取缓存的签名密钥
     */
    private SecretKey getSigningKey() {
        return cachedSigningKey;
    }

    /**
     * 创建签名密钥（仅在初始化时调用）
     */
    private SecretKey createSigningKey() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
