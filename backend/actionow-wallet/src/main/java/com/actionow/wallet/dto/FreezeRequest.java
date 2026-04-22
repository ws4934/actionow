package com.actionow.wallet.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 冻结请求
 *
 * @author Actionow
 */
@Data
public class FreezeRequest {

    /**
     * 工作空间ID（内部接口使用）
     */
    private String workspaceId;

    /**
     * 操作人ID（内部接口使用）
     */
    private String operatorId;

    /**
     * 用户ID（执行操作的用户）
     */
    private String userId;

    /**
     * 金额（积分）
     */
    @NotNull(message = "金额不能为空")
    @Min(value = 1, message = "金额必须大于0")
    private Long amount;

    /**
     * 冻结原因
     */
    private String reason;

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

    /**
     * 操作描述
     */
    private String description;
}
