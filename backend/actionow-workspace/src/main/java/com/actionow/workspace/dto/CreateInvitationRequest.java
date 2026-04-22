package com.actionow.workspace.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 创建邀请请求
 *
 * @author Actionow
 */
@Data
public class CreateInvitationRequest {

    /**
     * 分配的角色
     */
    @NotBlank(message = "角色不能为空")
    private String role;

    /**
     * 被邀请人邮箱列表（可选，指定后只有这些邮箱可使用）
     */
    @Size(max = 50, message = "每次最多邀请50个邮箱")
    private List<String> emails;

    /**
     * 被邀请人邮箱（单个，向后兼容）
     * @deprecated 请使用 emails 字段
     */
    @Deprecated
    private String inviteeEmail;

    /**
     * 邀请留言
     */
    @Size(max = 500, message = "邀请留言不能超过500字符")
    private String message;

    /**
     * 有效期（小时）
     */
    @Min(value = 1, message = "有效期最少1小时")
    @Max(value = 168, message = "有效期最多7天")
    private Integer expireHours = 24;

    /**
     * 最大使用次数
     */
    @Min(value = 1, message = "最大使用次数至少为1")
    @Max(value = 100, message = "最大使用次数不能超过100")
    private Integer maxUses = 1;

    /**
     * 获取有效的邮箱（优先使用emails，向后兼容inviteeEmail）
     */
    public String getEffectiveEmail() {
        if (emails != null && !emails.isEmpty()) {
            return emails.get(0);
        }
        return inviteeEmail;
    }

    /**
     * 判断是否为批量邀请
     */
    public boolean isBatchInvite() {
        return emails != null && emails.size() > 1;
    }
}
