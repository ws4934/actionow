package com.actionow.common.security.aspect;

import com.actionow.common.core.context.UserContext;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.result.Result;
import com.actionow.common.core.result.ResultCode;
import com.actionow.common.security.annotation.RequireWorkspaceMember;
import com.actionow.common.security.annotation.RequireWorkspaceMember.WorkspaceRole;
import com.actionow.common.security.workspace.WorkspaceInternalClient;
import com.actionow.common.security.workspace.WorkspaceMembershipInfo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;

/**
 * 工作空间成员验证切面
 * 处理 @RequireWorkspaceMember 注解
 *
 * @author Actionow
 */
@Slf4j
@Aspect
@Component
public class RequireWorkspaceMemberAspect {

    private static final String WORKSPACE_ID_HEADER = "X-Workspace-Id";
    private static final String WORKSPACE_ID_PARAM = "workspaceId";

    /**
     * 角色级别映射
     * Guest < Member < Admin < Creator
     */
    private static final Map<String, Integer> ROLE_LEVELS = Map.of(
            "GUEST", 1,
            "MEMBER", 2,
            "ADMIN", 3,
            "CREATOR", 4
    );

    private final WorkspaceInternalClient workspaceClient;

    public RequireWorkspaceMemberAspect(@Lazy WorkspaceInternalClient workspaceClient) {
        this.workspaceClient = workspaceClient;
    }

    @Around("@annotation(requireWorkspaceMember) || @within(requireWorkspaceMember)")
    public Object checkWorkspaceMembership(ProceedingJoinPoint joinPoint, RequireWorkspaceMember requireWorkspaceMember) throws Throwable {
        // 如果方法上有注解，优先使用方法上的注解
        if (requireWorkspaceMember == null) {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            requireWorkspaceMember = method.getAnnotation(RequireWorkspaceMember.class);
            if (requireWorkspaceMember == null) {
                requireWorkspaceMember = joinPoint.getTarget().getClass().getAnnotation(RequireWorkspaceMember.class);
            }
        }

        if (requireWorkspaceMember == null) {
            return joinPoint.proceed();
        }

        // 检查用户是否登录
        if (!UserContextHolder.isLoggedIn()) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        String userId = UserContextHolder.getUserId();
        String workspaceId = extractWorkspaceId(joinPoint, requireWorkspaceMember);

        if (workspaceId == null || workspaceId.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "工作空间ID不能为空");
        }

        // 验证成员身份
        WorkspaceMembershipInfo membershipInfo = getMembershipInfo(workspaceId, userId);

        if (!membershipInfo.isMember()) {
            log.warn("用户不是工作空间成员: userId={}, workspaceId={}", userId, workspaceId);
            throw new BusinessException(ResultCode.NOT_WORKSPACE_MEMBER);
        }

        // 检查角色
        WorkspaceRole minRole = requireWorkspaceMember.minRole();
        if (!hasRequiredRole(membershipInfo.getRole(), minRole)) {
            log.warn("用户角色不足: userId={}, workspaceId={}, userRole={}, requiredRole={}",
                    userId, workspaceId, membershipInfo.getRole(), minRole);
            throw new BusinessException(ResultCode.NO_PERMISSION);
        }

        // 设置用户上下文
        UserContext context = UserContextHolder.getContext();
        context.setWorkspaceId(workspaceId);
        context.setWorkspaceRole(membershipInfo.getRole());
        context.setTenantSchema(membershipInfo.getTenantSchema());
        UserContextHolder.setContext(context);

        log.debug("工作空间成员验证通过: userId={}, workspaceId={}, role={}, schema={}",
                userId, workspaceId, membershipInfo.getRole(), membershipInfo.getTenantSchema());

