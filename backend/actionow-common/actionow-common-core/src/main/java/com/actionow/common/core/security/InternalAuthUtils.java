package com.actionow.common.core.security;

import com.actionow.common.core.id.UuidGenerator;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;

/**
 * 内部服务认证工具类
 * 提供两种能力：
 * 1. 旧版 HMAC-SHA256 签名（兼容保留）
 * 2. 高安全内部短JWT（推荐）
 *
 * @author Actionow
 */
public final class InternalAuthUtils {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SEPARATOR = "|";
    private static final String INTERNAL_TOKEN_ISSUER = "actionow-internal";
    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_WORKSPACE_ID = "workspaceId";
    private static final String CLAIM_TENANT_SCHEMA = "tenantSchema";

    private InternalAuthUtils() {
    }

    /**
     * 生成内部短JWT
     */
    public static String generateInternalToken(String secret,
                                               String userId,
                                               String workspaceId,
                                               String tenantSchema,
                                               long expireSeconds) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("Internal auth secret must not be blank");
        }
        long ttl = expireSeconds > 0 ? expireSeconds : 60;
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(ttl);

        var builder = Jwts.builder()
                .issuer(INTERNAL_TOKEN_ISSUER)
                .id(UuidGenerator.generateShortId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp));

        if (userId != null && !userId.isBlank()) {
            builder.claim(CLAIM_USER_ID, userId);
        }
        if (workspaceId != null && !workspaceId.isBlank()) {
            builder.claim(CLAIM_WORKSPACE_ID, workspaceId);
        }
        if (tenantSchema != null && !tenantSchema.isBlank()) {
            builder.claim(CLAIM_TENANT_SCHEMA, tenantSchema);
        }

        return builder
                .signWith(getSigningKey(secret))
                .compact();
    }

    /**
     * 校验并解析内部短JWT，失败返回 null
     */
    public static InternalAuthTokenClaims verifyAndParseInternalToken(String secret, String token) {
        if (secret == null || secret.isBlank() || token == null || token.isBlank()) {
            return null;
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey(secret))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!INTERNAL_TOKEN_ISSUER.equals(claims.getIssuer())) {
                return null;
            }

            Date issuedAt = claims.getIssuedAt();
            Date expiration = claims.getExpiration();

            return new InternalAuthTokenClaims(
                    claims.getId(),
                    claims.get(CLAIM_USER_ID, String.class),
                    claims.get(CLAIM_WORKSPACE_ID, String.class),
                    claims.get(CLAIM_TENANT_SCHEMA, String.class),
                    issuedAt != null ? issuedAt.toInstant() : null,
                    expiration != null ? expiration.toInstant() : null
            );
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 生成HMAC-SHA256签名
     *
     * @param secret       密钥
     * @param userId       用户ID（可为null）
     * @param workspaceId  工作空间ID（可为null）
     * @param tenantSchema 租户Schema（可为null）
     * @param timestamp    时间戳（毫秒）
     * @return 签名的十六进制字符串
     */
    public static String generateSignature(String secret, String userId, String workspaceId,
                                           String tenantSchema, String timestamp) {
        String data = buildSignData(userId, workspaceId, tenantSchema, timestamp);
        return hmacSha256(secret, data);
    }

    /**
     * 验证HMAC-SHA256签名
     *
     * @param secret        密钥
     * @param signature     待验证的签名
     * @param userId        用户ID（可为null）
     * @param workspaceId   工作空间ID（可为null）
     * @param tenantSchema  租户Schema（可为null）
     * @param timestamp     时间戳（毫秒）
     * @param maxAgeSeconds 签名最大有效期（秒）
     * @return 签名是否有效
     */
    public static boolean verifySignature(String secret, String signature, String userId,
                                          String workspaceId, String tenantSchema,
                                          String timestamp, long maxAgeSeconds) {
        if (secret == null || signature == null || timestamp == null) {
            return false;
        }

        // 检查时间戳是否在有效期内
        try {
            long ts = Long.parseLong(timestamp);
            long now = Instant.now().toEpochMilli();
            long ageMs = Math.abs(now - ts);
            if (ageMs > maxAgeSeconds * 1000) {
                return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }

        // 重新计算签名并进行恒定时间比较
        String expectedSignature = generateSignature(secret, userId, workspaceId, tenantSchema, timestamp);
        return MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String buildSignData(String userId, String workspaceId,
                                        String tenantSchema, String timestamp) {
        return nullSafe(userId) + SEPARATOR
                + nullSafe(workspaceId) + SEPARATOR
                + nullSafe(tenantSchema) + SEPARATOR
                + nullSafe(timestamp);
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static String hmacSha256(String secret, String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static SecretKey getSigningKey(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
