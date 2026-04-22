package com.actionow.collab.comment.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.actionow.common.data.entity.TenantBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_comment", autoResultMap = true)
public class Comment extends TenantBaseEntity {

    private String targetType;

    private String targetId;

    private String scriptId;

    private String parentId;

    private String content;

    @TableField(value = "mentions", typeHandler = JacksonTypeHandler.class)
    private List<Object> mentions;

    private String status;

    private String resolvedBy;

    private LocalDateTime resolvedAt;
}
