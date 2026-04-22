package com.actionow.task.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
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
     * 钱包侧返回字段名为 "id"，通过 @JsonAlias 兼容
     */
    @JsonAlias("id")
    private String transactionId;

    /**
     * 冻结金额
     */
    private Long frozenAmount;
}
