package com.actionow.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OAuth授权URL响应
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthAuthorizeResponse {

    /**
     * 授权URL
     */
    private String authorizeUrl;

    /**
     * 状态码（用于防止CSRF）
     */
    private String state;
}
