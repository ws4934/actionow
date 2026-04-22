package com.actionow.task.security;

import java.lang.annotation.*;

/**
 * 任务操作权限注解
 * 用于控制任务的细粒度操作权限
 *
 * @author Actionow
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireTaskPermission {

    /**
     * 需要的权限类型
     */
    TaskPermission[] value();

    /**
     * 任务ID参数名称
     */
    String taskIdParam() default "taskId";

    /**
     * 是否允许任务创建者操作
     */
    boolean allowCreator() default true;

    /**
     * 是否允许工作空间管理员操作
     */
    boolean allowAdmin() default true;

    /**
     * 任务权限枚举
     */
    enum TaskPermission {
        /**
         * 查看任务
         */
        VIEW,

        /**
         * 取消任务
         */
        CANCEL,

        /**
         * 重试任务
         */
        RETRY,

        /**
         * 调整优先级
         */
        ADJUST_PRIORITY,

        /**
         * 删除任务
         */
        DELETE,

        /**
         * 所有权限
         */
        ALL
    }
}
