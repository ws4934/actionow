package com.actionow.project.entity.version;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 版本实体基类
 * 所有版本表实体继承此类
 *
 * @author Actionow
 */
@Data
public abstract class EntityVersion implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 版本记录ID (UUIDv7)
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 工作空间ID
     */
    @TableField("workspace_id")
    private String workspaceId;

    /**
     * 版本号 (从1开始递增)
     */
    @TableField("version_number")
    private Integer versionNumber;

    /**
     * 变更摘要
     */
    @TableField("change_summary")
    private String changeSummary;

    /**
     * 创建人ID
     */
    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    /**
     * 创建时间
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
