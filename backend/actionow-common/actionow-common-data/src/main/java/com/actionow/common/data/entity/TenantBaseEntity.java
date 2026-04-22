package com.actionow.common.data.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 多租户基础实体
 * 继承自 BaseEntity，增加工作空间字段
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class TenantBaseEntity extends BaseEntity {

    /**
     * 工作空间ID（租户ID）
     */
    @TableField("workspace_id")
    private String workspaceId;
}
