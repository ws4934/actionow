package com.actionow.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户注册请求
 * 支持邮箱注册和手机号注册，二选一
 *
 * @author Actionow
 */
@Data
public class RegisterRequest {

    /**
     * 用户名
     */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 32, message = "用户名长度必须在4-32个字符之间")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]*$", message = "用户名必须以字母开头，只能包含字母、数字和下划线")
    private String username;

    /**
     * 邮箱（邮箱和手机号至少填写一项）
     */
    @Email(message = "邮箱格式不正确")
    private String email;

    /**
     * 手机号（邮箱和手机号至少填写一项）
     */
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度必须在8-64个字符之间")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$", message = "密码必须包含大小写字母和数字")
    private String password;

    /**
     * 昵称
     */
    @Size(max = 64, message = "昵称长度不能超过64个字符")
    private String nickname;

    /**
     * 验证码（发送到邮箱或手机的验证码）
     */
    @NotBlank(message = "验证码不能为空")
    @Size(min = 6, max = 6, message = "验证码长度必须为6位")
    private String verifyCode;

    /**
     * 邀请码（可选）
     */
    @Size(max = 20, message = "邀请码长度不能超过20位")
    private String inviteCode;

    /**
     * 获取注册目标（邮箱或手机号）
     */
    public String getTarget() {
        return email != null ? email : phone;
    }

    /**
     * 判断是否为邮箱注册
     */
    public boolean isEmailRegister() {
        return email != null && !email.isEmpty();
    }
}
