package com.actionow.agent.tool.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Tool Catalog 扩展元数据注解
 *
 * <p>用于补充 Spring AI {@code @Tool} 无法表达的展示与文档信息。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AgentToolSpec {

    /**
     * 用户友好的显示名称。
     */
    String displayName() default "";

    /**
     * 简要摘要。
     */
    String summary() default "";

    /**
     * 工具作用说明。
     */
    String purpose() default "";

    /**
     * 动作类型。
     */
    ToolActionType actionType() default ToolActionType.UNKNOWN;

    /**
     * 标签。
     */
    String[] tags() default {};

    /**
     * 使用注意事项。
     */
    String[] usageNotes() default {};

    /**
     * 常见错误或失败场景。
     */
    String[] errorCases() default {};

    /**
     * 输入示例。
     */
    String exampleInput() default "";

    /**
     * 输出示例。
     */
    String exampleOutput() default "";
}