        return joinPoint.proceed();
    }

    /**
     * 提取工作空间ID
     */
    private String extractWorkspaceId(ProceedingJoinPoint joinPoint, RequireWorkspaceMember annotation) {
        String contextWorkspaceId = UserContextHolder.getWorkspaceId();

        // 1. 从注解指定的参数名获取
        String paramName = annotation.workspaceIdParam();
        String annotationWorkspaceId = null;
        if (paramName != null && !paramName.isBlank()) {
            annotationWorkspaceId = getParameterValue(joinPoint, paramName);
        }

        // 2. 从路径变量 workspaceId 获取
        String pathWorkspaceId = getParameterValue(joinPoint, WORKSPACE_ID_PARAM);

        // 3. 从请求头获取
        String headerWorkspaceId = null;
        HttpServletRequest request = getRequest();
        if (request != null) {
            headerWorkspaceId = request.getHeader(WORKSPACE_ID_HEADER);
        }

        // 优先使用网关注入的上下文工作空间ID，并对外部参数做一致性校验
        if (contextWorkspaceId != null && !contextWorkspaceId.isBlank()) {
            validateWorkspaceConsistency(contextWorkspaceId, annotationWorkspaceId, "annotation parameter");
            validateWorkspaceConsistency(contextWorkspaceId, pathWorkspaceId, "path variable");
            validateWorkspaceConsistency(contextWorkspaceId, headerWorkspaceId, "request header");
            return contextWorkspaceId;
        }

        if (annotationWorkspaceId != null && !annotationWorkspaceId.isBlank()) {
            return annotationWorkspaceId;
        }

        if (pathWorkspaceId != null && !pathWorkspaceId.isBlank()) {
            return pathWorkspaceId;
        }

        if (headerWorkspaceId != null && !headerWorkspaceId.isBlank()) {
            return headerWorkspaceId;
        }

        // 4. 从 UserContext 获取（通常为空，保留兜底）
        return contextWorkspaceId;
    }

    private void validateWorkspaceConsistency(String trustedWorkspaceId, String candidateWorkspaceId, String source) {
        if (candidateWorkspaceId == null || candidateWorkspaceId.isBlank()) {
            return;
        }
        if (!trustedWorkspaceId.equals(candidateWorkspaceId)) {
            log.warn("检测到workspaceId与上下文不一致: source={}, contextWorkspaceId={}, candidateWorkspaceId={}",
                    source, trustedWorkspaceId, candidateWorkspaceId);
            throw new BusinessException(ResultCode.NO_PERMISSION, "工作空间上下文不一致");
        }
    }

    /**
     * 获取参数值
     */
    private String getParameterValue(ProceedingJoinPoint joinPoint, String paramName) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];

            // 检查 @PathVariable
            PathVariable pathVariable = parameter.getAnnotation(PathVariable.class);
            if (pathVariable != null) {
                String name = pathVariable.value().isEmpty() ? parameter.getName() : pathVariable.value();
                if (name.equals(paramName) && args[i] != null) {
                    return args[i].toString();
                }
            }

            // 检查 @RequestParam
            RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
            if (requestParam != null) {
                String name = requestParam.value().isEmpty() ? parameter.getName() : requestParam.value();
                if (name.equals(paramName) && args[i] != null) {
                    return args[i].toString();
                }
            }

            // 检查参数名
            if (parameter.getName().equals(paramName) && args[i] != null) {
                return args[i].toString();
            }
        }

        return null;
    }

    /**
     * 获取成员身份信息
     */
    private WorkspaceMembershipInfo getMembershipInfo(String workspaceId, String userId) {
        Result<WorkspaceMembershipInfo> result;
        try {
            result = workspaceClient.getMembership(workspaceId, userId);
        } catch (Exception e) {
            log.error("获取工作空间成员信息失败: workspaceId={}, userId={}, error={}", workspaceId, userId, e.getMessage());
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "工作空间服务调用失败");
        }

        if (result != null && result.isSuccess() && result.getData() != null) {
            return result.getData();
        }

        log.warn("工作空间服务返回异常结果: workspaceId={}, userId={}, result={}", workspaceId, userId, result);
        throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "工作空间服务返回异常");
    }

    /**
     * 检查是否具有所需角色
     */
    private boolean hasRequiredRole(String userRole, WorkspaceRole requiredRole) {
        if (userRole == null) {
            return false;
        }

        // 用户角色直接使用（如 "CREATOR", "ADMIN", "MEMBER", "GUEST"）
        Integer userLevel = ROLE_LEVELS.get(userRole);
        // 枚举名转为 UPPER_SNAKE_CASE 字符串
        String requiredRoleName = switch (requiredRole) {
            case CREATOR -> "CREATOR";
            case ADMIN -> "ADMIN";
            case MEMBER -> "MEMBER";
            case GUEST -> "GUEST";
        };
        Integer requiredLevel = ROLE_LEVELS.get(requiredRoleName);

        if (userLevel == null || requiredLevel == null) {
            return false;
        }

        return userLevel >= requiredLevel;
    }

    /**
     * 获取当前请求
     */
    private HttpServletRequest getRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
}
