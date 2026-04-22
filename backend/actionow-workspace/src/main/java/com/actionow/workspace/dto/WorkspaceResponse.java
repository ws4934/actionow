package com.actionow.workspace.dto;

import com.actionow.common.core.constant.CommonConstants;
import com.actionow.workspace.entity.Workspace;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 工作空间响应
 *
 * @author Actionow
 */
@Data
public class WorkspaceResponse {

    /**
     * 工作空间ID
     */
    private String id;

    /**
     * 工作空间名称
     */
    private String name;

    /**
     * URL友好标识
     */
    private String slug;

    /**
     * 工作空间描述
     */
    private String description;

    /**
     * 工作空间Logo URL
     */
    private String logoUrl;

    /**
     * 创建者用户ID
     */
    private String ownerId;

    /**
     * 创建者信息
     */
    private OwnerInfo owner;

    /**
     * 租户Schema名称
     */
    private String schemaName;

    /**
     * 工作空间状态: Active, Suspended, Deleted
     */
    private String status;

    /**
     * 订阅计划类型: Free, Basic, Pro, Enterprise
     */
    private String planType;

    /**
     * 成员数量上限
     */
    private Integer maxMembers;

    /**
     * 当前成员数量
     */
    private Integer memberCount;

    /**
     * 空间级配置
     */
    private Map<String, Object> config;

    /**
     * 是否是系统工作空间
     */
    private Boolean isSystem;

    /**
     * 当前用户在工作空间的角色
     */
    private String myRole;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 从实体转换
     */
    public static WorkspaceResponse fromEntity(Workspace workspace) {
        WorkspaceResponse response = new WorkspaceResponse();
        response.setId(workspace.getId());
        response.setName(workspace.getName());
        response.setSlug(workspace.getSlug());
        response.setDescription(workspace.getDescription());
        response.setLogoUrl(workspace.getLogoUrl());
        response.setOwnerId(workspace.getOwnerId());
        response.setSchemaName(workspace.getSchemaName());
        response.setStatus(workspace.getStatus());
        response.setPlanType(workspace.getPlanType());
        response.setMaxMembers(workspace.getMaxMembers());
        response.setMemberCount(workspace.getMemberCount());
        response.setConfig(workspace.getConfig());
        response.setCreatedAt(workspace.getCreatedAt());
        response.setIsSystem(CommonConstants.SYSTEM_WORKSPACE_ID.equals(workspace.getId()));
        return response;
    }

    /**
     * 创建者信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OwnerInfo {
        /**
         * 用户ID
         */
        private String id;

        /**
         * 用户名
         */
        private String username;

        /**
         * 昵称
         */
        private String nickname;

        /**
         * 头像URL
         */
        private String avatar;
    }
}
