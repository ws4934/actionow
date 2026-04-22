package com.actionow.agent.core.scope;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Agent 作用域枚举
 * 定义 Agent 对话的权限边界
 *
 * - GLOBAL: 工作空间级别，可访问所有资源
 * - SCRIPT: 剧本级别，可访问剧本及其下属资源
 *
 * @author Actionow
 */
@Getter
@RequiredArgsConstructor
public enum AgentScope {

    /**
     * 全局作用域 - 可访问当前用户/工作空间下的所有资源
     * 适用于: 工作空间级别的对话、跨剧本操作
     */
    GLOBAL("global", "全局", "可访问工作空间下所有剧本和资源"),

    /**
     * 剧本作用域 - 仅可访问指定剧本及其子资源
     * 适用于: 在剧本编辑页面发起的对话
     */
    SCRIPT("script", "剧本", "仅可访问当前剧本及其章节、角色等");

    private final String code;
    private final String name;
    private final String description;

    /**
     * 根据 code 获取枚举
     */
    public static AgentScope fromCode(String code) {
        if (code == null) {
            return GLOBAL;
        }
        if (GLOBAL.code.equalsIgnoreCase(code)) {
            return GLOBAL;
        }
        if (SCRIPT.code.equalsIgnoreCase(code)) {
            return SCRIPT;
        }
        return GLOBAL;
    }
}
