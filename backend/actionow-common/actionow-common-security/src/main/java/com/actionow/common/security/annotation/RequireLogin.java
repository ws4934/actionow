package com.actionow.common.security.annotation;

import java.lang.annotation.*;

/**
 * 需要登录注解
 * 标记在方法或类上，表示需要登录才能访问
 *
 * @author Actionow
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireLogin {
}
