package com.actionow.agent.core.scope;

import lombok.Getter;

/**
 * 作用域访问异常
 * 当 Agent 尝试访问超出其作用域的资源时抛出
 *
 * @author Actionow
 */
@Getter
public class ScopeAccessException extends RuntimeException {

    /**
     * 当前作用域
     */
    private final AgentScope currentScope;

    /**
     * 尝试访问的资源类型
     */
    private final String resourceType;

    /**
     * 尝试访问的资源 ID
     */
    private final String resourceId;

    public ScopeAccessException(AgentScope currentScope, String resourceType, String resourceId) {
        super(buildMessage(currentScope, resourceType, resourceId));
        this.currentScope = currentScope;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    public ScopeAccessException(String message) {
        super(message);
        this.currentScope = null;
        this.resourceType = null;
        this.resourceId = null;
    }

    private static String buildMessage(AgentScope scope, String resourceType, String resourceId) {
        return String.format(
                "当前对话作用域为「%s」，无法访问%s「%s」。请在对应的%s页面发起对话，或使用全局对话。",
                scope.getName(),
                resourceType,
                resourceId,
                resourceType
        );
    }

    /**
     * 生成友好的错误响应
     */
    public String getFriendlyMessage() {
        if (currentScope == null) {
            return getMessage();
        }

        return switch (currentScope) {
            case SCRIPT -> String.format(
                    "当前对话仅限于当前剧本范围。如需访问其他%s，请返回工作空间发起新对话。",
                    resourceType
            );
            default -> getMessage();
        };
    }
}
