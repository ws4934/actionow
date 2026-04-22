package com.actionow.common.security.workspace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作空间成员身份信息
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceMembershipInfo {

    /**
     * 工作空间ID
     */
    private String workspaceId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 是否是成员
     */
    private boolean member;

    /**
     * 成员角色（OWNER/ADMIN/EDITOR/VIEWER）
     */
    private String role;

    /**
     * 租户Schema
     */
    private String tenantSchema;

    /**
     * 工作空间名称
     */
    private String workspaceName;
}
