package com.actionow.collab.feign;

import lombok.Data;

/**
 * 工作空间成员身份信息（本地副本，对应 workspace 服务的同名 DTO）
 *
 * @author Actionow
 */
@Data
public class WorkspaceMembershipInfo {
    private String workspaceId;
    private String userId;
    private boolean member;
    private String role;
    private String tenantSchema;
    private String workspaceName;
}
