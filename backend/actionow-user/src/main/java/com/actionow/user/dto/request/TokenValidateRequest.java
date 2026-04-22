package com.actionow.user.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token验证请求（供内部服务使用）
 *
 * @author Actionow
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenValidateRequest {

    /**
     * JWT token字符串
     */
    private String token;
}
