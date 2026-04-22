package com.actionow.common.security.jwt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Token 响应数据
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Access Token
     */
    private String accessToken;

    /**
     * Refresh Token
     */
    private String refreshToken;

    /**
     * Token 类型
     */
    private String tokenType;

    /**
     * Access Token 过期时间（秒）
     */
    private Long expiresIn;

    /**
     * Refresh Token 过期时间（秒）
     */
    private Long refreshExpiresIn;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 当前工作空间ID
     */
    private String workspaceId;
}
