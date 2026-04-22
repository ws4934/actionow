package com.actionow.user.dto.response;

import com.actionow.user.entity.InvitationCodeUsage;
import com.actionow.user.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 邀请码使用记录响应
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvitationCodeUsageResponse {

    /**
     * ID
     */
    private String id;

    /**
     * 邀请码ID
     */
    private String invitationCodeId;

    /**
     * 邀请码
     */
    private String code;

    /**
     * 邀请人ID
     */
    private String inviterId;

    /**
     * 邀请人用户名
     */
    private String inviterUsername;

    /**
     * 被邀请人ID
     */
    private String inviteeId;

    /**
     * 被邀请人用户名
     */
    private String inviteeUsername;

    /**
     * 被邀请人邮箱
     */
    private String inviteeEmail;

    /**
     * 使用时间
     */
    private LocalDateTime usedAt;

    /**
     * IP地址
     */
    private String ipAddress;

    /**
     * 从实体转换
     */
    public static InvitationCodeUsageResponse fromEntity(InvitationCodeUsage entity) {
        if (entity == null) {
            return null;
        }

        return InvitationCodeUsageResponse.builder()
                .id(entity.getId())
                .invitationCodeId(entity.getInvitationCodeId())
                .code(entity.getCode())
                .inviterId(entity.getInviterId())
                .inviteeId(entity.getInviteeId())
                .inviteeUsername(entity.getInviteeUsername())
                .inviteeEmail(entity.getInviteeEmail())
                .usedAt(entity.getUsedAt())
                .ipAddress(entity.getIpAddress())
                .build();
    }
}
