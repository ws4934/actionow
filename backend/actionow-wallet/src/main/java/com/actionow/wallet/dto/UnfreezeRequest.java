package com.actionow.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 解冻请求（内部接口）
 *
 * @author Actionow
 */
@Data
public class UnfreezeRequest {

    /**
     * 工作空间ID
     */
    @NotBlank(message = "工作空间ID不能为空")
    private String workspaceId;

    /**
     * 操作人ID
     */
    @NotBlank(message = "操作人ID不能为空")
    private String operatorId;

    /**
     * 关联任务ID
     */
    @NotBlank(message = "任务ID不能为空")
    private String businessId;

    /**
     * 关联业务类型
     */
    @NotBlank(message = "业务类型不能为空")
    private String businessType;
}
