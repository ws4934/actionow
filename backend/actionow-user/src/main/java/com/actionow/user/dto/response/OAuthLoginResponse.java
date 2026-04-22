package com.actionow.user.dto.response;

import com.actionow.common.security.jwt.TokenResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OAuth登录响应
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthLoginResponse {

    /**
     * 是否为新用户
     */
    private Boolean isNewUser;

    /**
     * 用户信息
     */
    private UserResponse user;

    /**
     * Token信息
     */
    private TokenResponse token;
}
