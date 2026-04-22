package com.actionow.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 平台统计实体
 *
 * @author Actionow
 */
@Data
@TableName(value = "platform_stats", autoResultMap = true)
public class PlatformStats {

    /**
     * 统计ID
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 统计日期
     */
    private LocalDate statsDate;

    /**
     * 统计周期（HOURLY/DAILY/WEEKLY/MONTHLY）
     */
    private String period;

    /**
     * 指标类型
     */
    private String metricType;

    /**
     * 工作空间ID（为空表示全平台）
     */
    private String workspaceId;

    /**
     * 指标值
     */
    private Long metricValue;

    /**
     * 详细数据
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> details;

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
}
