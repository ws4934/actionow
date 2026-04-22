package com.actionow.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * OAuth绑定信息响应
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthBindingResponse {

    /**
     * OAuth提供商
     */
    private String provider;

    /**
     * 提供商用户名
     */
    private String providerUsername;

    /**
     * 提供商邮箱
     */
    private String providerEmail;

    /**
     * 提供商头像
     */
    private String providerAvatar;

    /**
     * 绑定时间
     */
    private LocalDateTime bindAt;
}
