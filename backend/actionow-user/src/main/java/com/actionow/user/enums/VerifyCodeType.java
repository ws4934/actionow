package com.actionow.user.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 验证码类型枚举
 *
 * @author Actionow
 */
@Getter
@AllArgsConstructor
public enum VerifyCodeType {

    REGISTER("register", "注册"),
    LOGIN("login", "登录"),
    RESET_PASSWORD("reset_password", "重置密码"),
    BIND("bind", "绑定");

    private final String code;
    private final String name;

    /**
     * 根据code获取枚举
     */
    public static VerifyCodeType fromCode(String code) {
        for (VerifyCodeType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }
}
