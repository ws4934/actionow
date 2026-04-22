package com.actionow.agent.constant;

import lombok.Getter;

/**
 * Mission Step 状态枚举
 *
 * @author Actionow
 */
@Getter
public enum MissionStepStatus {

    PENDING("PENDING", "待执行"),
    RUNNING("RUNNING", "执行中"),
    COMPLETED("COMPLETED", "已完成"),
    FAILED("FAILED", "已失败");

    private final String code;
    private final String name;

    MissionStepStatus(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static MissionStepStatus fromCode(String code) {
        if (code == null) {
            return PENDING;
        }
        for (MissionStepStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        return PENDING;
    }
}
