package com.actionow.ai.plugin.groovy.binding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 日志工具绑定
 * 提供脚本中可用的日志方法
 *
 * @author Actionow
 */
public class LogBinding {

    private static final Logger log = LoggerFactory.getLogger("GroovyScript");

    /**
     * 记录信息日志
     *
     * @param message 日志消息
     */
    public void info(String message) {
        log.info("[Script] {}", message);
    }

    /**
     * 记录信息日志（带参数）
     *
     * @param format 日志格式
     * @param args   参数
     */
    public void info(String format, Object... args) {
        log.info("[Script] " + format, args);
    }

    /**
     * 记录调试日志
     *
     * @param message 日志消息
     */
    public void debug(String message) {
        log.debug("[Script] {}", message);
    }

    /**
     * 记录调试日志（带参数）
     *
     * @param format 日志格式
     * @param args   参数
     */
    public void debug(String format, Object... args) {
        log.debug("[Script] " + format, args);
    }

    /**
     * 记录警告日志
     *
     * @param message 日志消息
     */
    public void warn(String message) {
        log.warn("[Script] {}", message);
    }

    /**
     * 记录警告日志（带参数）
     *
     * @param format 日志格式
     * @param args   参数
     */
    public void warn(String format, Object... args) {
        log.warn("[Script] " + format, args);
    }

    /**
     * 记录错误日志
     *
     * @param message 日志消息
     */
    public void error(String message) {
        log.error("[Script] {}", message);
    }

    /**
     * 记录错误日志（带参数）
     *
     * @param format 日志格式
     * @param args   参数
     */
    public void error(String format, Object... args) {
        log.error("[Script] " + format, args);
    }

    /**
     * 记录错误日志（带异常）
     *
     * @param message   日志消息
     * @param throwable 异常
     */
    public void error(String message, Throwable throwable) {
        log.error("[Script] {}", message, throwable);
    }

    /**
     * 打印对象（调试用）
     *
     * @param obj 要打印的对象
     */
    public void print(Object obj) {
        if (obj == null) {
            log.info("[Script] null");
        } else {
            log.info("[Script] {} = {}", obj.getClass().getSimpleName(), obj);
        }
    }

    /**
     * 打印对象（带标签）
     *
     * @param label 标签
     * @param obj   要打印的对象
     */
    public void print(String label, Object obj) {
        if (obj == null) {
            log.info("[Script] {} = null", label);
        } else {
            log.info("[Script] {} = {}", label, obj);
        }
    }
}
