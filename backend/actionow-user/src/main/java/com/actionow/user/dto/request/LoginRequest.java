package com.actionow.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户登录请求
 *
 * @author Actionow
 */
@Data
public class LoginRequest {

    /**
     * 登录账号（用户名/邮箱/手机号）
     */
    @NotBlank(message = "登录账号不能为空")
    private String account;

    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    private String password;

    /**
     * 验证码（登录失败多次后需要）
     */
    private String captcha;

    /**
     * 验证码Key
     */
    private String captchaKey;

    /**
     * 是否记住登录
     */
    private Boolean rememberMe = false;
}
