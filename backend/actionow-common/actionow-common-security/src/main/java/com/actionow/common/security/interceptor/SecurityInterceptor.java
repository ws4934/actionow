package com.actionow.common.security.interceptor;

import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.result.ResultCode;
import com.actionow.common.security.annotation.IgnoreAuth;
import com.actionow.common.security.annotation.RequireLogin;
import com.actionow.common.security.annotation.RequireRole;
import com.actionow.common.security.annotation.RequireSystemTenant;
import com.actionow.common.security.annotation.RequireWorkspaceMember;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Set;

/**
 * 安全拦截器
 * 处理 @RequireLogin、@RequireRole 等注解
 *
 * @author Actionow
 */
@Slf4j
@Component
public class SecurityInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Async completion dispatch（SSE/DeferredResult 完成时的二次 dispatch）：
        // 原始请求已通过完整认证链，无需重复校验
        if (request.getDispatcherType() == DispatcherType.ASYNC) {
            return true;
        }

        // 非 Controller 方法直接放行
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // 检查是否跳过认证
        if (hasIgnoreAuth(handlerMethod)) {
            return true;
        }

        // 检查登录要求
        if (hasRequireLogin(handlerMethod)) {
            checkLogin();
        }

        // 检查角色要求
        RequireRole requireRole = getRequireRole(handlerMethod);
        if (requireRole != null) {
            checkLogin();
            checkRole(requireRole);
        }

        // 纵深防御：@RequireSystemTenant 和 @RequireWorkspaceMember 的完整授权由 AOP 切面处理，
        // 拦截器作为兜底至少确保用户已登录
        if (hasAnnotation(handlerMethod, RequireSystemTenant.class)) {
            checkLogin();
        }
        if (hasAnnotation(handlerMethod, RequireWorkspaceMember.class)) {
            checkLogin();
        }

        return true;
    }

    private boolean hasIgnoreAuth(HandlerMethod handlerMethod) {
        // 方法级别注解优先
        if (handlerMethod.hasMethodAnnotation(IgnoreAuth.class)) {
            return true;
        }
        // 类级别注解
        return handlerMethod.getBeanType().isAnnotationPresent(IgnoreAuth.class);
    }

    private boolean hasRequireLogin(HandlerMethod handlerMethod) {
        // 方法级别注解优先
        if (handlerMethod.hasMethodAnnotation(RequireLogin.class)) {
            return true;
        }
        // 类级别注解
        return handlerMethod.getBeanType().isAnnotationPresent(RequireLogin.class);
    }

    private RequireRole getRequireRole(HandlerMethod handlerMethod) {
        // 方法级别注解优先
        RequireRole methodAnnotation = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (methodAnnotation != null) {
            return methodAnnotation;
        }
        // 类级别注解
        return handlerMethod.getBeanType().getAnnotation(RequireRole.class);
    }

    private boolean hasAnnotation(HandlerMethod handlerMethod, Class<? extends Annotation> annotationType) {
        if (handlerMethod.hasMethodAnnotation(annotationType)) {
            return true;
        }
        return handlerMethod.getBeanType().isAnnotationPresent(annotationType);
    }

    private void checkLogin() {
        if (!UserContextHolder.isLoggedIn()) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
    }

    private void checkRole(RequireRole requireRole) {
        Set<String> userRoles = UserContextHolder.getContext().getRoles();
        if (userRoles == null || userRoles.isEmpty()) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        String[] requiredRoles = requireRole.value();
        boolean requireAll = requireRole.requireAll();

        if (requireAll) {
            // 需要满足所有角色
            boolean hasAllRoles = Arrays.stream(requiredRoles).allMatch(userRoles::contains);
            if (!hasAllRoles) {
                log.warn("用户 {} 缺少必要角色: {}", UserContextHolder.getUserId(), Arrays.toString(requiredRoles));
                throw new BusinessException(ResultCode.FORBIDDEN);
            }
        } else {
            // 满足任一角色即可
            boolean hasAnyRole = Arrays.stream(requiredRoles).anyMatch(userRoles::contains);
            if (!hasAnyRole) {
                log.warn("用户 {} 缺少必要角色: {}", UserContextHolder.getUserId(), Arrays.toString(requiredRoles));
                throw new BusinessException(ResultCode.FORBIDDEN);
            }
        }
    }
}
