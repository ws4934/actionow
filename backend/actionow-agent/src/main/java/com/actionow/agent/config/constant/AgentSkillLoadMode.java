package com.actionow.agent.config.constant;

/**
 * Agent Skill 加载模式。
 */
public enum AgentSkillLoadMode {
    ALL_ENABLED,
    DEFAULT_ONLY,
    REQUEST_SCOPED,
    DISABLED;

    public static AgentSkillLoadMode fromCode(String code) {
        if (code == null || code.isBlank()) {
            return ALL_ENABLED;
        }
        for (AgentSkillLoadMode mode : values()) {
            if (mode.name().equalsIgnoreCase(code)) {
                return mode;
            }
        }
        return ALL_ENABLED;
    }
}
