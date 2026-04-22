package com.actionow.gateway.service;

import com.actionow.common.core.constant.RedisKeyConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

/**
 * Token黑名单服务
 * 用于管理已注销或已撤销的JWT Token
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    /**
     * 将Token加入黑名单
     *
     * @param jti           Token的唯一标识符（JWT ID）
     * @param expirationTime Token的过期时间
     * @return 操作结果
     */
    public Mono<Boolean> addToBlacklist(String jti, Instant expirationTime) {
        if (jti == null || jti.isEmpty()) {
            return Mono.just(false);
        }

        String key = RedisKeyConstants.TOKEN_BLACKLIST + jti;

        // 计算剩余过期时间，Token过期后自动从黑名单移除
        Duration ttl = Duration.between(Instant.now(), expirationTime);
        if (ttl.isNegative() || ttl.isZero()) {
            // Token已过期，无需加入黑名单
            return Mono.just(true);
        }

        return reactiveRedisTemplate.opsForValue()
                .set(key, String.valueOf(Instant.now().toEpochMilli()), ttl)
                .doOnSuccess(result -> log.debug("Token added to blacklist: jti={}, ttl={}s", jti, ttl.toSeconds()))
                .onErrorResume(e -> {
                    log.error("Failed to add token to blacklist: jti={}", jti, e);
                    return Mono.just(false);
                });
    }

    /**
     * 检查Token是否在黑名单中
     *
     * @param jti Token的唯一标识符（JWT ID）
     * @return 是否在黑名单中
     */
    public Mono<Boolean> isBlacklisted(String jti) {
        if (jti == null || jti.isEmpty()) {
            return Mono.just(false);
        }

        String key = RedisKeyConstants.TOKEN_BLACKLIST + jti;
        return reactiveRedisTemplate.hasKey(key)
                .doOnNext(blacklisted -> {
                    if (blacklisted) {
                        log.debug("Token found in blacklist: jti={}", jti);
                    }
                });
    }

    /**
     * 撤销用户的所有Token（通过记录撤销时间戳）
     * 所有在此时间戳之前颁发的Token都将被视为无效
     *
     * @param userId 用户ID
     * @param ttl    撤销记录的保留时间（通常与最长Token过期时间一致）
     * @return 操作结果
     */
    public Mono<Boolean> revokeAllUserTokens(String userId, Duration ttl) {
        if (userId == null || userId.isEmpty()) {
            return Mono.just(false);
        }

        String key = RedisKeyConstants.TOKEN_BLACKLIST_USER + userId;
        String timestamp = String.valueOf(Instant.now().toEpochMilli());

        return reactiveRedisTemplate.opsForValue()
                .set(key, timestamp, ttl)
                .doOnSuccess(result -> log.info("All tokens revoked for user: userId={}", userId))
                .onErrorResume(e -> {
                    log.error("Failed to revoke all tokens for user: userId={}", userId, e);
                    return Mono.just(false);
                });
    }

    /**
     * 检查Token是否被用户级别撤销
     *
     * @param userId    用户ID
     * @param issuedAt  Token的颁发时间（毫秒时间戳）
     * @return 是否被撤销
     */
    public Mono<Boolean> isUserTokenRevoked(String userId, long issuedAt) {
        if (userId == null || userId.isEmpty()) {
            return Mono.just(false);
        }

        String key = RedisKeyConstants.TOKEN_BLACKLIST_USER + userId;
        return reactiveRedisTemplate.opsForValue()
                .get(key)
                .map(revokedAtStr -> {
                    Long revokedAt = parseRevokedAt(revokedAtStr);
                    if (revokedAt == null) {
                        log.warn("Invalid user revoke timestamp in Redis, reject token by default: userId={}, rawValue={}",
                                userId, revokedAtStr);
                        return true;
                    }
                    long tokenIssuedAtMillis = issuedAt;
                    long revokedAtMillis = normalizeToEpochMillis(revokedAt);
                    // 如果Token颁发时间早于撤销时间，则视为已撤销
                    return tokenIssuedAtMillis < revokedAtMillis;
                })
                .defaultIfEmpty(false)
                .doOnNext(revoked -> {
                    if (revoked) {
                        log.debug("Token revoked by user-level revocation: userId={}", userId);
                    }
                });
    }

    /**
     * 解析用户级撤销时间戳。
     * 兼容 Redis JSON 序列化后的字符串值（例如 "\"1772642688111\""）。
     */
    private Long parseRevokedAt(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String normalized = rawValue.trim();
        // 去除可能存在的外层引号（支持多层）
        while (normalized.length() >= 2
                && ((normalized.startsWith("\"") && normalized.endsWith("\""))
                || (normalized.startsWith("'") && normalized.endsWith("'")))) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        if (normalized.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(normalized);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private long normalizeToEpochMillis(long timestamp) {
        // 10位秒值 -> 毫秒；13位毫秒保持不变
        return timestamp < 1_000_000_000_000L ? timestamp * 1000 : timestamp;
    }

    /**
     * 检查Token是否被会话级别撤销
     *
     * @param sessionId  会话ID
     * @param issuedAt   Token的颁发时间（毫秒时间戳）
     * @return 是否被撤销
     */
    public Mono<Boolean> isSessionTokenRevoked(String sessionId, long issuedAt) {
        if (sessionId == null || sessionId.isEmpty()) {
            return Mono.just(false);
        }

        String key = RedisKeyConstants.TOKEN_BLACKLIST_SESSION + sessionId;
        return reactiveRedisTemplate.opsForValue()
                .get(key)
                .map(revokedAtStr -> {
                    Long revokedAt = parseRevokedAt(revokedAtStr);
                    if (revokedAt == null) {
                        log.warn("Invalid session revoke timestamp in Redis, reject token by default: sessionId={}, rawValue={}",
                                sessionId, revokedAtStr);
                        return true;
                    }
                    long revokedAtMillis = normalizeToEpochMillis(revokedAt);
                    return issuedAt < revokedAtMillis;
                })
                .defaultIfEmpty(false)
                .doOnNext(revoked -> {
                    if (revoked) {
                        log.debug("Token revoked by session-level revocation: sessionId={}", sessionId);
                    }
                });
    }

    /**
     * 从黑名单中移除Token（用于测试或特殊场景）
     *
     * @param jti Token的唯一标识符
     * @return 操作结果
     */
    public Mono<Boolean> removeFromBlacklist(String jti) {
        if (jti == null || jti.isEmpty()) {
            return Mono.just(false);
        }

        String key = RedisKeyConstants.TOKEN_BLACKLIST + jti;
        return reactiveRedisTemplate.delete(key)
                .map(count -> count > 0)
                .doOnSuccess(result -> log.debug("Token removed from blacklist: jti={}, success={}", jti, result));
    }
}
