package com.actionow.project.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 内部端点限流注解
 * 类级别声明默认策略；方法级别覆盖类级别。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface InternalRateLimit {

    int permits() default 100;

    int intervalSeconds() default 1;

    KeyBy keyBy() default KeyBy.WORKSPACE;

    String name() default "";

    enum KeyBy {
        WORKSPACE,
        USER,
        GLOBAL
    }
}
