package com.actionow.project.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.actionow.common.data.entity.TenantBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 灵感会话实体。
 *
 * <p><b>已 deprecated</b>：被 Asset + EntityRelation 统一流程取代。
 *
 * @author Actionow
 */
@Deprecated(since = "3.0", forRemoval = true)
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_inspiration_session", autoResultMap = true)
public class InspirationSession extends TenantBaseEntity {

    @TableField("user_id")
    private String userId;

    private String title;

    @TableField("cover_url")
    private String coverUrl;

    @TableField("record_count")
    private Integer recordCount;

    @TableField("total_credits")
    private BigDecimal totalCredits;

    private String status;

    @TableField("last_active_at")
    private LocalDateTime lastActiveAt;
}
