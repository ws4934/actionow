package com.actionow.common.mail.exception;

/**
 * 邮件发送异常
 *
 * @author Actionow
 */
public class MailException extends RuntimeException {

    private final String errorCode;

    public MailException(String message) {
        super(message);
        this.errorCode = "MAIL_ERROR";
    }

    public MailException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "MAIL_ERROR";
    }

    public MailException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public MailException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
