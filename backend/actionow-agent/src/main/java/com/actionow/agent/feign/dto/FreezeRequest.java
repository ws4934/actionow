package com.actionow.agent.feign.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 冻结积分请求
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FreezeRequest {

    /**
     * 工作空间 ID
     */
    private String workspaceId;

    /**
     * 操作人 ID
     */
    private String operatorId;

    /**
     * 冻结金额
     */
    private Long amount;

    /**
     * 业务类型
     */
    private String businessType;

    /**
     * 业务 ID
     */
    private String businessId;

    /**
     * 备注
     */
    private String remark;
}
