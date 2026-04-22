package com.actionow.common.core.context;

import lombok.Data;

import java.io.Serializable;
import java.util.Set;

/**
 * 用户上下文信息
 *
 * @author Actionow
 */
@Data
public class UserContext implements Serializable {

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
     * 当前工作空间ID
     */
    private String workspaceId;

    /**
     * 当前工作空间角色（OWNER/ADMIN/EDITOR/VIEWER）
     */
    private String workspaceRole;

    /**
     * 租户Schema
     */
    private String tenantSchema;

    /**
     * 用户角色列表
     */
    private Set<String> roles;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 请求ID
     */
    private String requestId;

    /**
     * 客户端IP
     */
    private String clientIp;

    /**
     * 是否系统用户
     */
    public boolean isSystemUser() {
        return "SYSTEM".equals(userId);
    }
}
