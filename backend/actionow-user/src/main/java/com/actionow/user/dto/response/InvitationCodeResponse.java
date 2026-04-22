package com.actionow.user.dto.response;

import com.actionow.user.entity.InvitationCode;
import com.actionow.user.enums.InvitationCodeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 邀请码响应
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvitationCodeResponse {

    /**
     * ID
     */
    private String id;

    /**
     * 邀请码
     */
    private String code;

    /**
     * 名称/备注
     */
    private String name;

    /**
     * 类型
     */
    private String type;

    /**
     * 所属用户ID
     */
    private String ownerId;

    /**
     * 所属用户名
     */
    private String ownerName;

    /**
     * 最大使用次数
     */
    private Integer maxUses;

    /**
     * 已使用次数
     */
    private Integer usedCount;

    /**
     * 剩余使用次数（-1表示无限）
     */
    private Integer remainingUses;

    /**
     * 生效时间
     */
    private LocalDateTime validFrom;

    /**
     * 失效时间
     */
    private LocalDateTime validUntil;

    /**
     * 状态
     */
    private String status;

    /**
     * 批次ID
     */
    private String batchId;

    /**
     * 创建人ID
     */
    private String createdBy;

    /**
     * 创建人名称
     */
    private String creatorName;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 当前是否可用
     */
    private Boolean isValid;

    /**
     * 从实体转换
     */
    public static InvitationCodeResponse fromEntity(InvitationCode entity) {
        if (entity == null) {
            return null;
        }

        int remainingUses = -1;
        if (entity.getMaxUses() != null && entity.getMaxUses() != -1) {
            remainingUses = Math.max(0, entity.getMaxUses() - (entity.getUsedCount() != null ? entity.getUsedCount() : 0));
        }

        boolean isValid = isCodeValid(entity);

        return InvitationCodeResponse.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .name(entity.getName())
                .type(entity.getType())
                .ownerId(entity.getOwnerId())
                .maxUses(entity.getMaxUses())
                .usedCount(entity.getUsedCount())
                .remainingUses(remainingUses)
                .validFrom(entity.getValidFrom())
                .validUntil(entity.getValidUntil())
                .status(entity.getStatus())
                .batchId(entity.getBatchId())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .isValid(isValid)
                .build();
    }

    /**
     * 判断邀请码当前是否有效
     */
    private static boolean isCodeValid(InvitationCode entity) {
        if (entity == null) {
            return false;
        }

        // 检查状态
        InvitationCodeStatus status = InvitationCodeStatus.fromCode(entity.getStatus());
        if (status == null || !status.isUsable()) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();

        // 检查生效时间
        if (entity.getValidFrom() != null && now.isBefore(entity.getValidFrom())) {
            return false;
        }

        // 检查失效时间
        if (entity.getValidUntil() != null && now.isAfter(entity.getValidUntil())) {
            return false;
        }

        // 检查使用次数
        if (entity.getMaxUses() != null && entity.getMaxUses() != -1) {
            int usedCount = entity.getUsedCount() != null ? entity.getUsedCount() : 0;
            if (usedCount >= entity.getMaxUses()) {
                return false;
            }
        }

        return true;
    }
}
