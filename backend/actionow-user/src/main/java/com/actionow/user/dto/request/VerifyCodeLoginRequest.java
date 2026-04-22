package com.actionow.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 验证码登录请求
 *
 * @author Actionow
 */
@Data
public class VerifyCodeLoginRequest {

    /**
     * 目标（手机号或邮箱）
     */
    @NotBlank(message = "手机号或邮箱不能为空")
    private String target;

    /**
     * 验证码
     */
    @NotBlank(message = "验证码不能为空")
    private String verifyCode;
}
