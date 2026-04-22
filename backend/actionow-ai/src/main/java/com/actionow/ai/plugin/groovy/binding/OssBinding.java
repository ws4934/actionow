package com.actionow.ai.plugin.groovy.binding;

import com.actionow.common.oss.service.OssService;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * OSS工具绑定
 * 提供脚本中可用的OSS/MinIO操作方法
 *
 * @author Actionow
 */
@Slf4j
public class OssBinding {

    private static final long MULTIPART_THRESHOLD = 20 * 1024 * 1024; // 20MB
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 1000;

    private final OssService ossService;
    private final HttpClient httpClient;

    /**
     * 工作空间ID（用于路径隔离）
     */
    private String workspaceId;

    /**
     * 执行ID（用于文件命名）
     */
    private String executionId;

    public OssBinding(OssService ossService) {
        this.ossService = ossService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * 设置上下文信息
     *
     * @param workspaceId 工作空间ID
     * @param executionId 执行ID
     */
    public void setContext(String workspaceId, String executionId) {
        this.workspaceId = workspaceId;
        this.executionId = executionId;
    }

    /**
     * 从URL下载文件并上传到OSS
     *
     * @param sourceUrl  源文件URL
     * @param objectName 目标对象名称（支持路径，如 images/xxx.png）
     * @return OSS文件URL
     */
    public String uploadFromUrl(String sourceUrl, String objectName) {
        return uploadFromUrl(sourceUrl, objectName, null);
    }

    /**
     * 从URL下载文件并上传到OSS（指定内容类型）
     *
     * @param sourceUrl   源文件URL
     * @param objectName  目标对象名称
     * @param contentType 内容类型（如果为null，则自动检测）
     * @return OSS文件URL
     */
    public String uploadFromUrl(String sourceUrl, String objectName, String contentType) {
        log.info("[OssBinding] Uploading from URL: {} to {}", sourceUrl, objectName);

        return withRetry("uploadFromUrl", () -> {
            String cleanUrl = sanitizeUrl(sourceUrl);
            com.actionow.ai.util.SafeUrlValidator.validate(cleanUrl);

            // HEAD request to get Content-Length (for streaming)
            long contentLength = fetchContentLength(cleanUrl);

            // Download as stream (not byte[])
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(cleanUrl))
                    .timeout(Duration.ofMinutes(10))
                    .GET().build();
            HttpResponse<InputStream> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Download failed: status " + response.statusCode());
            }

            String ct = contentType;
            if (ct == null) {
                ct = response.headers().firstValue("Content-Type")
                        .orElse(detectContentType(objectName));
            }
            String fullPath = buildObjectPath(objectName);

            try (InputStream downloadStream = response.body()) {
                if (contentLength > 0 && contentLength > MULTIPART_THRESHOLD) {
                    return ossService.uploadMultipartFromStream(fullPath, downloadStream, contentLength, ct);
                } else if (contentLength > 0) {
                    return ossService.upload(fullPath, downloadStream, contentLength, ct);
                } else {
                    byte[] data = downloadStream.readAllBytes();
                    return ossService.upload(fullPath, new ByteArrayInputStream(data), data.length, ct);
                }
            }
        });
    }

    /**
     * 从URL下载文件并上传到OSS，返回包含URL和fileKey的结果
     *
     * @param sourceUrl   源文件URL
     * @param objectName  目标对象名称
     * @param contentType 内容类型（如果为null，则自动检测）
     * @return 包含 url 和 fileKey 的 Map
     */
    public Map<String, String> uploadFromUrlWithKey(String sourceUrl, String objectName, String contentType) {
        log.info("[OssBinding] Uploading from URL with key: {} to {}", sourceUrl, objectName);

        return withRetry("uploadFromUrlWithKey", () -> {
            String cleanUrl = sanitizeUrl(sourceUrl);
            com.actionow.ai.util.SafeUrlValidator.validate(cleanUrl);

            // HEAD request to get Content-Length (for streaming)
            long contentLength = fetchContentLength(cleanUrl);

            // Download as stream (not byte[])
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(cleanUrl))
                    .timeout(Duration.ofMinutes(10))
                    .GET().build();
            HttpResponse<InputStream> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Download failed: status " + response.statusCode());
            }

            String ct = contentType;
            if (ct == null) {
                ct = response.headers().firstValue("Content-Type")
                        .orElse(detectContentType(objectName));
            }
            String fullPath = buildObjectPath(objectName);

            String url;
            long actualSize;
            try (InputStream downloadStream = response.body()) {
                if (contentLength > 0 && contentLength > MULTIPART_THRESHOLD) {
                    url = ossService.uploadMultipartFromStream(fullPath, downloadStream, contentLength, ct);
                    actualSize = contentLength;
                } else if (contentLength > 0) {
                    url = ossService.upload(fullPath, downloadStream, contentLength, ct);
                    actualSize = contentLength;
                } else {
                    byte[] data = downloadStream.readAllBytes();
                    url = ossService.upload(fullPath, new ByteArrayInputStream(data), data.length, ct);
                    actualSize = data.length;
                }
            }

            Map<String, String> result = new HashMap<>();
            result.put("url", url);
            result.put("fileKey", fullPath);
            result.put("fileSize", String.valueOf(actualSize));
            log.info("[OssBinding] Upload completed: fileKey={}, size={}", fullPath, actualSize);
            return result;
        });
    }

    /**
     * 上传Base64编码的数据
     *
     * @param base64Data  Base64编码的数据
     * @param objectName  目标对象名称
     * @param contentType 内容类型
     * @return OSS文件URL
     */
    public String uploadBase64(String base64Data, String objectName, String contentType) {
        log.info("[OssBinding] Uploading base64 data to {}", objectName);

        try {
            // 移除可能的data URI前缀
            String cleanBase64 = base64Data;
            if (base64Data.contains(",")) {
                cleanBase64 = base64Data.substring(base64Data.indexOf(",") + 1);
            }

            byte[] data = Base64.getDecoder().decode(cleanBase64);
            String fullPath = buildObjectPath(objectName);

            try (InputStream inputStream = new ByteArrayInputStream(data)) {
                return ossService.upload(fullPath, inputStream, data.length, contentType);
            }

        } catch (IOException e) {
            log.error("[OssBinding] Failed to upload base64 data", e);
            throw new RuntimeException("Failed to upload base64 data", e);
        }
    }

    /**
     * 上传Base64编码的数据，返回包含URL和fileKey的结果
     *
     * @param base64Data  Base64编码的数据
     * @param objectName  目标对象名称
     * @param contentType 内容类型
     * @return 包含 url 和 fileKey 的 Map
     */
    public Map<String, String> uploadBase64WithKey(String base64Data, String objectName, String contentType) {
        log.info("[OssBinding] Uploading base64 data with key to {}", objectName);

        try {
            // 移除可能的data URI前缀
            String cleanBase64 = base64Data;
            if (base64Data.contains(",")) {
                cleanBase64 = base64Data.substring(base64Data.indexOf(",") + 1);
            }

            byte[] data = Base64.getDecoder().decode(cleanBase64);
            String fullPath = buildObjectPath(objectName);

            String url;
            try (InputStream inputStream = new ByteArrayInputStream(data)) {
                url = ossService.upload(fullPath, inputStream, data.length, contentType);
            }

            Map<String, String> result = new HashMap<>();
            result.put("url", url);
            result.put("fileKey", fullPath);
            result.put("fileSize", String.valueOf(data.length));

            log.info("[OssBinding] Base64 upload completed: fileKey={}, url={}", fullPath, url);
            return result;

        } catch (IOException e) {
            log.error("[OssBinding] Failed to upload base64 data", e);
            throw new RuntimeException("Failed to upload base64 data", e);
        }
    }

    /**
     * 上传字节数组
     *
     * @param data        字节数据
     * @param objectName  目标对象名称
     * @param contentType 内容类型
     * @return OSS文件URL
     */
    public String uploadBytes(byte[] data, String objectName, String contentType) {
        log.info("[OssBinding] Uploading {} bytes to {}", data.length, objectName);

        try {
            String fullPath = buildObjectPath(objectName);
            try (InputStream inputStream = new ByteArrayInputStream(data)) {
                return ossService.upload(fullPath, inputStream, data.length, contentType);
            }
        } catch (IOException e) {
            log.error("[OssBinding] Failed to upload bytes", e);
            throw new RuntimeException("Failed to upload bytes", e);
        }
    }

    /**
     * 获取文件的预签名下载URL
     *
     * @param objectName    对象名称
     * @param expireSeconds 过期时间（秒）
     * @return 预签名URL
     */
    public String getPresignedUrl(String objectName, int expireSeconds) {
        String fullPath = buildObjectPath(objectName);
        return ossService.getPresignedDownloadUrl(fullPath, expireSeconds);
    }

    /**
     * 获取文件的永久访问URL
     *
     * @param objectName 对象名称
     * @return 文件URL
     */
    public String getUrl(String objectName) {
        String fullPath = buildObjectPath(objectName);
        return ossService.getUrl(fullPath);
    }

    /**
     * 检查文件是否存在
     *
     * @param objectName 对象名称
     * @return 是否存在
     */
    public boolean exists(String objectName) {
        String fullPath = buildObjectPath(objectName);
        return ossService.exists(fullPath);
    }

    /**
     * 删除文件
     *
     * @param objectName 对象名称
     */
    public void delete(String objectName) {
        log.info("[OssBinding] Deleting object: {}", objectName);
        String fullPath = buildObjectPath(objectName);
        ossService.delete(fullPath);
    }

    /**
     * 生成唯一文件名
     *
     * @param extension 文件扩展名（如 png, jpg, mp4）
     * @return 唯一文件名
     */
    public String generateFileName(String extension) {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        if (executionId != null) {
            return executionId + "_" + uuid + "." + extension;
        }
        return uuid + "." + extension;
    }

    /**
     * 生成带路径的唯一文件名（适配新目录结构）
     * 路径格式: {mediaDir}/ai-generated/{yyyy/MM/dd}/{filename}
     * 完整路径（含租户前缀）: tenant_{workspaceId}/{mediaDir}/ai-generated/{yyyy/MM/dd}/{filename}
     *
     * @param directory 目录（如 images, videos, audios）
     * @param extension 文件扩展名
     * @return 完整路径（不含租户前缀，会在 buildObjectPath 中添加）
     */
    public String generatePath(String directory, String extension) {
        String datePath = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String filename = generateFileName(extension);
        return directory + "/ai-generated/" + datePath + "/" + filename;
    }

    /**
     * 生成AI生成文件的完整路径
     *
     * @param mediaType 媒体类型（image, video, audio）
     * @param extension 文件扩展名
     * @return 完整OSS路径（含租户前缀）
     */
    public String generateAiOutputPath(String mediaType, String extension) {
        String mediaDir = switch (mediaType.toLowerCase()) {
            case "image" -> "images";
            case "video" -> "videos";
            case "audio" -> "audios";
            default -> "documents";
        };
        String relativePath = generatePath(mediaDir, extension);
        return buildObjectPath(relativePath);
    }

    /**
     * 获取文件信息
     *
     * @param objectName 对象名称
     * @return 文件信息Map
     */
    public Map<String, Object> getFileInfo(String objectName) {
        Map<String, Object> info = new HashMap<>();
        String fullPath = buildObjectPath(objectName);

        info.put("objectName", fullPath);
        info.put("exists", ossService.exists(fullPath));
        info.put("url", ossService.getUrl(fullPath));

        return info;
    }

    /**
     * 构建完整的对象路径（添加租户空间隔离）
     * 新格式: tenant_{workspaceId}/{relativePath}
     */
    private String buildObjectPath(String objectName) {
        if (workspaceId == null) {
            return objectName;
        }

        String tenantPrefix = "tenant_" + workspaceId;

        // 如果已经包含租户前缀，直接返回
        if (objectName.startsWith(tenantPrefix + "/") || objectName.startsWith(tenantPrefix)) {
            return objectName;
        }

        // 如果使用旧格式（workspaceId/...），转换为新格式
        if (objectName.startsWith(workspaceId + "/")) {
            String relativePath = objectName.substring(workspaceId.length() + 1);
            return tenantPrefix + "/" + relativePath;
        }

        // 添加租户前缀
        if (objectName.startsWith("/")) {
            return tenantPrefix + objectName;
        }
        return tenantPrefix + "/" + objectName;
    }

    // ==================== 重试与流式辅助方法 ====================

    private long fetchContentLength(String url) {
        try {
            HttpRequest head = HttpRequest.newBuilder()
                    .uri(URI.create(url)).timeout(Duration.ofSeconds(15))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
            HttpResponse<Void> resp = httpClient.send(head, HttpResponse.BodyHandlers.discarding());
            return resp.headers().firstValueAsLong("Content-Length").orElse(-1);
        } catch (Exception e) {
            log.warn("[OssBinding] HEAD request failed, will use unknown size: {}", e.getMessage());
            return -1;
        }
    }

    private <T> T withRetry(String operation, Callable<T> action) {
        int attempt = 0;
        Exception lastException = null;
        while (attempt < MAX_RETRIES) {
            try {
                return action.call();
            } catch (Exception e) {
                lastException = e;
                attempt++;
                if (attempt < MAX_RETRIES && isRetryable(e)) {
                    long backoff = INITIAL_BACKOFF_MS * (1L << (attempt - 1));
                    log.warn("[OssBinding] {} failed (attempt {}/{}), retrying in {}ms: {}",
                            operation, attempt, MAX_RETRIES, backoff, e.getMessage());
                    try { Thread.sleep(backoff); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(ie);
                    }
                } else { break; }
            }
        }
        throw new RuntimeException("Failed after " + attempt + " attempts: " + operation, lastException);
    }

    private boolean isRetryable(Exception e) {
        if (e instanceof java.net.SocketException || e instanceof java.net.SocketTimeoutException) return true;
        String msg = e.getMessage();
        if (msg == null) return false;
        String lower = msg.toLowerCase();
        return lower.contains("timeout") || lower.contains("connection reset")
                || lower.contains("503") || lower.contains("500");
    }

    /**
     * 清理URL中的不可见字符和特殊unicode字符
     * 某些AI服务返回的URL可能包含零宽字符等不可见字符，导致Java HTTP客户端无法解析
     *
     * @param url 原始URL
     * @return 清理后的URL
     */
    private String sanitizeUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }

        // 移除零宽字符和其他不可见unicode字符
        // \u200B: Zero-width space
        // \u200C: Zero-width non-joiner
        // \u200D: Zero-width joiner
        // \uFEFF: Byte order mark
        // \u2060: Word joiner
        // \u00A0: Non-breaking space
        String sanitized = url
                .replace("\u200B", "")
                .replace("\u200C", "")
                .replace("\u200D", "")
                .replace("\uFEFF", "")
                .replace("\u2060", "")
                .replace("\u00A0", " ")
                .trim();

        // 移除其他控制字符 (Unicode category Cc)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sanitized.length(); i++) {
            char c = sanitized.charAt(i);
            // 保留可打印字符和标准空格
            if (!Character.isISOControl(c) || c == '\t' || c == '\n' || c == '\r') {
                sb.append(c);
            }
        }
        sanitized = sb.toString().trim();

        if (!url.equals(sanitized)) {
            log.warn("[OssBinding] URL was sanitized, removed invisible characters. Original length: {}, Sanitized length: {}",
                    url.length(), sanitized.length());
        }

        return sanitized;
    }

    /**
     * 根据文件扩展名检测内容类型
     */
    private String detectContentType(String objectName) {
        if (objectName == null) {
            return "application/octet-stream";
        }

        String lower = objectName.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lower.endsWith(".gif")) {
            return "image/gif";
        } else if (lower.endsWith(".webp")) {
            return "image/webp";
        } else if (lower.endsWith(".mp4")) {
            return "video/mp4";
        } else if (lower.endsWith(".webm")) {
            return "video/webm";
        } else if (lower.endsWith(".mp3")) {
            return "audio/mpeg";
        } else if (lower.endsWith(".wav")) {
            return "audio/wav";
        } else if (lower.endsWith(".ogg")) {
            return "audio/ogg";
        } else if (lower.endsWith(".json")) {
            return "application/json";
        } else if (lower.endsWith(".txt")) {
            return "text/plain";
        }

        return "application/octet-stream";
    }
}
