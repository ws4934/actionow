package com.actionow.common.security.jwt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Set;

/**
 * JWT Claims 数据
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JwtClaims implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 角色列表
     */
    private Set<String> roles;

    /**
     * 会话ID（SID）
     */
    private String sessionId;

    /**
     * 当前工作空间ID（WID）
     */
    private String workspaceId;

    /**
     * 当前租户Schema（SCH）
     */
    private String tenantSchema;

    /**
     * 权限版本号
     */
    private Integer permVersion;

    /**
     * 设备ID
     */
    private String deviceId;

    /**
     * 签发时间
     */
    private Long issuedAt;

    /**
     * 过期时间
     */
    private Long expiration;

    /**
     * Token ID
     */
    private String tokenId;

    /**
     * Token 类型 (access/refresh)
     */
    private String tokenType;
}
