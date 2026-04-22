package com.actionow.user.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 更新邀请码请求
 *
 * @author Actionow
 */
@Data
public class UpdateInvitationCodeRequest {

    /**
     * 名称/备注
     */
    @Size(max = 100, message = "名称长度不能超过100个字符")
    private String name;

    /**
     * 最大使用次数（-1表示无限）
     */
    @Min(value = -1, message = "最大使用次数不能小于-1")
    private Integer maxUses;

    /**
     * 失效时间
     */
    @Future(message = "失效时间必须是将来的时间")
    private LocalDateTime validUntil;

    /**
     * 状态（ACTIVE/DISABLED）
     */
    @Size(max = 20, message = "状态长度不能超过20个字符")
    private String status;
}
