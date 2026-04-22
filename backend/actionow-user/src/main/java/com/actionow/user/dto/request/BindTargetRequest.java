package com.actionow.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 绑定邮箱/手机请求
 *
 * @author Actionow
 */
@Data
public class BindTargetRequest {

    /**
     * 类型：email/phone
     */
    @NotBlank(message = "类型不能为空")
    @Pattern(regexp = "^(email|phone)$", message = "类型必须为email或phone")
    private String type;

    /**
     * 目标（邮箱或手机号）
     */
    @NotBlank(message = "目标不能为空")
    private String target;

    /**
     * 验证码
     */
    @NotBlank(message = "验证码不能为空")
    private String verifyCode;
}
