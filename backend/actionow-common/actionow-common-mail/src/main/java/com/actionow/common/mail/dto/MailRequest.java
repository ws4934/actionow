package com.actionow.common.mail.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 邮件请求 DTO
 *
 * @author Actionow
 */
public class MailRequest {

    /**
     * 收件人列表
     */
    private List<String> to = new ArrayList<>();

    /**
     * 抄送列表
     */
    private List<String> cc = new ArrayList<>();

    /**
     * 密送列表
     */
    private List<String> bcc = new ArrayList<>();

    /**
     * 邮件主题
     */
    private String subject;

    /**
     * 邮件内容（纯文本）
     */
    private String text;

    /**
     * 邮件内容（HTML）
     */
    private String html;

    /**
     * 模板名称
     */
    private String templateName;

    /**
     * 模板变量
     */
    private Map<String, Object> templateVariables = new HashMap<>();

    /**
     * 附件列表
     */
    private List<Attachment> attachments = new ArrayList<>();

    /**
     * 自定义发件人（覆盖默认配置）
     */
    private String from;

    /**
     * 自定义发件人名称
     */
    private String fromName;

    /**
     * 回复地址
     */
    private String replyTo;

    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }

    public List<String> getTo() {
        return to;
    }

    public void setTo(List<String> to) {
        this.to = to;
    }

    public List<String> getCc() {
        return cc;
    }

    public void setCc(List<String> cc) {
        this.cc = cc;
    }

    public List<String> getBcc() {
        return bcc;
    }

    public void setBcc(List<String> bcc) {
        this.bcc = bcc;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getHtml() {
        return html;
    }

    public void setHtml(String html) {
        this.html = html;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public Map<String, Object> getTemplateVariables() {
        return templateVariables;
    }

    public void setTemplateVariables(Map<String, Object> templateVariables) {
        this.templateVariables = templateVariables;
    }

    public List<Attachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<Attachment> attachments) {
        this.attachments = attachments;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }

    public String getReplyTo() {
        return replyTo;
    }

    public void setReplyTo(String replyTo) {
        this.replyTo = replyTo;
    }

    public static class Attachment {
        private String filename;
        private byte[] content;
        private String contentType;

        public Attachment() {
        }

        public Attachment(String filename, byte[] content, String contentType) {
            this.filename = filename;
            this.content = content;
            this.contentType = contentType;
        }

        public String getFilename() {
            return filename;
        }

        public void setFilename(String filename) {
            this.filename = filename;
        }

        public byte[] getContent() {
            return content;
        }

        public void setContent(byte[] content) {
            this.content = content;
        }

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }
    }

    public static class Builder {
        private final MailRequest request = new MailRequest();

        public Builder to(String... recipients) {
            request.to.addAll(List.of(recipients));
            return this;
        }

        public Builder cc(String... recipients) {
            request.cc.addAll(List.of(recipients));
            return this;
        }

        public Builder bcc(String... recipients) {
            request.bcc.addAll(List.of(recipients));
            return this;
        }

        public Builder subject(String subject) {
            request.subject = subject;
            return this;
        }

        public Builder text(String text) {
            request.text = text;
            return this;
        }

        public Builder html(String html) {
            request.html = html;
            return this;
        }

        public Builder template(String templateName) {
            request.templateName = templateName;
            return this;
        }

        public Builder variable(String key, Object value) {
            request.templateVariables.put(key, value);
            return this;
        }

        public Builder variables(Map<String, Object> variables) {
            request.templateVariables.putAll(variables);
            return this;
        }

        public Builder attachment(String filename, byte[] content, String contentType) {
            request.attachments.add(new Attachment(filename, content, contentType));
            return this;
        }

        public Builder from(String from) {
            request.from = from;
            return this;
        }

        public Builder fromName(String fromName) {
            request.fromName = fromName;
            return this;
        }

        public Builder replyTo(String replyTo) {
            request.replyTo = replyTo;
            return this;
        }

        public MailRequest build() {
            return request;
        }
    }
}
