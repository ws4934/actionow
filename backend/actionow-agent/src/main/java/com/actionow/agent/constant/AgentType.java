package com.actionow.agent.constant;

import lombok.Getter;

/**
 * Agent 类型枚举
 *
 * 剧本创作 Agent 的角色分工
 *
 * @author Actionow
 */
@Getter
public enum AgentType {

    /**
     * 协调者 Agent
     * 负责任务分发、流程协调、结果汇总
     */
    COORDINATOR("COORDINATOR", "协调者", "coordinator", true),

    /**
     * 通用创作专家 Agent（Skill 化架构）
     * 持有 SkillsAgentHook，按需 lazy 加载专家技能
     */
    UNIVERSAL("UNIVERSAL", "通用创作专家", "universal", false),

    /**
     * 自定义 Agent 占位类型
     * 用于用户自定义的 Agent，实际配置从数据库加载
     */
    CUSTOM("CUSTOM", "自定义Agent", "custom", false);

    /**
     * Agent 类型代码
     */
    private final String code;

    /**
     * Agent 名称（中文）
     */
    private final String name;

    /**
     * Agent 对应的 prompt key 前缀
     */
    private final String promptKeyPrefix;

    /**
     * 是否为协调者角色
     */
    private final boolean coordinator;

    AgentType(String code, String name, String promptKeyPrefix, boolean coordinator) {
        this.code = code;
        this.name = name;
        this.promptKeyPrefix = promptKeyPrefix;
        this.coordinator = coordinator;
    }

    /**
     * 根据 code 获取枚举
     * 如果 code 不匹配任何系统内置类型，返回 CUSTOM
     */
    public static AgentType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (AgentType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        // 未知类型视为自定义 Agent
        return CUSTOM;
    }

    /**
     * 根据 code 获取枚举，严格匹配
     * 仅返回系统内置类型，不匹配时返回 null
     */
    public static AgentType fromCodeStrict(String code) {
        if (code == null) {
            return null;
        }
        for (AgentType type : values()) {
            if (type != CUSTOM && type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 检查是否为系统内置 Agent 类型
     */
    public static boolean isSystemType(String code) {
        return fromCodeStrict(code) != null;
    }

    /**
     * 检查是否为自定义 Agent 类型
     */
    public static boolean isCustomType(String code) {
        return !isSystemType(code);
    }

    /**
     * 获取系统提示词的 prompt key
     */
    public String getSystemPromptKey() {
        return promptKeyPrefix.toUpperCase() + "_PROMPT";
    }

    /**
     * 判断是否为专家 Agent
     */
    public boolean isExpert() {
        return !coordinator;
    }
}
