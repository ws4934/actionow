package com.actionow.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据字典实体
 *
 * @author Actionow
 */
@Data
@TableName("t_dict_type")
public class DictType {

    /**
     * 字典类型ID
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 字典类型编码
     */
    private String typeCode;

    /**
     * 字典类型名称
     */
    private String typeName;

    /**
     * 描述
     */
    private String description;

    /**
     * 是否系统内置
     */
    private Boolean isSystem;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 排序序号
     */
    private Integer sortOrder;

    /**
     * 创建者ID
     */
    private String createdBy;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 是否删除 (0=未删除, 1=已删除)
     */
    @TableLogic
    private Integer deleted;

    /**
     * 乐观锁版本号
     */
    @Version
    private Integer version;
}
