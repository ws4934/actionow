package com.actionow.common.core.context;

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * 用户上下文持有器
 * 使用 TransmittableThreadLocal 支持线程池场景下的上下文传递
 *
 * @author Actionow
 */
public final class UserContextHolder {

    private static final TransmittableThreadLocal<UserContext> CONTEXT_HOLDER = new TransmittableThreadLocal<>();

    private UserContextHolder() {
    }

    /**
     * 设置用户上下文
     */
    public static void setContext(UserContext context) {
        CONTEXT_HOLDER.set(context);
    }

    /**
     * 获取用户上下文
     */
    public static UserContext getContext() {
        return CONTEXT_HOLDER.get();
    }

    /**
     * 获取用户上下文，不存在则返回空对象
     */
    public static UserContext getContextOrEmpty() {
        UserContext context = CONTEXT_HOLDER.get();
        return context != null ? context : new UserContext();
    }

    /**
     * 清除用户上下文
     */
    public static void clear() {
        CONTEXT_HOLDER.remove();
    }

    /**
     * 获取当前用户ID
     */
    public static String getUserId() {
        UserContext context = getContext();
        return context != null ? context.getUserId() : null;
    }

    /**
     * 获取当前用户名
     */
    public static String getUsername() {
        UserContext context = getContext();
        return context != null ? context.getUsername() : null;
    }

    /**
     * 获取当前工作空间ID
     */
    public static String getWorkspaceId() {
        UserContext context = getContext();
        return context != null ? context.getWorkspaceId() : null;
    }

    /**
     * 获取租户Schema
     */
    public static String getTenantSchema() {
        UserContext context = getContext();
        return context != null ? context.getTenantSchema() : null;
    }

    /**
     * 获取当前会话ID
     */
    public static String getSessionId() {
        UserContext context = getContext();
        return context != null ? context.getSessionId() : null;
    }

    /**
     * 获取请求ID
     */
    public static String getRequestId() {
        UserContext context = getContext();
        return context != null ? context.getRequestId() : null;
    }

    /**
     * 获取用户邮箱
     */
    public static String getEmail() {
        UserContext context = getContext();
        return context != null ? context.getEmail() : null;
    }

    /**
     * 获取当前工作空间角色
     */
    public static String getWorkspaceRole() {
        UserContext context = getContext();
        return context != null ? context.getWorkspaceRole() : null;
    }

    /**
     * 获取客户端IP
     */
    public static String getClientIp() {
        UserContext context = getContext();
        return context != null ? context.getClientIp() : null;
    }

    /**
     * 判断是否已登录
     */
    public static boolean isLoggedIn() {
        return getUserId() != null;
    }
}
