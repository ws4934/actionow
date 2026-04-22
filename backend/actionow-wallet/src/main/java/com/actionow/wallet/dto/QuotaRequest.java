package com.actionow.wallet.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 配额设置请求
 *
 * @author Actionow
 */
@Data
public class QuotaRequest {

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 配额上限（积分）
     * -1 表示无限制
     */
    @NotNull(message = "配额上限不能为空")
    @Min(value = -1, message = "配额上限不能小于-1")
    private Long limitAmount;

    /**
     * 重置周期：DAILY, WEEKLY, MONTHLY, NEVER
     */
    private String resetCycle;
}
