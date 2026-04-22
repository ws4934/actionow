package com.actionow.user.enums;

import com.actionow.common.core.result.IResultCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户服务错误码枚举
 * 格式: 20xxx（用户服务错误码段）
 * - 200xx: 用户相关
 * - 201xx: 认证相关
 * - 202xx: OAuth相关
 *
 * @author Actionow
 */
@Getter
@AllArgsConstructor
public enum UserErrorCode implements IResultCode {

    // ==================== 用户相关 (200xx) ====================
    USER_NOT_FOUND("20001", "用户不存在"),
    USERNAME_EXISTS("20002", "用户名已存在"),
    EMAIL_EXISTS("20003", "邮箱已注册"),
    PHONE_EXISTS("20004", "手机号已注册"),
    USER_CREATE_FAILED("20005", "用户创建失败"),
    PROFILE_UPDATE_FAILED("20006", "资料更新失败"),

    // ==================== 认证相关 (201xx) ====================
    PASSWORD_INCORRECT("20101", "密码错误"),
    VERIFY_CODE_ERROR("20102", "验证码错误或已过期"),
    ACCOUNT_DISABLED("20103", "账号已被禁用"),
    ACCOUNT_BANNED("20104", "账号已被封禁"),
    ACCOUNT_INACTIVE("20105", "账号未激活"),
    LOGIN_FAILED_LIMIT("20106", "登录失败次数过多，请稍后再试"),
    TOKEN_INVALID("20107", "Token无效或已过期"),
    REFRESH_TOKEN_INVALID("20108", "Refresh Token无效"),
    OLD_PASSWORD_INCORRECT("20109", "原密码错误"),
    PASSWORD_SAME_AS_OLD("20110", "新密码不能与原密码相同"),
    PASSWORD_TOO_WEAK("20111", "密码强度不足"),

    // ==================== OAuth相关 (202xx) ====================
    OAUTH_PROVIDER_INVALID("20201", "不支持的OAuth提供商"),
    OAUTH_STATE_INVALID("20202", "无效的state参数"),
    OAUTH_CODE_INVALID("20203", "无效的授权码"),
    OAUTH_TOKEN_FAILED("20204", "获取OAuth Token失败"),
    OAUTH_USERINFO_FAILED("20205", "获取OAuth用户信息失败"),
    OAUTH_BINDING_FAILED("20206", "OAuth绑定失败"),
    OAUTH_BINDING_EXISTS("20207", "该第三方账号已绑定其他用户"),
    OAUTH_ALREADY_BOUND("20208", "您已绑定该第三方账号"),
    CANNOT_UNBIND_LAST("20209", "无法解绑最后一种登录方式"),
    OAUTH_NOT_BOUND("20210", "未找到该绑定记录");

    private final String code;
    private final String message;
}
