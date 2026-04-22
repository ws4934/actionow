package com.actionow.common.core.util;

/**
 * Web工具类
 * 提供HTTP请求相关的框架无关通用工具方法
 *
 * @author Actionow
 */
public final class WebUtils {

    private WebUtils() {
    }

    private static final String UNKNOWN = "unknown";

    /**
     * 从HTTP头值中获取客户端真实IP地址
     *
     * 优先级：X-Forwarded-For > X-Real-IP > remoteAddr
     *
     * @param xForwardedFor X-Forwarded-For 头的值
     * @param xRealIp X-Real-IP 头的值
     * @param remoteAddr 远程地址
     * @return 客户端IP地址
     */
    public static String getClientIp(String xForwardedFor, String xRealIp, String remoteAddr) {
        String ip = xForwardedFor;

        if (isBlankOrUnknown(ip)) {
            ip = xRealIp;
        }

        if (isBlankOrUnknown(ip)) {
            ip = remoteAddr;
        }

        return extractFirstIp(ip);
    }

    /**
     * 从可能包含多个IP的字符串中提取第一个IP
     */
    public static String extractFirstIp(String ip) {
        if (isBlankOrUnknown(ip)) {
            return UNKNOWN;
        }
        if (ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 判断字符串是否为空或为"unknown"
     */
    private static boolean isBlankOrUnknown(String str) {
        return str == null || str.isEmpty() || UNKNOWN.equalsIgnoreCase(str);
    }

    /**
     * 判断是否为内网IP
     *
     * @param ip IP地址
     * @return 是否为内网IP
     */
    public static boolean isInternalIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }

        // localhost
        if ("127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "localhost".equalsIgnoreCase(ip)) {
            return true;
        }

        // 内网IP段
        // 10.0.0.0 - 10.255.255.255
        // 172.16.0.0 - 172.31.255.255
        // 192.168.0.0 - 192.168.255.255
        return ip.startsWith("10.")
                || ip.startsWith("192.168.")
                || (ip.startsWith("172.") && isIn172PrivateRange(ip));
    }

    private static boolean isIn172PrivateRange(String ip) {
        try {
            String[] parts = ip.split("\\.");
            if (parts.length >= 2) {
                int second = Integer.parseInt(parts[1]);
                return second >= 16 && second <= 31;
            }
        } catch (NumberFormatException e) {
            // 忽略解析错误
        }
        return false;
    }
}
