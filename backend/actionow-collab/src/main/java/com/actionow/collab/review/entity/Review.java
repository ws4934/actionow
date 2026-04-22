package com.actionow.collab.review.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.actionow.common.data.entity.TenantBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_review")
public class Review extends TenantBaseEntity {

    private String entityType;

    private String entityId;

    private String title;

    private String description;

    private String status;

    private String requesterId;

    private String reviewerId;

    private LocalDateTime reviewedAt;

    private String reviewComment;

    private Integer versionNumber;
}
