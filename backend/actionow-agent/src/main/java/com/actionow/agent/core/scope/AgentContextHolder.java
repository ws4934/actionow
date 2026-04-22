package com.actionow.agent.core.scope;

import com.actionow.agent.core.context.SessionContextHolder;
import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * Agent 上下文持有者（与 {@link SessionContextHolder} 共同构成"单一真源"）。
 *
 * <p>使用 TransmittableThreadLocal 支持 Reactor boundedElastic 等线程池场景下的上下文自动传播。
 * 当 TTL 没被传播到（例如某些上游线程没有经过 TTL agent 工具），
 * {@link #getContext()} 会自动回退到 {@link SessionContextHolder} 中以 sessionId 索引的同一份 AgentContext，
 * 避免两路 Holder 语义漂移造成跨租户或跨会话的读写错位。
 *
 * @author Actionow
 */
public class AgentContextHolder {

    private static final TransmittableThreadLocal<AgentContext> CONTEXT_HOLDER = new TransmittableThreadLocal<>();

    /**
     * 设置当前线程的 Agent 上下文
     */
    public static void setContext(AgentContext context) {
        CONTEXT_HOLDER.set(context);
    }

    /**
     * 获取当前线程的 Agent 上下文；TTL 为空时回退到 SessionContextHolder 以 sessionId 查询。
     */
    public static AgentContext getContext() {
        AgentContext ctx = CONTEXT_HOLDER.get();
        if (ctx != null) {
            return ctx;
        }
        return SessionContextHolder.getCurrentAgentContext();
    }

    /**
     * 获取当前上下文，如果不存在则抛出异常
     */
    public static AgentContext requireContext() {
        AgentContext context = getContext();
        if (context == null) {
            throw new IllegalStateException("AgentContext not set. Make sure the tool is called within an agent session.");
        }
        return context;
    }

    /**
     * 清除当前线程的上下文
     */
    public static void clearContext() {
        CONTEXT_HOLDER.remove();
    }

    /**
     * 获取当前作用域
     */
    public static AgentScope getScope() {
        AgentContext context = getContext();
        return context != null ? context.getScope() : AgentScope.GLOBAL;
    }

    /**
     * 判断当前是否为全局作用域
     */
    public static boolean isGlobalScope() {
        return getScope() == AgentScope.GLOBAL;
    }

    /**
     * 获取当前锚定的剧本 ID
     */
    public static String getScriptId() {
        AgentContext context = getContext();
        return context != null ? context.getScriptId() : null;
    }
}
