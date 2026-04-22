package com.actionow.agent.config.constant;

/**
 * Agent 执行模式。
 */
public enum AgentExecutionMode {
    CHAT,
    MISSION,
    BOTH;

    public static AgentExecutionMode fromCode(String code) {
        if (code == null || code.isBlank()) {
            return BOTH;
        }
        for (AgentExecutionMode mode : values()) {
            if (mode.name().equalsIgnoreCase(code)) {
                return mode;
            }
        }
        return BOTH;
    }
}
