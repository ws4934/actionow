package com.actionow.common.core.exception;

import com.actionow.common.core.result.IResultCode;

/**
 * 业务异常
 * 用于业务逻辑错误，如参数校验失败、业务规则不满足等
 *
 * @author Actionow
 */
public class BusinessException extends BaseException {

    private static final long serialVersionUID = 1L;

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String code, String message) {
        super(code, message);
    }

    public BusinessException(IResultCode resultCode) {
        super(resultCode);
    }

    public BusinessException(IResultCode resultCode, String message) {
        super(resultCode, message);
    }

    public BusinessException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public BusinessException(IResultCode resultCode, Throwable cause) {
        super(resultCode, cause);
    }

    /**
     * 快速创建业务异常
     */
    public static BusinessException of(String message) {
        return new BusinessException(message);
    }

    /**
     * 快速创建业务异常
     */
    public static BusinessException of(IResultCode resultCode) {
        return new BusinessException(resultCode);
    }

    /**
     * 快速创建业务异常
     */
    public static BusinessException of(IResultCode resultCode, String message) {
        return new BusinessException(resultCode, message);
    }
}
