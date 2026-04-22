package com.actionow.common.core.exception;

import com.actionow.common.core.result.IResultCode;
import com.actionow.common.core.result.ResultCode;

/**
 * 服务异常
 * 用于服务层错误，如远程服务调用失败、服务不可用等
 *
 * @author Actionow
 */
public class ServiceException extends BaseException {

    private static final long serialVersionUID = 1L;

    public ServiceException(String message) {
        super(ResultCode.SERVICE_UNAVAILABLE.getCode(), message);
    }

    public ServiceException(String code, String message) {
        super(code, message);
    }

    public ServiceException(IResultCode resultCode) {
        super(resultCode);
    }

    public ServiceException(IResultCode resultCode, String message) {
        super(resultCode, message);
    }

    public ServiceException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public ServiceException(IResultCode resultCode, Throwable cause) {
        super(resultCode, cause);
    }

    /**
     * 快速创建服务异常
     */
    public static ServiceException of(String message) {
        return new ServiceException(message);
    }

    /**
     * 快速创建服务异常
     */
    public static ServiceException of(IResultCode resultCode) {
        return new ServiceException(resultCode);
    }
}
