package com.actionow.task.security;

import com.actionow.common.core.constant.CommonConstants;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.result.ResultCode;
import com.actionow.task.entity.Task;
import com.actionow.task.mapper.TaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;

/**
 * 任务权限检查切面
 *
 * @author Actionow
 */
@Slf4j
@Aspect
@Component
@Order(100)
@RequiredArgsConstructor
public class TaskPermissionAspect {

    private final TaskMapper taskMapper;

    @Before("@annotation(requireTaskPermission)")
    public void checkPermission(JoinPoint joinPoint, RequireTaskPermission requireTaskPermission) {
        String userId = UserContextHolder.getUserId();
        String workspaceId = UserContextHolder.getWorkspaceId();

        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户未登录");
        }

        // 获取任务ID。解析失败必须 fail-closed，否则相当于跳过整个权限校验。
        // 常见失败原因：编译未开 -parameters、DTO 嵌套 taskId、参数名被重构。
        String taskId = extractTaskId(joinPoint, requireTaskPermission.taskIdParam());
        if (taskId == null) {
            log.warn("无法从参数中提取任务ID，拒绝操作: method={}, param={}",
                    joinPoint.getSignature(), requireTaskPermission.taskIdParam());
            throw new BusinessException(ResultCode.FORBIDDEN, "任务参数解析失败，操作已拒绝");
        }

        // 查询任务
        Task task = taskMapper.selectById(taskId);
        if (task == null || task.getDeleted() == CommonConstants.DELETED) {
            throw new BusinessException(ResultCode.TASK_NOT_FOUND);
        }

        // 验证工作空间。workspaceId 为空（内部调用、未登录、上下文丢失）同样 fail-closed。
        if (workspaceId == null || !workspaceId.equals(task.getWorkspaceId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作此任务");
        }

        // 检查是否是创建者
        boolean isCreator = userId.equals(task.getCreatorId());

        // 如果允许创建者且当前用户是创建者，直接通过
        if (requireTaskPermission.allowCreator() && isCreator) {
            log.debug("任务权限检查通过(创建者): taskId={}, userId={}", taskId, userId);
            return;
        }

        // 如果允许管理员，检查用户是否是工作空间管理员
        if (requireTaskPermission.allowAdmin() && isWorkspaceAdmin(workspaceId, userId)) {
            log.debug("任务权限检查通过(管理员): taskId={}, userId={}", taskId, userId);
            return;
        }

        // 检查具体权限
        RequireTaskPermission.TaskPermission[] requiredPermissions = requireTaskPermission.value();
        boolean hasPermission = checkSpecificPermissions(task, userId, requiredPermissions);

        if (!hasPermission) {
            log.warn("任务权限检查失败: taskId={}, userId={}, requiredPermissions={}",
                    taskId, userId, Arrays.toString(requiredPermissions));
            throw new BusinessException(ResultCode.FORBIDDEN, "无权执行此操作");
        }

        log.debug("任务权限检查通过: taskId={}, userId={}", taskId, userId);
    }

    /**
     * 从方法参数中提取任务ID
     */
    private String extractTaskId(JoinPoint joinPoint, String paramName) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].getName().equals(paramName)) {
                Object arg = args[i];
                if (arg instanceof String) {
                    return (String) arg;
                }
            }
        }

        // 尝试从 @PathVariable 注解获取
        String[] parameterNames = signature.getParameterNames();
        if (parameterNames != null) {
            for (int i = 0; i < parameterNames.length; i++) {
                if (parameterNames[i].equals(paramName)) {
                    Object arg = args[i];
                    if (arg instanceof String) {
                        return (String) arg;
                    }
                }
            }
        }

        return null;
    }

    /**
     * 检查用户是否是工作空间管理员
     */
    private boolean isWorkspaceAdmin(String workspaceId, String userId) {
        // 从上下文中获取工作空间角色
        String role = UserContextHolder.getWorkspaceRole();
        return "CREATOR".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role);
    }

    /**
     * 检查具体权限
     * <p>
     * 执行顺序约定：调用此方法前 {@link #checkPermission} 已经先尝试了
     * allowCreator（创建者短路）和 allowAdmin（管理员短路）两条路径。
     * 因此此处代表「既不是创建者也不是管理员」的工作空间普通成员。
     * <ul>
     *   <li>VIEW → 同工作空间成员允许</li>
     *   <li>ALL → 仅管理员（调用方已拒绝），此处恒 false</li>
     *   <li>CANCEL/RETRY/ADJUST_PRIORITY/DELETE → 属于改写类操作，
     *       当前没有任务级 ACL 表，普通成员一律拒绝。
     *       注：使用此注解时务必保持 allowCreator=true 或 allowAdmin=true，
     *       否则这些改写权限将因无授予途径而完全不可用。</li>
     * </ul>
     */
    private boolean checkSpecificPermissions(Task task, String userId,
                                              RequireTaskPermission.TaskPermission[] requiredPermissions) {
        for (RequireTaskPermission.TaskPermission permission : requiredPermissions) {
            if (permission == RequireTaskPermission.TaskPermission.VIEW) {
                return true; // 已通过工作空间成员校验
            }
            // 其余权限（CANCEL/RETRY/ADJUST_PRIORITY/DELETE/ALL）普通成员均拒绝
        }
        return false;
    }
}
