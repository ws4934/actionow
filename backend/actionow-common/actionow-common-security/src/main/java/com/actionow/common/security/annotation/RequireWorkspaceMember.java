package com.actionow.common.security.annotation;

import java.lang.annotation.*;

/**
 * 工作空间成员注解
 * 标记在方法上，表示需要是工作空间成员才能访问
 *
 * @author Actionow
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireWorkspaceMember {

    /**
     * 工作空间ID参数名，从请求参数或路径变量中获取
     * 如果为空，则从请求头 X-Workspace-Id 获取
     */
    String workspaceIdParam() default "";

    /**
     * 需要的最小角色级别
     * Guest < Member < Admin < Creator
     */
    WorkspaceRole minRole() default WorkspaceRole.GUEST;

    /**
     * 工作空间角色枚举
     * Creator: 创建者，最高权限，可删除空间、转让所有权
     * Admin: 管理员，管理成员、管理配置，不能删除空间
     * Member: 普通成员，创建/编辑内容，不能管理成员
     * Guest: 访客，只读权限，不能创建内容
     */
    enum WorkspaceRole {
        GUEST,
        MEMBER,
        ADMIN,
        CREATOR
    }
}
