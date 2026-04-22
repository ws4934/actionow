package com.actionow.ai.plugin.groovy.binding;

import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 响应处理辅助工具
 * 封装 Groovy 响应映射脚本中常用的重复逻辑：
 * - 错误检查
 * - 状态映射
 * - 文件 URL / 缩略图 / 任务 ID / 尺寸提取
 * - 媒体类型推断
 * - OSS 上传
 * - 标准响应一键构建
 *
 * 通过 Groovy 上下文的 {@code resp} 变量暴露给脚本。
 *
 * @author Actionow
 */
@Slf4j
public class ResponseHelper {

    private final JsonBinding json;
    private final OssBinding oss;  // nullable

    /**
     * 文件 URL 候选字段名
     */
    private static final List<String> URL_FIELDS = List.of(
            "url", "file_url", "image_url", "video_url", "audio_url", "output_url"
    );

    /**
     * 缩略图候选字段名
     */
    private static final List<String> THUMBNAIL_FIELDS = List.of(
            "thumbnail_url", "thumbnailUrl", "thumbnail",
            "cover_url", "coverUrl", "cover",
            "poster_url", "posterUrl", "poster"
    );

    /**
     * 数据路径前缀（从高到低优先级）
     */
    private static final List<String> DATA_PREFIXES = List.of(
            "$.data.", "$.output.", "$.result.", "$."
    );

    /**
     * 默认成功状态
     */
    private static final Set<String> DEFAULT_SUCCESS = Set.of(
            "success", "succeeded", "completed", "done", "finished"
    );

    /**
     * 默认失败状态
     */
    private static final Set<String> DEFAULT_FAILED = Set.of(
            "failed", "error", "failure", "cancelled", "canceled"
    );

    /**
     * 默认运行中状态
     */
    private static final Set<String> DEFAULT_RUNNING = Set.of(
            "running", "processing", "in_progress", "working"
    );

    /**
     * 默认等待状态
     */
    private static final Set<String> DEFAULT_PENDING = Set.of(
            "pending", "submitted", "queued"
    );

    public ResponseHelper(JsonBinding json, OssBinding oss) {
        this.json = json;
        this.oss = oss;
    }

    // ==================== 错误检查 ====================

    /**
     * 检查响应是否为空或包含错误。
     * 返回 StandardResponse 格式的 error map，如果无错误返回 null。
     *
     * @param response API 原始响应
     * @return error map 或 null
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> checkError(Object response) {
        if (response == null) {
            return buildErrorResponse("EMPTY_RESPONSE", "API 返回空响应", true);
        }

        if (response instanceof Map<?, ?> map) {
            Object errorObj = map.get("error");
            if (errorObj instanceof Map<?, ?> errorMap) {
                String code = firstNonNull(errorMap, "code", "type");
                String message = firstNonNull(errorMap, "message");
                boolean retryable = isRetryableError(code);
                Map<String, Object> result = buildErrorResponse(
                        code != null ? code : "API_ERROR",
                        message != null ? message : "API 调用失败",
                        retryable
                );
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("raw", response);
                result.put("metadata", metadata);
                return result;
            }
            // 有些 API 用顶层 error 字段传字符串
            if (errorObj instanceof String errorStr && !errorStr.isBlank()) {
                return buildErrorResponse("API_ERROR", errorStr, false);
            }
        }

        return null;
    }

    // ==================== 状态映射 ====================

    /**
     * 从响应中提取状态并标准化。
     * 探测路径：$.status → $.state → $.code
     *
     * @param response API 响应
     * @return SUCCEEDED / FAILED / PENDING / RUNNING
     */
    public String resolveStatus(Object response) {
        return resolveStatus(response, null, null, null);
    }

    /**
     * 从响应中提取状态并标准化（支持自定义映射）。
     *
     * @param response        API 响应
     * @param successStatuses 自定义成功状态列表（null 使用默认）
     * @param failedStatuses  自定义失败状态列表（null 使用默认）
     * @param statusPath      自定义状态字段 JSON 路径（null 使用默认）
     * @return SUCCEEDED / FAILED / PENDING / RUNNING
     */
    public String resolveStatus(Object response,
                                List<String> successStatuses,
                                List<String> failedStatuses,
                                String statusPath) {
        String raw = extractStatusRaw(response, statusPath);
        if (raw == null || raw.isBlank()) {
            return "SUCCEEDED";
        }

        String lower = raw.toLowerCase();

        Set<String> success = successStatuses != null
                ? new HashSet<>(successStatuses.stream().map(s -> s != null ? s.toLowerCase() : "").toList())
                : DEFAULT_SUCCESS;
        Set<String> failed = failedStatuses != null
                ? new HashSet<>(failedStatuses.stream().map(s -> s != null ? s.toLowerCase() : "").toList())
                : DEFAULT_FAILED;

        if (success.contains(lower)) return "SUCCEEDED";
        if (failed.contains(lower)) return "FAILED";
        if (DEFAULT_RUNNING.contains(lower)) return "RUNNING";
        if (DEFAULT_PENDING.contains(lower)) return "PENDING";

        return "SUCCEEDED";
    }

