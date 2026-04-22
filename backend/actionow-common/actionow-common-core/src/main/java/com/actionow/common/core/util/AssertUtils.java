package com.actionow.common.core.util;

import java.util.Collection;
import java.util.Map;

/**
 * 断言工具类
 * 用于参数校验，失败时抛出业务异常
 *
 * @author Actionow
 */
public final class AssertUtils {

    private AssertUtils() {
    }

    /**
     * 断言为真
     */
    public static void isTrue(boolean expression, String message) {
        if (!expression) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 断言为假
     */
    public static void isFalse(boolean expression, String message) {
        isTrue(!expression, message);
    }

    /**
     * 断言不为空
     */
    public static void notNull(Object object, String message) {
        if (object == null) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 断言为空
     */
    public static void isNull(Object object, String message) {
        if (object != null) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 断言字符串不为空
     */
    public static void notBlank(String text, String message) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 断言字符串有内容
     */
    public static void hasText(String text, String message) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 断言集合不为空
     */
    public static void notEmpty(Collection<?> collection, String message) {
        if (collection == null || collection.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 断言 Map 不为空
     */
    public static void notEmpty(Map<?, ?> map, String message) {
        if (map == null || map.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 断言数组不为空
     */
    public static void notEmpty(Object[] array, String message) {
        if (array == null || array.length == 0) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 断言字符串长度范围
     */
    public static void lengthBetween(String text, int min, int max, String message) {
        notNull(text, message);
        int length = text.length();
        if (length < min || length > max) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 断言数值范围
     */
    public static void between(long value, long min, long max, String message) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 断言正数
     */
    public static void positive(long value, String message) {
        if (value <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 断言非负数
     */
    public static void notNegative(long value, String message) {
        if (value < 0) {
            throw new IllegalArgumentException(message);
        }
    }
}
