package com.actionow.agent.billing.exception;

import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.result.IResultCode;

/**
 * 积分不足异常
 * 当用户积分不足以进行 Agent 会话时抛出
 *
 * @author Actionow
 */
public class InsufficientCreditsException extends BusinessException {

    private static final long serialVersionUID = 1L;

    /**
     * 错误码：积分不足
     */
    public static final String ERROR_CODE = "INSUFFICIENT_CREDITS";

    /**
     * 默认错误消息
     */
    public static final String DEFAULT_MESSAGE = "积分不足，请充值后再试";

    /**
     * 当前余额
     */
    private final Long currentBalance;

    /**
     * 所需积分
     */
    private final Long requiredAmount;

    public InsufficientCreditsException() {
        super(ERROR_CODE, DEFAULT_MESSAGE);
        this.currentBalance = null;
        this.requiredAmount = null;
    }

    public InsufficientCreditsException(String message) {
        super(ERROR_CODE, message);
        this.currentBalance = null;
        this.requiredAmount = null;
    }

    public InsufficientCreditsException(Long currentBalance, Long requiredAmount) {
        super(ERROR_CODE, String.format("积分不足，当前余额: %d，所需: %d", currentBalance, requiredAmount));
        this.currentBalance = currentBalance;
        this.requiredAmount = requiredAmount;
    }

    public InsufficientCreditsException(IResultCode resultCode, String message) {
        super(resultCode, message);
        this.currentBalance = null;
        this.requiredAmount = null;
    }

    public Long getCurrentBalance() {
        return currentBalance;
    }

    public Long getRequiredAmount() {
        return requiredAmount;
    }

    /**
     * 检查给定的异常是否是积分不足异常
     *
     * @param throwable 异常
     * @return 是否是积分不足异常
     */
    public static boolean isInsufficientCredits(Throwable throwable) {
        if (throwable instanceof InsufficientCreditsException) {
            return true;
        }
        if (throwable instanceof BusinessException be) {
            // 检查是否包含积分不足相关的关键词
            String message = be.getMessage();
            return message != null && (
                    message.contains("余额不足") ||
                    message.contains("积分不足") ||
                    message.contains("BALANCE_NOT_ENOUGH") ||
                    message.contains("0303200")  // WALLET_BALANCE_NOT_ENOUGH 错误码
            );
        }
        // 检查嵌套异常
        if (throwable != null && throwable.getCause() != null) {
            return isInsufficientCredits(throwable.getCause());
        }
        return false;
    }
}