    // ==================== 字段提取 ====================

    /**
     * 从响应中提取第一个非空文件 URL。
     * 搜索路径：data/output/result 子对象 → 顶层 → images 数组
     */
    public String extractFileUrl(Object response) {
        if (response == null) return null;

        // 从 data/result/output 子对象中搜索
        Object data = firstNonNullPath(response, "$.data", "$.result", "$.output");
        if (data != null) {
            String url = extractUrlFromObject(data);
            if (url != null) return url;
        }

        // 从顶层字段搜索
        for (String field : URL_FIELDS) {
            Object val = json.path(response, "$." + field);
            if (val != null && !val.toString().isBlank()) {
                return val.toString();
            }
        }

        // 从数组中提取
        return extractFirstUrlFromArray(response);
    }

    /**
     * 提取所有文件 URL（支持数组形式的批量输出）。
     */
    @SuppressWarnings("unchecked")
    public List<String> extractAllFileUrls(Object response) {
        List<String> urls = new ArrayList<>();

        Object data = firstNonNullPath(response, "$.data", "$.images", "$.results");

        if (data instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    String url = extractUrlFromMap((Map<String, Object>) map);
                    if (url != null) urls.add(url);
                } else if (item instanceof String s && !s.isBlank()) {
                    urls.add(s);
                }
            }
        }

        // 如果数组方式没找到，尝试单个 URL
        if (urls.isEmpty()) {
            String single = extractFileUrl(response);
            if (single != null) urls.add(single);
        }

        return urls;
    }

    /**
     * 提取任务 ID。
     * 搜索：task_id → taskId → data.task_id → id → request_id
     */
    public String extractTaskId(Object response) {
        Object val = firstNonNullPath(response,
                "$.task_id", "$.taskId", "$.data.task_id", "$.id", "$.request_id");
        return val != null ? val.toString() : null;
    }

    /**
     * 提取运行 ID。
     */
    public String extractRunId(Object response) {
        Object val = firstNonNullPath(response,
                "$.run_id", "$.runId", "$.data.run_id");
        return val != null ? val.toString() : null;
    }

    /**
     * 提取缩略图 URL。
     */
    public String extractThumbnailUrl(Object response) {
        // 从 data 子对象搜索
        for (String prefix : DATA_PREFIXES) {
            for (String field : THUMBNAIL_FIELDS) {
                Object val = json.path(response, prefix + field);
                if (val != null && !val.toString().isBlank()) {
                    return val.toString();
                }
            }
        }
        return null;
    }

    /**
     * 提取宽高尺寸，返回 [width, height]。如果无法提取返回 null。
     */
    public int[] extractDimensions(Object response) {
        Integer width = extractDimensionField(response, "width");
        Integer height = extractDimensionField(response, "height");

        // 尝试从 size 字段解析 "1024x1024" 格式
        if (width == null || height == null) {
            Object size = firstNonNullPath(response, "$.data.size", "$.size");
            if (size != null) {
                String[] parts = size.toString().split("x");
                if (parts.length == 2) {
                    try {
                        width = width != null ? width : Integer.parseInt(parts[0].trim());
                        height = height != null ? height : Integer.parseInt(parts[1].trim());
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        if (width != null && height != null) {
            return new int[]{width, height};
        }
        return null;
    }

    /**
     * 提取文本内容。
     */
    public String extractTextContent(Object response) {
        Object val = firstNonNullPath(response,
                "$.data.text", "$.output.text", "$.result.text",
                "$.choices[0].text", "$.choices[0].message.content", "$.content");
        return val != null ? val.toString() : null;
    }

    /**
     * 提取错误信息（用于 FAILED 状态）。
     */
    public String extractErrorMessage(Object response) {
        // 注意：不搜索 $.error，因为它可能是完整的 error Map 对象而非字符串
        Object val = firstNonNullPath(response,
                "$.message", "$.error_message",
                "$.data.error", "$.reason", "$.error.message");
        return val != null ? val.toString() : null;
    }

    /**
     * 提取错误代码。
     */
    public String extractErrorCode(Object response) {
        Object val = firstNonNullPath(response, "$.error_code", "$.code");
        return val != null ? val.toString() : "EXECUTION_FAILED";
    }

    // ==================== 媒体类型推断 ====================

    /**
     * 从 URL 扩展名推断媒体类型和 MIME 类型。
     *
     * @param url 文件 URL
     * @return [mediaType, mimeType]，如 ["IMAGE", "image/jpeg"]
     */
    public String[] inferMediaType(String url) {
        if (url == null) return new String[]{"IMAGE", "image/jpeg"};

        String lower = url.toLowerCase();
        if (lower.matches(".*\\.(mp4|avi|mov|mkv|webm|flv).*")) {
            return new String[]{"VIDEO", "video/mp4"};
        }
        if (lower.matches(".*\\.(mp3|wav|aac|flac|ogg|m4a).*")) {
            return new String[]{"AUDIO", "audio/mpeg"};
        }
        return new String[]{"IMAGE", "image/jpeg"};
    }

    // ==================== OSS 上传 ====================

    /**
     * 将 URL 上传到自有 OSS。失败时保留原 URL。
     *
     * @param url       原始文件 URL
     * @param mediaType 媒体类型（IMAGE/VIDEO/AUDIO）
     * @return {url, fileKey} map
     */
    public Map<String, Object> uploadToOss(String url, String mediaType) {
        Map<String, Object> result = new HashMap<>();
        result.put("url", url);
        result.put("fileKey", null);

        if (oss == null || url == null || url.isBlank()) {
            return result;
        }

        try {
            String extension = guessExtension(url, mediaType);
            String ossPath = oss.generatePath("ai-outputs", extension);
            Map<String, String> uploadResult = oss.uploadFromUrlWithKey(url, ossPath, null);
            if (uploadResult != null) {
                result.put("url", uploadResult.get("url"));
                result.put("fileKey", uploadResult.get("fileKey"));
            }
        } catch (Exception e) {
            log.debug("OSS upload failed, keeping original URL: {}", e.getMessage());
        }

        return result;
    }

    // ==================== 标准响应一键构建 ====================

    /**
     * 一键构建媒体类型的 StandardResponse。
     * 自动提取 URL、推断媒体类型、提取缩略图/尺寸、处理批量输出。
     * 如果启用了 OSS 且可用，自动上传。
     *
     * @param response API 响应
     * @return StandardResponse map，如果无法提取媒体返回 null
     */
    public Map<String, Object> buildMediaResponse(Object response) {
        return buildMediaResponse(response, true);
    }

    /**
     * 构建媒体响应，可选是否上传到 OSS。
     */
    public Map<String, Object> buildMediaResponse(Object response, boolean uploadToOss) {
        List<String> allUrls = extractAllFileUrls(response);
        if (allUrls.isEmpty()) {
            return null;
        }

        String thumbnailUrl = extractThumbnailUrl(response);
        int[] dimensions = extractDimensions(response);
        String taskId = extractTaskId(response);
        String runId = extractRunId(response);

        // 处理每个文件
        List<Map<String, Object>> items = new ArrayList<>();
        String detectedMediaType = null;

        for (String url : allUrls) {
            String[] typeInfo = inferMediaType(url);
            if (detectedMediaType == null) detectedMediaType = typeInfo[0];

            Map<String, Object> item = new LinkedHashMap<>();

            // OSS 上传
            if (uploadToOss && oss != null) {
                Map<String, Object> ossResult = uploadToOss(url, typeInfo[0]);
                item.put("fileUrl", ossResult.get("url"));
                item.put("fileKey", ossResult.get("fileKey"));
            } else {
                item.put("fileUrl", url);
            }

            item.put("mimeType", typeInfo[1]);
            item.put("thumbnailUrl", thumbnailUrl);

            // 只在第一个（或单个）项上设置尺寸
            if (items.isEmpty() && dimensions != null) {
                item.put("width", dimensions[0]);
                item.put("height", dimensions[1]);
            }

            items.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("outputType", items.size() > 1 ? "MEDIA_BATCH" : "MEDIA_SINGLE");
        result.put("status", "SUCCEEDED");
        Map<String, Object> media = new LinkedHashMap<>();
        media.put("mediaType", detectedMediaType != null ? detectedMediaType : "IMAGE");
        media.put("items", items);
        result.put("media", media);
        result.put("metadata", buildMetadata(taskId, runId, response));

        return result;
    }

    /**
     * 构建文本类型的 StandardResponse。
     *
     * @param response API 响应
     * @return StandardResponse map，如果无法提取文本返回 null
     */
    public Map<String, Object> buildTextResponse(Object response) {
        String text = extractTextContent(response);
        if (text == null || text.isBlank()) return null;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("outputType", "TEXT_CONTENT");
        result.put("status", "SUCCEEDED");
        result.put("textContent", text);
        result.put("metadata", buildMetadata(extractTaskId(response), extractRunId(response), response));

        return result;
    }

    // ==================== 内部工具方法 ====================

    private Map<String, Object> buildErrorResponse(String code, String message, boolean retryable) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("outputType", "MEDIA_SINGLE");
        result.put("status", "FAILED");
        // 使用可变 Map：Groovy 脚本可能需要修改返回的 error 结构
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        error.put("retryable", retryable);
        result.put("error", error);
        return result;
    }

    private Map<String, Object> buildMetadata(String taskId, String runId, Object raw) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (taskId != null) metadata.put("externalTaskId", taskId);
        if (runId != null) metadata.put("externalRunId", runId);
        metadata.put("raw", raw);
        return metadata;
    }

    private String extractStatusRaw(Object response, String statusPath) {
        if (response == null) return null;

        // 自定义路径优先
        if (statusPath != null && !statusPath.isBlank()) {
            Object val = json.path(response, statusPath);
            if (val != null) return val.toString();
        }

        // 默认探测
        Object val = firstNonNullPath(response, "$.status", "$.state", "$.code");

        // 如果 json.path 返回 null，尝试直接从 Map 获取
        if (val == null && response instanceof Map<?, ?> map) {
            val = map.get("status");
            if (val == null) val = map.get("state");
            if (val == null) val = map.get("task_status");
        }

        return val != null ? val.toString() : null;
    }

    @SuppressWarnings("unchecked")
    private String extractUrlFromObject(Object data) {
        if (data instanceof Map<?, ?> map) {
            return extractUrlFromMap((Map<String, Object>) map);
        }
        if (data instanceof List<?> list && !list.isEmpty()) {
            Object first = list.getFirst();
            if (first instanceof Map<?, ?> map) {
                return extractUrlFromMap((Map<String, Object>) map);
            }
            if (first instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return null;
    }

    private String extractUrlFromMap(Map<String, Object> map) {
        for (String field : URL_FIELDS) {
            Object val = map.get(field);
            if (val != null && !val.toString().isBlank()) {
                return val.toString();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String extractFirstUrlFromArray(Object response) {
        Object images = firstNonNullPath(response, "$.images", "$.data");
        if (images instanceof List<?> list && !list.isEmpty()) {
            Object first = list.getFirst();
            if (first instanceof Map<?, ?> map) {
                String url = extractUrlFromMap((Map<String, Object>) map);
                return url;
            }
            if (first instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return null;
    }

    private Integer extractDimensionField(Object response, String field) {
        for (String prefix : List.of("$.data.", "$.output.", "$.")) {
            Object val = json.path(response, prefix + field);
            if (val instanceof Number n) return n.intValue();
        }
        return null;
    }

    private Object firstNonNullPath(Object response, String... paths) {
        for (String path : paths) {
            Object val = json.path(response, path);
            if (val != null) return val;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String firstNonNull(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object val = map.get(key);
            if (val != null && !val.toString().isBlank()) return val.toString();
        }
        return null;
    }

    private boolean isRetryableError(String code) {
        if (code == null) return false;
        return code.contains("rate_limit") || code.contains("timeout") || code.contains("server_error");
    }

    private String guessExtension(String url, String mediaType) {
        if (url != null) {
            String lower = url.toLowerCase();
            if (lower.contains(".png")) return "png";
            if (lower.contains(".webp")) return "webp";
            if (lower.contains(".mp4")) return "mp4";
            if (lower.contains(".mp3")) return "mp3";
            if (lower.contains(".wav")) return "wav";
        }
        if ("VIDEO".equalsIgnoreCase(mediaType)) return "mp4";
        if ("AUDIO".equalsIgnoreCase(mediaType)) return "mp3";
        return "jpg";
    }
}
