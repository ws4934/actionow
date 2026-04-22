package com.actionow.wallet.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 消费请求
 *
 * @author Actionow
 */
@Data
public class ConsumeRequest {

    /**
     * 用户ID（执行消费的用户）
     */
    private String userId;

    /**
     * 消费金额（积分）
     */
    @NotNull(message = "消费金额不能为空")
    @Min(value = 1, message = "消费金额必须大于0")
    private Long amount;

    /**
     * 关联业务ID（如任务ID）
     */
    @NotBlank(message = "业务ID不能为空")
    private String businessId;

    /**
     * 关联业务类型
     */
    @NotBlank(message = "业务类型不能为空")
    private String businessType;

    /**
     * 消费描述
     */
    private String description;

    /**
     * 扩展元数据
     */
    private Map<String, Object> metadata;
}
