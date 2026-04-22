package com.actionow.agent.constant;

import lombok.Getter;

/**
 * Mission Step 类型枚举
 *
 * @author Actionow
 */
@Getter
public enum MissionStepType {

    /**
     * 调用 Agent 进行分析/决策/工具调用
     */
    AGENT_INVOKE("AGENT_INVOKE", "Agent 调用"),

    /**
     * 等待委派的生成任务完成
     */
    WAIT_TASKS("WAIT_TASKS", "等待任务");

    private final String code;
    private final String name;

    MissionStepType(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static MissionStepType fromCode(String code) {
        if (code == null) {
            return AGENT_INVOKE;
        }
        for (MissionStepType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return AGENT_INVOKE;
    }
}
