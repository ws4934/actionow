package com.actionow.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量创建邀请码结果
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchCreateInvitationCodeResult {

    /**
     * 批次ID
     */
    private String batchId;

    /**
     * 成功创建数量
     */
    private Integer successCount;

    /**
     * 失败数量
     */
    private Integer failedCount;

    /**
     * 创建的邀请码列表
     */
    private List<InvitationCodeResponse> codes;
}
