package com.actionow.workspace.feign;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 钱包基本信息
 * 用于接收钱包服务返回的基础数据
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletBasicInfo {

    /**
     * 钱包 ID
     */
    private String id;

    /**
     * 工作空间 ID
     */
    private String workspaceId;

    /**
     * 余额（积分）
     */
    private Long balance;

    /**
     * 冻结金额
     */
    private Long frozenAmount;
}
