package com.actionow.ai.plugin.groovy.binding;

import lombok.extern.slf4j.Slf4j;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.StringJoiner;

/**
 * HTTP工具绑定
 * 提供脚本中可用的HTTP辅助方法
 *
 * @author Actionow
 */
@Slf4j
public class HttpBinding {

    /**
     * URL编码
     *
     * @param value 要编码的值
     * @return 编码后的字符串
     */
    public String encodeUrl(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * URL解码
     *
     * @param value 要解码的值
     * @return 解码后的字符串
     */
    public String decodeUrl(String value) {
        if (value == null) {
            return "";
        }
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    /**
     * 构建查询字符串
     *
     * @param params 参数Map
     * @return 查询字符串（不含?）
     */
    public String buildQueryString(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }

        StringJoiner joiner = new StringJoiner("&");
        params.forEach((key, value) -> {
            if (value != null) {
                if (value instanceof Iterable) {
                    ((Iterable<?>) value).forEach(v ->
                            joiner.add(encodeUrl(key) + "=" + encodeUrl(String.valueOf(v))));
                } else {
                    joiner.add(encodeUrl(key) + "=" + encodeUrl(String.valueOf(value)));
                }
            }
        });
        return joiner.toString();
    }

    /**
     * 构建完整URL
     *
     * @param baseUrl 基础URL
     * @param path    路径
     * @param params  查询参数
     * @return 完整URL
     */
    public String buildUrl(String baseUrl, String path, Map<String, Object> params) {
        StringBuilder url = new StringBuilder();

        // 基础URL
        if (baseUrl != null) {
            url.append(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
        }

        // 路径
        if (path != null && !path.isEmpty()) {
            if (!path.startsWith("/")) {
                url.append("/");
            }
            url.append(path);
        }

        // 查询参数
        String queryString = buildQueryString(params);
        if (!queryString.isEmpty()) {
            url.append("?").append(queryString);
        }

        return url.toString();
    }

    /**
     * 构建完整URL（无查询参数）
     */
    public String buildUrl(String baseUrl, String path) {
        return buildUrl(baseUrl, path, null);
    }

    /**
     * 解析查询字符串为Map
     *
     * @param queryString 查询字符串
     * @return 参数Map
     */
    public Map<String, String> parseQueryString(String queryString) {
        Map<String, String> params = new java.util.HashMap<>();
        if (queryString == null || queryString.isEmpty()) {
            return params;
        }

        // 移除开头的?
        if (queryString.startsWith("?")) {
            queryString = queryString.substring(1);
        }

        String[] pairs = queryString.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                String key = decodeUrl(pair.substring(0, idx));
                String value = idx < pair.length() - 1 ? decodeUrl(pair.substring(idx + 1)) : "";
                params.put(key, value);
            }
        }
        return params;
    }

    /**
     * 从URL中提取路径
     *
     * @param url 完整URL
     * @return 路径部分
     */
    public String extractPath(String url) {
        if (url == null) {
            return "";
        }

        // 移除协议
        int protocolEnd = url.indexOf("://");
        if (protocolEnd > 0) {
            url = url.substring(protocolEnd + 3);
        }

        // 移除域名
        int pathStart = url.indexOf("/");
        if (pathStart < 0) {
            return "/";
        }
        url = url.substring(pathStart);

        // 移除查询参数
        int queryStart = url.indexOf("?");
        if (queryStart > 0) {
            url = url.substring(0, queryStart);
        }

        return url;
    }

    /**
     * 拼接路径
     *
     * @param parts 路径部分
     * @return 拼接后的路径
     */
    public String joinPath(String... parts) {
        if (parts == null || parts.length == 0) {
            return "/";
        }

        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isEmpty()) {
                continue;
            }

            // 移除开头的斜杠（除非是第一个）
            if (result.length() > 0 && part.startsWith("/")) {
                part = part.substring(1);
            }

            // 确保不以斜杠结尾（除非是最后一个）
            if (result.length() > 0 && !result.toString().endsWith("/")) {
                result.append("/");
            }

            result.append(part);
        }

        return result.toString();
    }

    /**
     * 替换路径中的变量
     * 如: /users/{userId}/posts/{postId}
     *
     * @param pathTemplate 路径模板
     * @param variables    变量Map
     * @return 替换后的路径
     */
    public String expandPath(String pathTemplate, Map<String, Object> variables) {
        if (pathTemplate == null || variables == null) {
            return pathTemplate;
        }

        String result = pathTemplate;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            String value = entry.getValue() != null ? String.valueOf(entry.getValue()) : "";
            result = result.replace(placeholder, encodeUrl(value));
        }
        return result;
    }
}
