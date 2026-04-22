package com.actionow.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 邀请码统计响应
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvitationCodeStatisticsResponse {

    /**
     * 总数
     */
    private Long total;

    /**
     * 可用数量
     */
    private Long activeCount;

    /**
     * 已禁用数量
     */
    private Long disabledCount;

    /**
     * 已耗尽数量
     */
    private Long exhaustedCount;

    /**
     * 已过期数量
     */
    private Long expiredCount;

    /**
     * 系统邀请码数量
     */
    private Long systemCodeCount;

    /**
     * 用户邀请码数量
     */
    private Long userCodeCount;

    /**
     * 总使用次数
     */
    private Long totalUsed;
}
