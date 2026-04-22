package com.actionow.common.core.security;

import java.time.Instant;

/**
 * 内部服务短JWT Claims
 */
public record InternalAuthTokenClaims(
        String tokenId,
        String userId,
        String workspaceId,
        String tenantSchema,
        Instant issuedAt,
        Instant expiration
) {
}

