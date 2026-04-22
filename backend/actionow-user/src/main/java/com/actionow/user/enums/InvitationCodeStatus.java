package com.actionow.user.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 邀请码状态枚举
 *
 * @author Actionow
 */
@Getter
@AllArgsConstructor
public enum InvitationCodeStatus {

    /**
     * 可用
     */
    ACTIVE("ACTIVE", "可用"),

    /**
     * 已禁用
     */
    DISABLED("DISABLED", "已禁用"),

    /**
     * 已耗尽（使用次数已满）
     */
    EXHAUSTED("EXHAUSTED", "已耗尽"),

    /**
     * 已过期
     */
    EXPIRED("EXPIRED", "已过期"),

    /**
     * 已替换（用户刷新邀请码后旧码状态）
     */
    REPLACED("REPLACED", "已替换");

    private final String code;
    private final String description;

    /**
     * 根据code获取枚举值
     */
    public static InvitationCodeStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (InvitationCodeStatus status : values()) {
            if (status.getCode().equalsIgnoreCase(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 判断邀请码是否可用
     */
    public boolean isUsable() {
        return this == ACTIVE;
    }
}
