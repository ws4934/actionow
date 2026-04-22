package com.actionow.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 邀请码验证响应
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvitationCodeValidateResponse {

    /**
     * 是否有效
     */
    private Boolean valid;

    /**
     * 提示消息
     */
    private String message;

    /**
     * 邀请码类型
     */
    private String type;

    /**
     * 邀请人昵称（USER类型时）
     */
    private String inviterName;

    /**
     * 剩余使用次数
     */
    private Integer remainingUses;

    /**
     * 失效时间
     */
    private LocalDateTime validUntil;

    /**
     * 创建无效响应
     */
    public static InvitationCodeValidateResponse invalid(String message) {
        return InvitationCodeValidateResponse.builder()
                .valid(false)
                .message(message)
                .build();
    }

    /**
     * 创建有效响应
     */
    public static InvitationCodeValidateResponse valid(String type, String inviterName,
            Integer remainingUses, LocalDateTime validUntil) {
        return InvitationCodeValidateResponse.builder()
                .valid(true)
                .message("邀请码有效")
                .type(type)
                .inviterName(inviterName)
                .remainingUses(remainingUses)
                .validUntil(validUntil)
                .build();
    }
}
