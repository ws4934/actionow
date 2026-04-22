package com.actionow.task.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.actionow.common.data.entity.TenantBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 补偿任务实体
 * 用于处理积分操作失败后的自动重试
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_compensation_task", autoResultMap = true)
public class CompensationTask extends TenantBaseEntity {

    /**
     * 补偿类型: UNFREEZE(解冻积分), CONFIRM_CONSUME(确认消费)
     */
    private String type;

    /**
     * 状态: PENDING(待处理), PROCESSING(处理中), COMPLETED(已完成), EXHAUSTED(已耗尽重试)
     */
    private String status;

    /**
     * 已重试次数
     */
    @TableField("retry_count")
    private Integer retryCount;

    /**
     * 下次重试时间
     */
    @TableField("next_retry_at")
    private LocalDateTime nextRetryAt;

    /**
     * 补偿参数 JSON: {transactionId, businessId, amount, remark}
     */
    @TableField(value = "payload", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> payload;

    /**
     * 最后一次执行的错误信息
     */
    @TableField("last_error")
    private String lastError;

    /**
     * 任务完成时间
     */
    @TableField("completed_at")
    private LocalDateTime completedAt;
}
