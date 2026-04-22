package com.actionow.wallet.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 充值请求
 *
 * @author Actionow
 */
@Data
public class TopupRequest {

    /**
     * 充值金额（积分）
     */
    @NotNull(message = "充值金额不能为空")
    @Min(value = 1, message = "充值金额必须大于0")
    private Long amount;

    /**
     * 充值来源描述
     */
    private String description;

    /**
     * 关联的支付单号
     */
    private String paymentOrderId;

    /**
     * 支付方式
     */
    private String paymentMethod;
}
