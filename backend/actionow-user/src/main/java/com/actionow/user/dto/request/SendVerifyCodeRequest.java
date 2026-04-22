package com.actionow.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 发送验证码请求
 *
 * @author Actionow
 */
@Data
public class SendVerifyCodeRequest {

    /**
     * 目标（手机号或邮箱）
     */
    @NotBlank(message = "目标不能为空")
    private String target;

    /**
     * 验证码类型：register/login/reset_password/bind
     */
    @NotBlank(message = "验证码类型不能为空")
    @Pattern(regexp = "^(register|login|reset_password|bind)$", message = "验证码类型无效")
    private String type;

    /**
     * 图形验证码（防刷）
     */
    @NotBlank(message = "图形验证码不能为空")
    private String captcha;

    /**
     * 图形验证码Key
     */
    @NotBlank(message = "图形验证码Key不能为空")
    private String captchaKey;
}
