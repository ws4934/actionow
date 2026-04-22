package com.actionow.common.security.aspect;

import com.actionow.common.core.constant.CommonConstants;
import com.actionow.common.core.context.UserContext;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.result.Result;
import com.actionow.common.core.result.ResultCode;
import com.actionow.common.security.annotation.RequireSystemTenant;
import com.actionow.common.security.workspace.WorkspaceInternalClient;
import com.actionow.common.security.workspace.WorkspaceMembershipInfo;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * 系统租户验证切面
 * 处理 @RequireSystemTenant 注解：
 * 1. 验证用户是系统工作空间成员
 * 2. 检查所需角色级别（minRole）
 * 3. 将 TenantSchema 设置为 tenant_system，使 MyBatis-Plus 自动走系统 schema
 *
 * @author Actionow
 */
@Slf4j
@Aspect
@Component
public class RequireSystemTenantAspect {

    private static final Map<String, Integer> ROLE_LEVELS = Map.of(
            "GUEST",   1,
            "MEMBER",  2,
            "ADMIN",   3,
            "CREATOR", 4
    );

    private final WorkspaceInternalClient workspaceClient;

    public RequireSystemTenantAspect(@Lazy WorkspaceInternalClient workspaceClient) {
        this.workspaceClient = workspaceClient;
    }

    @Around("@annotation(requireSystemTenant) || @within(requireSystemTenant)")
    public Object checkSystemTenant(ProceedingJoinPoint joinPoint, RequireSystemTenant requireSystemTenant) throws Throwable {
        // 方法级注解优先于类级注解
        if (requireSystemTenant == null) {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            requireSystemTenant = method.getAnnotation(RequireSystemTenant.class);
            if (requireSystemTenant == null) {
                requireSystemTenant = joinPoint.getTarget().getClass().getAnnotation(RequireSystemTenant.class);
            }
        }

        if (requireSystemTenant == null) {
            return joinPoint.proceed();
        }

        if (!UserContextHolder.isLoggedIn()) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        String userId = UserContextHolder.getUserId();
        WorkspaceMembershipInfo membership = getSystemMembership(userId);

        if (!membership.isMember()) {
            log.warn("非系统租户用户尝试访问受限资源: userId={}", userId);
            throw new BusinessException(ResultCode.FORBIDDEN, requireSystemTenant.message());
        }

        // 角色检查
        String minRole = requireSystemTenant.minRole();
        if (!hasRequiredRole(membership.getRole(), minRole)) {
            log.warn("系统租户用户角色不足: userId={}, userRole={}, requiredRole={}",
                    userId, membership.getRole(), minRole);
            throw new BusinessException(ResultCode.NO_PERMISSION,
                    "需要系统工作空间 " + minRole + " 及以上角色");
        }

        // 将 TenantSchema 设置为 tenant_system，使 TenantSchemaInterceptor 生效
        UserContext context = UserContextHolder.getContext();
        context.setWorkspaceId(CommonConstants.SYSTEM_WORKSPACE_ID);
        context.setWorkspaceRole(membership.getRole());
        context.setTenantSchema(CommonConstants.SYSTEM_TENANT_SCHEMA);
        UserContextHolder.setContext(context);

        log.debug("系统租户验证通过: userId={}, role={}, schema=tenant_system", userId, membership.getRole());
        return joinPoint.proceed();
    }

    private WorkspaceMembershipInfo getSystemMembership(String userId) {
        try {
            Result<WorkspaceMembershipInfo> result = workspaceClient.getMembership(
                    CommonConstants.SYSTEM_WORKSPACE_ID, userId);
            if (result != null && result.isSuccess() && result.getData() != null) {
                return result.getData();
            }
        } catch (Exception e) {
            log.error("验证系统租户成员身份失败: userId={}, error={}", userId, e.getMessage());
        }
        return WorkspaceMembershipInfo.builder()
                .workspaceId(CommonConstants.SYSTEM_WORKSPACE_ID)
                .userId(userId)
                .member(false)
                .build();
    }

    private boolean hasRequiredRole(String userRole, String requiredRole) {
        if (userRole == null || requiredRole == null) return false;
        Integer userLevel = ROLE_LEVELS.get(userRole);
        Integer requiredLevel = ROLE_LEVELS.get(requiredRole);
        if (userLevel == null || requiredLevel == null) return false;
        return userLevel >= requiredLevel;
    }
}
