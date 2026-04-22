package com.actionow.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 注册配置响应
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationConfigResponse {

    /**
     * 是否需要邀请码注册
     */
    private Boolean invitationCodeRequired;

    /**
     * 是否允许使用用户邀请码注册
     */
    private Boolean allowUserCode;
}
