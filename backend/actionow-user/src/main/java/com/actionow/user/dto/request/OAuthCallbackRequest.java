package com.actionow.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * OAuth回调请求
 *
 * @author Actionow
 */
@Data
public class OAuthCallbackRequest {

    /**
     * 授权码
     */
    @NotBlank(message = "授权码不能为空")
    private String code;

    /**
     * 状态码（用于防止CSRF）
     */
    private String state;

    /**
     * 回调地址（Google OAuth需要，必须与获取授权URL时一致）
     */
    private String redirectUri;

    /**
     * 邀请码（新用户注册时使用，可选）
     */
    private String inviteCode;
}
