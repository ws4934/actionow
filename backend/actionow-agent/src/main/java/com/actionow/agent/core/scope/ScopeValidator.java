package com.actionow.agent.core.scope;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 作用域校验器
 * 提供统一的权限校验方法，供 Tool 调用
 *
 * @author Actionow
 */
@Slf4j
@Component
public class ScopeValidator {

    /**
     * 校验是否可以访问指定剧本
     */
    public void validateScriptAccess(String scriptId) {
        AgentContext context = AgentContextHolder.requireContext();

        if (!context.isScriptAccessible(scriptId)) {
            log.warn("Scope violation: session {} (scope={}, anchor={}) attempted to access script {}",
                    context.getSessionId(), context.getScope(), context.getScriptId(), scriptId);
            throw new ScopeAccessException(context.getScope(), "剧本", scriptId);
        }
    }

    /**
     * 校验是否可以执行全局操作（如创建新剧本、列出所有剧本）
     */
    public void requireGlobalScope() {
        AgentContext context = AgentContextHolder.requireContext();

        if (!context.isGlobalScope()) {
            log.warn("Scope violation: session {} (scope={}) attempted global operation",
                    context.getSessionId(), context.getScope());
            throw new ScopeAccessException(
                    String.format("当前对话作用域为「%s」，无法执行全局操作。请返回工作空间发起新对话。",
                            context.getScope().getName())
            );
        }
    }

    /**
     * 生成权限不足时的友好响应
     */
    public Map<String, Object> buildScopeErrorResponse(ScopeAccessException e) {
        return Map.of(
                "success", false,
                "error", e.getFriendlyMessage(),
                "errorType", "SCOPE_VIOLATION",
                "currentScope", e.getCurrentScope() != null ? e.getCurrentScope().getCode() : "unknown",
                "hint", "请在对应的资源页面发起对话，或使用全局对话访问更多资源"
        );
    }
}
