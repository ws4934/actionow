package com.actionow.agent.constant;

import lombok.Getter;

/**
 * Mission 状态枚举
 *
 * 状态机:
 * CREATED → EXECUTING → WAITING → EXECUTING → ... → COMPLETED
 *                                                  ↘ FAILED
 *          任意状态 → CANCELLED (用户取消)
 *
 * @author Actionow
 */
@Getter
public enum MissionStatus {

    /**
     * 已创建，等待开始执行
     */
    CREATED("CREATED", "已创建"),

    /**
     * 正在执行 Agent Step
     */
    EXECUTING("EXECUTING", "执行中"),

    /**
     * 等待委派的生成任务完成
     */
    WAITING("WAITING", "等待任务完成"),

    /**
     * 所有工作已完成
     */
    COMPLETED("COMPLETED", "已完成"),

    /**
     * 执行失败
     */
    FAILED("FAILED", "已失败"),

    /**
     * 用户取消
     */
    CANCELLED("CANCELLED", "已取消");

    private final String code;
    private final String name;

    MissionStatus(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static MissionStatus fromCode(String code) {
        if (code == null) {
            return CREATED;
        }
        for (MissionStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        return CREATED;
    }

    /**
     * 是否为终态（不可再变更）
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }

    /**
     * 是否可以取消
     */
    public boolean isCancellable() {
        return !isTerminal();
    }
}
