package com.actionow.user.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 邀请码类型枚举
 *
 * @author Actionow
 */
@Getter
@AllArgsConstructor
public enum InvitationCodeType {

    /**
     * 系统邀请码（管理员创建）
     */
    SYSTEM("System", "系统邀请码"),

    /**
     * 用户专属邀请码
     */
    USER("User", "用户邀请码");

    private final String code;
    private final String description;

    /**
     * 根据code获取枚举值
     */
    public static InvitationCodeType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (InvitationCodeType type : values()) {
            if (type.getCode().equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }
}
