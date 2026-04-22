package com.actionow.ai.plugin.groovy.exception;

/**
 * Groovy脚本执行异常
 *
 * @author Actionow
 */
public class GroovyScriptException extends RuntimeException {

    private final String scriptName;
    private final Integer lineNumber;

    public GroovyScriptException(String message) {
        super(message);
        this.scriptName = null;
        this.lineNumber = null;
    }

    public GroovyScriptException(String message, Throwable cause) {
        super(message, cause);
        this.scriptName = null;
        this.lineNumber = null;
    }

    public GroovyScriptException(String message, String scriptName, Integer lineNumber) {
        super(message);
        this.scriptName = scriptName;
        this.lineNumber = lineNumber;
    }

    public GroovyScriptException(String message, String scriptName, Integer lineNumber, Throwable cause) {
        super(message, cause);
        this.scriptName = scriptName;
        this.lineNumber = lineNumber;
    }

    public String getScriptName() {
        return scriptName;
    }

    public Integer getLineNumber() {
        return lineNumber;
    }

    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder(super.getMessage());
        if (scriptName != null) {
            sb.append(" [script: ").append(scriptName).append("]");
        }
        if (lineNumber != null) {
            sb.append(" [line: ").append(lineNumber).append("]");
        }
        return sb.toString();
    }
}
