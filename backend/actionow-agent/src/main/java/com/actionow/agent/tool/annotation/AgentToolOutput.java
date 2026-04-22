package com.actionow.agent.tool.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Tool 输出元数据注解。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AgentToolOutput {

    /**
     * 输出说明。
     */
    String description() default "";

    /**
     * 结构化输出对应的 DTO/Class。
     */
    Class<?> schemaClass() default Void.class;

    /**
     * 手写 Schema JSON。
     */
    String schemaJson() default "";

    /**
     * 输出示例。
     */
    String example() default "";
}
