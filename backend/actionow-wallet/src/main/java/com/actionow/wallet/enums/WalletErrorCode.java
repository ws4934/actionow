package com.actionow.wallet.enums;

import com.actionow.common.core.result.IResultCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 钱包服务错误码枚举
 * 格式: 40xxx（钱包服务错误码段）
 * - 400xx: 钱包相关
 * - 401xx: 配额相关
 * - 402xx: 交易相关
 *
 * @author Actionow
 */
@Getter
@AllArgsConstructor
public enum WalletErrorCode implements IResultCode {

    // ==================== 钱包相关 (400xx) ====================
    WALLET_NOT_FOUND("40001", "钱包不存在"),
    BALANCE_NOT_ENOUGH("40002", "余额不足"),
    FREEZE_EXCEEDS_BALANCE("40003", "冻结金额超出余额"),
    FROZEN_RECORD_NOT_FOUND("40004", "冻结记录不存在"),
    FROZEN_RECORD_INVALID_STATUS("40005", "冻结记录状态异常"),
    INVALID_TOPUP_AMOUNT("40006", "充值金额无效"),
    INVALID_CONSUME_AMOUNT("40007", "消费金额无效"),

    // ==================== 配额相关 (401xx) ====================
    QUOTA_NOT_FOUND("40008", "配额不存在"),
    QUOTA_EXCEEDED("40009", "配额已用尽"),
    QUOTA_PERIOD_EXPIRED("40010", "配额周期已过期"),
    INVALID_QUOTA_TYPE("40011", "无效的配额类型"),

    // ==================== 交易相关 (402xx) ====================
    TRANSACTION_NOT_FOUND("40012", "交易记录不存在"),
    TRANSACTION_FAILED("40013", "交易执行失败，请重试"),
    CONFIRM_CONSUME_FAILED("40014", "确认消费失败"),
    UNFREEZE_FAILED("40015", "解冻失败，请重试"),
    WALLET_CLOSED("40016", "工作空间钱包已关闭，无法进行交易");

    private final String code;
    private final String message;
}
