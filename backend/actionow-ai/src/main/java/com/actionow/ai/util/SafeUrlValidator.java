package com.actionow.ai.util;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Set;

/**
 * URL 安全校验器
 * 防止 SSRF 攻击：仅允许 http/https scheme，拒绝内网/环回/元数据 IP
 *
 * @author Actionow
 */
public final class SafeUrlValidator {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    private SafeUrlValidator() {
    }

    /**
     * 校验 URL 安全性，不安全则抛出 IllegalArgumentException
     */
    public static void validate(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL 不能为空");
        }

        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("URL 格式无效: " + url, e);
        }

        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
            throw new IllegalArgumentException("不允许的 URL scheme: " + scheme + "，仅支持 http/https");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL 缺少 host: " + url);
        }

        // 解析域名为 IP 并检查
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (isUnsafeAddress(addr)) {
                    throw new IllegalArgumentException(
                            "URL 指向内网/保留地址，已拒绝: " + host + " -> " + addr.getHostAddress());
                }
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("无法解析 URL host: " + host, e);
        }
    }

    private static boolean isUnsafeAddress(InetAddress addr) {
        return addr.isLoopbackAddress()
                || addr.isSiteLocalAddress()
                || addr.isLinkLocalAddress()
                || addr.isAnyLocalAddress()
                || addr.isMulticastAddress()
                || isMetadataAddress(addr);
    }

    /**
     * 检查云元数据端点 IP (169.254.169.254)
     */
    private static boolean isMetadataAddress(InetAddress addr) {
        byte[] bytes = addr.getAddress();
        if (bytes.length == 4) {
            return (bytes[0] & 0xFF) == 169 && (bytes[1] & 0xFF) == 254
                    && (bytes[2] & 0xFF) == 169 && (bytes[3] & 0xFF) == 254;
        }
        return false;
    }
}
