package com.actionow.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 充值汇率响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopupRateResponse {

    /** 币种 */
    private String currency;

    /** 每主单位对应积分数（如 1 USD = 10 积分） */
    private Long pointsPerMajorUnit;

    /** 最小单位与主单位的换算（如 100 cent = 1 USD） */
    private Long minorPerMajorUnit;
}
