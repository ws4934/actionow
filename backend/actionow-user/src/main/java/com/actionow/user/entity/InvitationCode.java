package com.actionow.user.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.actionow.common.data.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 邀请码实体
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_invitation_code")
public class InvitationCode extends BaseEntity {

    /**
     * 邀请码
     */
    private String code;

    /**
     * 邀请码名称/备注
     */
    private String name;

    /**
     * 类型：SYSTEM-管理员创建，USER-用户专属
     */
    private String type;

    /**
     * 所属用户ID（USER类型必填）
     */
    @TableField("owner_id")
    private String ownerId;

    /**
     * 最大使用次数（-1表示无限）
     */
    @TableField("max_uses")
    private Integer maxUses;

    /**
     * 已使用次数
     */
    @TableField("used_count")
    private Integer usedCount;

    /**
     * 生效时间
     */
    @TableField("valid_from")
    private LocalDateTime validFrom;

    /**
     * 失效时间
     */
    @TableField("valid_until")
    private LocalDateTime validUntil;

    /**
     * 状态：ACTIVE/DISABLED/EXHAUSTED/EXPIRED/REPLACED
     */
    private String status;

    /**
     * 批次ID（批量生成时相同）
     */
    @TableField("batch_id")
    private String batchId;
}
