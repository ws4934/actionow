package com.actionow.common.file.exception;

import com.actionow.common.core.exception.BusinessException;

/**
 * 文件服务异常
 *
 * @author Actionow
 */
public class FileException extends BusinessException {

    public FileException(FileErrorCode errorCode) {
        super(errorCode);
    }

    public FileException(FileErrorCode errorCode, Throwable cause) {
        super(errorCode.getCode(), errorCode.getMessage(), cause);
    }

    public FileException(FileErrorCode errorCode, String detail) {
        super(errorCode.getCode(), errorCode.getMessage() + ": " + detail);
    }
}
