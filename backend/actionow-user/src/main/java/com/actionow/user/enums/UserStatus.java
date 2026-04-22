package com.actionow.user.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户状态枚举
 *
 * @author Actionow
 */
@Getter
@AllArgsConstructor
public enum UserStatus {

    /**
     * 正常状态
     */
    ACTIVE("ACTIVE", "正常"),

    /**
     * 已禁用（违规）
     */
    BANNED("BANNED", "已禁用"),

    /**
     * 未激活（待验证）
     */
    INACTIVE("INACTIVE", "未激活");

    private final String code;
    private final String description;

    /**
     * 根据code获取枚举值
     */
    public static UserStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (UserStatus status : values()) {
            if (status.getCode().equalsIgnoreCase(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 判断是否可正常登录
     */
    public boolean canLogin() {
        return this == ACTIVE;
    }
}
