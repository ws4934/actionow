package com.actionow.common.security.annotation;

import java.lang.annotation.*;

/**
 * 跳过认证注解
 * 标记在方法或类上，表示不需要认证即可访问
 *
 * @author Actionow
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IgnoreAuth {
}
