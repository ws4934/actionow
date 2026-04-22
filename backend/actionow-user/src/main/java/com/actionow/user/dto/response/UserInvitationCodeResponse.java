package com.actionow.user.dto.response;

import com.actionow.user.entity.InvitationCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户邀请码响应
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInvitationCodeResponse {

    /**
     * ID
     */
    private String id;

    /**
     * 邀请码
     */
    private String code;

    /**
     * 最大使用次数
     */
    private Integer maxUses;

    /**
     * 已使用次数
     */
    private Integer usedCount;

    /**
     * 剩余使用次数
     */
    private Integer remainingUses;

    /**
     * 失效时间
     */
    private LocalDateTime validUntil;

    /**
     * 状态
     */
    private String status;

    /**
     * 当前是否可用
     */
    private Boolean isValid;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 总邀请人数（历史所有邀请码累计）
     */
    private Integer totalInvited;

    /**
     * 从实体转换
     */
    public static UserInvitationCodeResponse fromEntity(InvitationCode entity, int totalInvited) {
        if (entity == null) {
            return null;
        }

        int remainingUses = -1;
        if (entity.getMaxUses() != null && entity.getMaxUses() != -1) {
            remainingUses = Math.max(0, entity.getMaxUses() - (entity.getUsedCount() != null ? entity.getUsedCount() : 0));
        }

        boolean isValid = InvitationCodeResponse.fromEntity(entity).getIsValid();

        return UserInvitationCodeResponse.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .maxUses(entity.getMaxUses())
                .usedCount(entity.getUsedCount())
                .remainingUses(remainingUses)
                .validUntil(entity.getValidUntil())
                .status(entity.getStatus())
                .isValid(isValid)
                .createdAt(entity.getCreatedAt())
                .totalInvited(totalInvited)
                .build();
    }
}
