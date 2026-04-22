package com.actionow.agent.tool.aspect;

import com.actionow.agent.core.agent.AgentStreamEvent;
import com.actionow.agent.core.context.SessionContextHolder;
import com.actionow.agent.core.scope.AgentContext;
import com.actionow.agent.core.scope.AgentContextHolder;
import com.actionow.agent.interaction.AgentStreamBridge;
import com.actionow.agent.metrics.AgentMetrics;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.agent.tool.interceptor.ToolExecutionInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * PROJECT 工具执行切面（SAA v2）
 *
 * <p>职责：
 * <ol>
 *   <li>跨线程上下文恢复（AgentContext / UserContext）</li>
 *   <li>工具执行后配额计数（非阻断）</li>
 * </ol>
 *
 * <p>权限与配额的阻断检查已移至构建期（AgentResolutionService + AgentToolAccessService），
 * 工具不被注入 = LLM 不可见 = 不可能调用，因此运行期无需重复校验。
 *
 * @author Actionow
 */
@Slf4j
@Aspect
@Component
@Order(1)
@RequiredArgsConstructor
public class ToolExecutionAspect {

    private final ToolExecutionInterceptor toolInterceptor;
    private final AgentMetrics agentMetrics;
    private final AgentStreamBridge streamBridge;

    /**
     * 切入点：scriptwriting.tools / mission.tools / interaction 包下所有 *Tools 类中的方法。
     * 包前缀与 {@code ProjectToolScanner} 保持一致。
     */
    @Pointcut("execution(* com.actionow.agent.scriptwriting.tools..*Tools.*(..)) "
            + "|| execution(* com.actionow.agent.mission.tools..*Tools.*(..)) "
            + "|| execution(* com.actionow.agent.interaction..*Tools.*(..))")
    public void projectToolMethods() {}

    /**
     * 环绕通知：执行权限检查
     */
    @Around("projectToolMethods()")
    public Object aroundToolExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 只拦截带 @Tool 注解的方法（Spring AI SAA v2）
        if (!method.isAnnotationPresent(Tool.class)) {
            return joinPoint.proceed();
        }

        // AgentContextHolder.getContext() 内部已经实现 TTL → SessionContextHolder 兜底，
        // 不需要在此处二次兜底。null 代表上游未走 AgentContextBuilder 注入，是接入问题。
        AgentContext agentContext = AgentContextHolder.getContext();

        // 恢复上下文到当前线程
        if (agentContext != null) {
            AgentContextHolder.setContext(agentContext);

            String sessionId = agentContext.getSessionId();
            if (sessionId != null) {
                SessionContextHolder.setCurrentSessionId(sessionId);

                if (UserContextHolder.getTenantSchema() == null) {
                    SessionContextHolder.ExecutionContext execCtx = SessionContextHolder.get(sessionId);
                    if (execCtx != null && execCtx.getUserContext() != null) {
                        UserContextHolder.setContext(execCtx.getUserContext());
                        log.debug("Restored UserContext from SessionContextHolder for session: {}", sessionId);
                    }
                }
            }
        }

        String userId = agentContext != null ? agentContext.getUserId() : null;
        String sessionId = agentContext != null ? agentContext.getSessionId() : null;

        // 生成工具标识
        String toolId = deriveToolId(joinPoint, method);
        String toolName = resolveToolName(method, toolId);
        String ownerClass = joinPoint.getTarget().getClass().getSimpleName();

        log.debug("PROJECT tool execution: toolId={}, userId={}", toolId, userId);

        // 权限和配额的阻断检查已移至构建期，此处不再二次校验

        boolean emitStatus = shouldEmitStatus(joinPoint.getTarget().getClass());
        if (emitStatus && sessionId != null) {
            Map<String, Object> details = new HashMap<>();
            details.put("toolId", toolId);
            details.put("toolName", toolName);
            streamBridge.publish(sessionId, AgentStreamEvent.status(
                    "tool_executing",
                    "正在执行 " + toolName,
                    null,
                    details));
        }

        boolean success = false;
        long startMs = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            success = isSuccessResult(result);
            return result;
        } catch (Throwable t) {
            success = false;
            throw t;
        } finally {
            long durationMs = System.currentTimeMillis() - startMs;
            agentMetrics.recordToolExecution(toolId, success, durationMs);

            if (emitStatus && sessionId != null) {
                Map<String, Object> details = new HashMap<>();
                details.put("toolId", toolId);
                details.put("toolName", toolName);
                details.put("success", success);
                details.put("durationMs", durationMs);
                streamBridge.publish(sessionId, AgentStreamEvent.status(
                        "tool_complete",
                        (success ? "已完成 " : "失败 ") + toolName,
                        null,
                        details));
            }

            // 不清理 AgentContext 和 sessionId — 由 AgentTeardownService 统一清理，
            // 避免同一 ReAct 迭代内连续工具调用间上下文丢失
            // 后置配额计数（非阻断）
            if (userId != null) {
                toolInterceptor.postExecute(userId, "PROJECT", toolId, success);
            }
        }
    }

    /**
     * 决定是否为本次工具调用发 status 事件。
     * <p>interaction 包下的 {@code AskUserTools} 自己会发 {@code ask_user} 事件，语义更具体，
     * 不再额外推 tool_executing status，避免前端收到两份同义事件。
     */
    private boolean shouldEmitStatus(Class<?> targetClass) {
        String fqn = targetClass.getName();
        return !fqn.startsWith("com.actionow.agent.interaction");
    }

    /**
     * 从 @Tool 注解或方法名解析 LLM 可见的工具名（snake_case），用于 status 事件的 label。
     */
    private String resolveToolName(Method method, String fallback) {
        Tool anno = method.getAnnotation(Tool.class);
        if (anno != null && !anno.name().isBlank()) return anno.name();
        return fallback;
    }

    private String deriveToolId(ProceedingJoinPoint joinPoint, Method method) {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        if (className.contains("$$")) {
            className = className.substring(0, className.indexOf("$$"));
        }
        String entityName = className.replace("Tools", "").toLowerCase();
        return entityName + "_" + method.getName();
    }

    /**
     * 判断执行结果是否成功
     */
    private boolean isSuccessResult(Object result) {
        if (result == null) {
            return true; // void 方法视为成功
        }
        if (result instanceof Map<?, ?> map) {
            Object successValue = map.get("success");
            if (successValue instanceof Boolean b) {
                return b;
            }
        }
        return true; // 默认视为成功
    }
}
