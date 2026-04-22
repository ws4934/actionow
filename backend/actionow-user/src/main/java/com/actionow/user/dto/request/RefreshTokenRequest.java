package com.actionow.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 刷新令牌请求
 *
 * @author Actionow
 */
@Data
public class RefreshTokenRequest {

    /**
     * Refresh Token
     */
    @NotBlank(message = "Refresh Token不能为空")
    private String refreshToken;
}
