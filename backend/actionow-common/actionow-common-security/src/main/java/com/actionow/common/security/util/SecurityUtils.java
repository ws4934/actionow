package com.actionow.common.security.util;

import com.actionow.common.core.context.UserContext;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.result.ResultCode;

import java.util.Set;

/**
 * 安全工具类
 * 提供当前用户信息的便捷访问方法
 *
 * @author Actionow
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 获取当前用户ID
     *
     * @return 用户ID
     */
    public static String getCurrentUserId() {
        return UserContextHolder.getUserId();
    }

    /**
     * 获取当前用户ID，未登录则抛出异常
     *
     * @return 用户ID
     */
    public static String requireCurrentUserId() {
        String userId = UserContextHolder.getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return userId;
    }

    /**
     * 获取当前用户名
     *
     * @return 用户名
     */
    public static String getCurrentUsername() {
        return UserContextHolder.getUsername();
    }

    /**
     * 获取当前工作空间ID
     *
     * @return 工作空间ID
     */
    public static String getCurrentWorkspaceId() {
        return UserContextHolder.getWorkspaceId();
    }

    /**
     * 获取租户Schema
     *
     * @return 租户Schema
     */
    public static String getTenantSchema() {
        return UserContextHolder.getTenantSchema();
    }

    /**
     * 获取当前用户上下文
     *
     * @return 用户上下文
     */
    public static UserContext getContext() {
        return UserContextHolder.getContext();
    }

    /**
     * 获取当前用户角色
     *
     * @return 角色集合
     */
    public static Set<String> getCurrentRoles() {
        UserContext context = UserContextHolder.getContext();
        return context != null ? context.getRoles() : Set.of();
    }

    /**
     * 判断当前用户是否已登录
     *
     * @return 是否已登录
     */
    public static boolean isLoggedIn() {
        return UserContextHolder.isLoggedIn();
    }

    /**
     * 判断当前用户是否具有指定角色
     *
     * @param role 角色
     * @return 是否具有该角色
     */
    public static boolean hasRole(String role) {
        Set<String> roles = getCurrentRoles();
        return roles != null && roles.contains(role);
    }

    /**
     * 判断当前用户是否具有任一指定角色
     *
     * @param roles 角色数组
     * @return 是否具有任一角色
     */
    public static boolean hasAnyRole(String... roles) {
        Set<String> userRoles = getCurrentRoles();
        if (userRoles == null || userRoles.isEmpty()) {
            return false;
        }
        for (String role : roles) {
            if (userRoles.contains(role)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断当前用户是否具有所有指定角色
     *
     * @param roles 角色数组
     * @return 是否具有所有角色
     */
    public static boolean hasAllRoles(String... roles) {
        Set<String> userRoles = getCurrentRoles();
        if (userRoles == null || userRoles.isEmpty()) {
            return false;
        }
        for (String role : roles) {
            if (!userRoles.contains(role)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 获取请求ID
     *
     * @return 请求ID
     */
    public static String getRequestId() {
        return UserContextHolder.getRequestId();
    }
}
