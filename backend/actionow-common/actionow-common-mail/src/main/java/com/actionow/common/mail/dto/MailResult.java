package com.actionow.common.mail.dto;

import java.time.Instant;

/**
 * 邮件发送结果
 *
 * @author Actionow
 */
public class MailResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 消息 ID（AWS SES 返回）
     */
    private String messageId;

    /**
     * 错误消息
     */
    private String errorMessage;

    /**
     * 发送时间
     */
    private Instant sentAt;

    public static MailResult success(String messageId) {
        MailResult result = new MailResult();
        result.success = true;
        result.messageId = messageId;
        result.sentAt = Instant.now();
        return result;
    }

    public static MailResult failure(String errorMessage) {
        MailResult result = new MailResult();
        result.success = false;
        result.errorMessage = errorMessage;
        result.sentAt = Instant.now();
        return result;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }
}
