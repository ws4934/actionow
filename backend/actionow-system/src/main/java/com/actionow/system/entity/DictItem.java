package com.actionow.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 数据字典项实体
 *
 * @author Actionow
 */
@Data
@TableName(value = "t_dict_item", autoResultMap = true)
public class DictItem {

    /**
     * 字典项ID
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 字典类型ID
     */
    private String typeId;

    /**
     * 字典类型编码
     */
    private String typeCode;

    /**
     * 字典项编码
     */
    private String itemCode;

    /**
     * 字典项名称
     */
    private String itemName;

    /**
     * 字典项值
     */
    private String itemValue;

    /**
     * 扩展属性
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extra;

    /**
     * 是否默认值
     */
    private Boolean isDefault;

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
