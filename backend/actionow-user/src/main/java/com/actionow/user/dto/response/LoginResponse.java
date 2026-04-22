package com.actionow.user.dto.response;

import com.actionow.common.security.jwt.TokenResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /**
     * 用户信息
     */
    private UserResponse user;

    /**
     * Token 信息
     */
    private TokenResponse token;
}
