package com.actionow.task.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 解冻积分请求
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnfreezeRequest {

    /**
     * 工作空间 ID
     */
    private String workspaceId;

    /**
     * 操作人 ID
     */
    private String operatorId;

    /**
     * 业务 ID
     */
    private String businessId;

    /**
     * 业务类型
     */
    private String businessType;

    /**
     * 备注
     */
    private String remark;
}
