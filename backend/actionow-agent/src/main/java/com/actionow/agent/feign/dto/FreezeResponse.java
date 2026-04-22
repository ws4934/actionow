package com.actionow.agent.feign.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 冻结积分响应
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FreezeResponse {

    /**
     * 冻结事务 ID（用于后续确认或解冻）
     */
    private String transactionId;

    /**
     * 冻结金额
     */
    private Long frozenAmount;
}
