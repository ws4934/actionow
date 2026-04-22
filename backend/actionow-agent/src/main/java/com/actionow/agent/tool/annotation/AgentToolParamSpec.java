package com.actionow.agent.tool.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Tool 参数扩展元数据注解。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface AgentToolParamSpec {

    /**
     * 默认值说明。
     */
    String defaultValue() default "";

    /**
     * 示例值。
     */
    String example() default "";

    /**
     * 枚举值列表。
     */
    String[] enumValues() default {};
}
