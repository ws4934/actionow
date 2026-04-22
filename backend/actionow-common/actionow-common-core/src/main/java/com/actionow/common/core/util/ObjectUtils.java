package com.actionow.common.core.util;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 对象工具类
 *
 * @author Actionow
 */
public final class ObjectUtils {

    private ObjectUtils() {
    }

    /**
     * 判断对象是否为空
     */
    public static boolean isEmpty(Object obj) {
        if (obj == null) {
            return true;
        }
        if (obj instanceof String str) {
            return str.isEmpty();
        }
        if (obj instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        if (obj instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        if (obj instanceof Object[] array) {
            return array.length == 0;
        }
        if (obj instanceof Optional<?> optional) {
            return optional.isEmpty();
        }
        return false;
    }

    /**
     * 判断对象是否不为空
     */
    public static boolean isNotEmpty(Object obj) {
        return !isEmpty(obj);
    }

    /**
     * 如果为空则返回默认值
     */
    public static <T> T defaultIfNull(T object, T defaultValue) {
        return object != null ? object : defaultValue;
    }

    /**
     * 如果为空则通过 Supplier 获取默认值
     */
    public static <T> T defaultIfNull(T object, Supplier<T> defaultSupplier) {
        return object != null ? object : defaultSupplier.get();
    }

    /**
     * 如果字符串为空则返回默认值
     */
    public static String defaultIfBlank(String str, String defaultValue) {
        return isBlank(str) ? defaultValue : str;
    }

    /**
     * 判断字符串是否为空白
     */
    public static boolean isBlank(String str) {
        return str == null || str.isBlank();
    }

    /**
     * 判断字符串是否不为空白
     */
    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    /**
     * 比较两个对象是否相等（空安全）
     */
    public static boolean equals(Object a, Object b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.equals(b);
    }

    /**
     * 获取对象的字符串值
     */
    public static String toString(Object obj) {
        return obj != null ? obj.toString() : null;
    }

    /**
     * 获取对象的字符串值，为空时返回默认值
     */
    public static String toString(Object obj, String defaultValue) {
        return obj != null ? obj.toString() : defaultValue;
    }
}
