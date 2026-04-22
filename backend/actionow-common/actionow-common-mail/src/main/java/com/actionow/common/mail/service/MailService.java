package com.actionow.common.mail.service;

import com.actionow.common.mail.dto.MailRequest;
import com.actionow.common.mail.dto.MailResult;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 邮件服务接口
 *
 * @author Actionow
 */
public interface MailService {

    /**
     * 发送邮件
     *
     * @param request 邮件请求
     * @return 发送结果
     */
    MailResult send(MailRequest request);

    /**
     * 异步发送邮件
     *
     * @param request 邮件请求
     * @return 发送结果 Future
     */
    CompletableFuture<MailResult> sendAsync(MailRequest request);

    /**
     * 发送简单文本邮件
     *
     * @param to      收件人
     * @param subject 主题
     * @param text    内容
     * @return 发送结果
     */
    MailResult sendSimple(String to, String subject, String text);

    /**
     * 发送 HTML 邮件
     *
     * @param to      收件人
     * @param subject 主题
     * @param html    HTML 内容
     * @return 发送结果
     */
    MailResult sendHtml(String to, String subject, String html);

    /**
     * 发送模板邮件
     *
     * @param to           收件人
     * @param subject      主题
     * @param templateName 模板名称
     * @param variables    模板变量
     * @return 发送结果
     */
    MailResult sendTemplate(String to, String subject, String templateName, Map<String, Object> variables);

    /**
     * 发送验证码邮件
     *
     * @param to   收件人
     * @param code 验证码
     * @return 发送结果
     */
    MailResult sendVerificationCode(String to, String code);

    /**
     * 发送密码重置邮件
     *
     * @param to        收件人
     * @param resetLink 重置链接
     * @return 发送结果
     */
    MailResult sendPasswordReset(String to, String resetLink);

    /**
     * 发送欢迎邮件
     *
     * @param to       收件人
     * @param username 用户名
     * @return 发送结果
     */
    MailResult sendWelcome(String to, String username);

    /**
     * 发送安全提醒邮件（密码修改、密码重置、账号锁定等）
     *
     * @param to           收件人
     * @param alertType    提醒类型：PASSWORD_CHANGED / PASSWORD_RESET / ACCOUNT_LOCKED
     * @param alertTitle   提醒标题
     * @param message      提醒消息
     * @param actionTime   操作时间
     * @param ipAddress    IP 地址（可为 null）
     * @param actionDetail 操作详情（可为 null）
     * @return 发送结果
     */
    MailResult sendSecurityAlert(String to, String alertType, String alertTitle,
                                 String message, String actionTime,
                                 String ipAddress, String actionDetail);
}
