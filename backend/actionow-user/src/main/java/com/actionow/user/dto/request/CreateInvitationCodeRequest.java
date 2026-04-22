package com.actionow.user.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 创建邀请码请求
 *
 * @author Actionow
 */
@Data
public class CreateInvitationCodeRequest {

    /**
     * 自定义邀请码（可选，不填则自动生成）
     */
    @Size(min = 4, max = 32, message = "邀请码长度必须在4-32个字符之间")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "邀请码只能包含字母、数字、下划线和连字符")
    private String code;

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
     * 生效时间
     */
    private LocalDateTime validFrom;

    /**
     * 失效时间
     */
    @Future(message = "失效时间必须是将来的时间")
    private LocalDateTime validUntil;
}
