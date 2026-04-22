package com.actionow.agent.billing.exception;

import com.actionow.common.core.exception.BusinessException;

/**
 * 成员配额不足异常
 * 当用户的成员配额不足以进行 Agent 会话时抛出
 *
 * @author Actionow
 */
public class InsufficientQuotaException extends BusinessException {

    private static final long serialVersionUID = 1L;

    /**
     * 错误码：配额不足
     */
    public static final String ERROR_CODE = "INSUFFICIENT_QUOTA";

    /**
     * 默认错误消息
     */
    public static final String DEFAULT_MESSAGE = "成员配额不足，请联系管理员增加配额";

    /**
     * 用户 ID
     */
    private final String userId;

    /**
     * 所需配额
     */
    private final Long requiredAmount;

    public InsufficientQuotaException() {
        super(ERROR_CODE, DEFAULT_MESSAGE);
        this.userId = null;
        this.requiredAmount = null;
    }

    public InsufficientQuotaException(String message) {
        super(ERROR_CODE, message);
        this.userId = null;
        this.requiredAmount = null;
    }

    public InsufficientQuotaException(String userId, Long requiredAmount) {
        super(ERROR_CODE, String.format("成员配额不足，所需: %d，请联系管理员增加配额", requiredAmount));
        this.userId = userId;
        this.requiredAmount = requiredAmount;
    }

    public String getUserId() {
        return userId;
    }

    public Long getRequiredAmount() {
        return requiredAmount;
    }

    /**
     * 检查给定的异常是否是配额不足异常
     *
     * @param throwable 异常
     * @return 是否是配额不足异常
     */
    public static boolean isInsufficientQuota(Throwable throwable) {
        if (throwable instanceof InsufficientQuotaException) {
            return true;
        }
        if (throwable instanceof BusinessException be) {
            String message = be.getMessage();
            return message != null && (
                    message.contains("配额不足") ||
                    message.contains("QUOTA_EXCEEDED") ||
                    message.contains("INSUFFICIENT_QUOTA")
            );
        }
        if (throwable != null && throwable.getCause() != null) {
            return isInsufficientQuota(throwable.getCause());
        }
        return false;
    }
}
