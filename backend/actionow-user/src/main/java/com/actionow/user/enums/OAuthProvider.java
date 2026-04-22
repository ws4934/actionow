package com.actionow.user.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * OAuth提供商枚举
 *
 * @author Actionow
 */
@Getter
@AllArgsConstructor
public enum OAuthProvider {

    WECHAT("wechat", "微信"),
    GITHUB("github", "GitHub"),
    GOOGLE("google", "Google"),
    APPLE("apple", "Apple"),
    LINUX_DO("linux_do", "Linux.do");

    private final String code;
    private final String name;

    /**
     * 根据code获取枚举
     */
    public static OAuthProvider fromCode(String code) {
        for (OAuthProvider provider : values()) {
            if (provider.code.equalsIgnoreCase(code)) {
                return provider;
            }
        }
        return null;
    }

    /**
     * 检查是否为有效的提供商
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }
}
