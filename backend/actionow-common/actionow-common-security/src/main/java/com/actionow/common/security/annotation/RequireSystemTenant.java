package com.actionow.common.security.annotation;

import java.lang.annotation.*;

/**
 * 需要系统租户权限注解
 * 标记在方法或类上，表示只有系统租户(工作空间)的用户才能访问
 *
 * @author Actionow
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireSystemTenant {

    /**
     * 需要的最小角色级别（系统工作空间内）
     * Guest < Member < Admin < Creator
     * 默认 Admin：仅系统管理员可访问
     */
    String minRole() default "ADMIN";

    /**
     * 错误提示信息
     */
    String message() default "仅系统管理员可访问";
}
