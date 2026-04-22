package com.actionow.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token验证响应（供内部服务使用）
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenValidateResponse {

    /**
     * Token是否有效
     */
    private boolean valid;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 头像URL
     */
    private String avatar;

    /**
     * 错误信息（Token无效时）
     */
    private String errorMessage;

    /**
     * 创建无效响应
     */
    public static TokenValidateResponse invalid(String errorMessage) {
        return TokenValidateResponse.builder()
                .valid(false)
                .errorMessage(errorMessage)
                .build();
    }
}
