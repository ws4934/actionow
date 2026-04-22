package com.actionow.common.security.annotation;

import java.lang.annotation.*;

/**
 * 需要角色注解
 * 标记在方法或类上，表示需要特定角色才能访问
 *
 * @author Actionow
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {

    /**
     * 角色列表，满足任一角色即可访问
     */
    String[] value();

    /**
     * 是否需要满足所有角色
     */
    boolean requireAll() default false;
}
