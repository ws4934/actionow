package com.actionow.ai.plugin.groovy.exception;

/**
 * Groovy脚本安全违规异常
 *
 * @author Actionow
 */
public class GroovySecurityException extends GroovyScriptException {

    private final String violationType;
    private final String blockedClass;

    public GroovySecurityException(String message) {
        super(message);
        this.violationType = "UNKNOWN";
        this.blockedClass = null;
    }

    public GroovySecurityException(String message, String violationType) {
        super(message);
        this.violationType = violationType;
        this.blockedClass = null;
    }

    public GroovySecurityException(String message, String violationType, String blockedClass) {
        super(message);
        this.violationType = violationType;
        this.blockedClass = blockedClass;
    }

    public String getViolationType() {
        return violationType;
    }

    public String getBlockedClass() {
        return blockedClass;
    }

    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder(super.getMessage());
        sb.append(" [violation: ").append(violationType).append("]");
        if (blockedClass != null) {
            sb.append(" [blocked: ").append(blockedClass).append("]");
        }
        return sb.toString();
    }
}
