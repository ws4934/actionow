package com.actionow.user.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 批量创建邀请码请求
 *
 * @author Actionow
 */
@Data
public class BatchCreateInvitationCodeRequest {

    /**
     * 生成数量
     */
    @NotNull(message = "生成数量不能为空")
    @Min(value = 1, message = "生成数量至少为1")
    @Max(value = 1000, message = "单次生成数量不能超过1000")
    private Integer count;

    /**
     * 邀请码前缀
     */
    @Size(max = 10, message = "前缀长度不能超过10个字符")
    @Pattern(regexp = "^[A-Za-z0-9_-]*$", message = "前缀只能包含字母、数字、下划线和连字符")
    private String prefix;

    /**
     * 批次名称
     */
    @Size(max = 100, message = "批次名称长度不能超过100个字符")
    private String name;

    /**
     * 每个邀请码的最大使用次数（-1表示无限）
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
