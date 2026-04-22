package com.actionow.ai.dto.standard;

import com.actionow.ai.plugin.model.PluginExecutionResult.ExecutionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 标准响应 DTO
 * AI 响应映射的统一输出格式
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StandardResponse {

    /**
     * 输出类型
     * 必填
     */
    private OutputType outputType;

    /**
     * 执行状态
     * 必填
     */
    private ExecutionStatus status;

    /**
     * 错误信息（失败时）
     */
    private ErrorInfo error;

    /**
     * 媒体输出（素材生成时）
     */
    private MediaOutput media;

    /**
     * 实体输出列表（文本解析时）
     */
    @Builder.Default
    private List<EntityOutput> entities = new ArrayList<>();

    /**
     * 文本内容（纯文本输出时）
     */
    private String textContent;

    /**
     * 元数据
     */
    @Builder.Default
    private ResponseMetadata metadata = new ResponseMetadata();

    // ==================== 工厂方法：成功响应 ====================

    /**
     * 创建单个媒体成功响应
     */
    public static StandardResponse mediaSingle(MediaOutput media, ResponseMetadata metadata) {
        return StandardResponse.builder()
                .outputType(OutputType.MEDIA_SINGLE)
                .status(ExecutionStatus.SUCCEEDED)
                .media(media)
                .metadata(metadata != null ? metadata : new ResponseMetadata())
                .build();
    }

    /**
     * 创建批量媒体成功响应
     */
    public static StandardResponse mediaBatch(MediaOutput media, ResponseMetadata metadata) {
        return StandardResponse.builder()
                .outputType(OutputType.MEDIA_BATCH)
                .status(ExecutionStatus.SUCCEEDED)
                .media(media)
                .metadata(metadata != null ? metadata : new ResponseMetadata())
                .build();
    }

    /**
     * 创建实体成功响应
     */
    public static StandardResponse entities(OutputType outputType, List<EntityOutput> entities, ResponseMetadata metadata) {
        return StandardResponse.builder()
                .outputType(outputType)
                .status(ExecutionStatus.SUCCEEDED)
                .entities(entities != null ? entities : new ArrayList<>())
                .metadata(metadata != null ? metadata : new ResponseMetadata())
                .build();
    }

    /**
     * 创建文本内容响应
     */
    public static StandardResponse text(String content, ResponseMetadata metadata) {
        return StandardResponse.builder()
                .outputType(OutputType.TEXT_CONTENT)
                .status(ExecutionStatus.SUCCEEDED)
                .textContent(content)
                .metadata(metadata != null ? metadata : new ResponseMetadata())
                .build();
    }

    // ==================== 工厂方法：异步响应 ====================

    /**
     * 创建待处理响应（用于异步模式）
     */
    public static StandardResponse pending(OutputType outputType, String externalTaskId) {
        return StandardResponse.builder()
                .outputType(outputType)
                .status(ExecutionStatus.PENDING)
                .metadata(ResponseMetadata.builder()
                        .externalTaskId(externalTaskId)
                        .build())
                .build();
    }

    /**
     * 创建运行中响应
     */
    public static StandardResponse running(OutputType outputType, String externalTaskId) {
        return StandardResponse.builder()
                .outputType(outputType)
                .status(ExecutionStatus.RUNNING)
                .metadata(ResponseMetadata.builder()
                        .externalTaskId(externalTaskId)
                        .build())
                .build();
    }

    // ==================== 工厂方法：失败响应 ====================

    /**
     * 创建失败响应
     */
    public static StandardResponse failure(OutputType outputType, ErrorInfo error, ResponseMetadata metadata) {
        return StandardResponse.builder()
                .outputType(outputType)
                .status(ExecutionStatus.FAILED)
                .error(error)
                .metadata(metadata != null ? metadata : new ResponseMetadata())
                .build();
    }

    /**
     * 创建失败响应（简化版）
     */
    public static StandardResponse failure(OutputType outputType, String errorCode, String errorMessage) {
        return failure(outputType, ErrorInfo.builder()
                .code(errorCode)
                .message(errorMessage)
                .build(), null);
    }

    // ==================== 工具方法 ====================

    /**
     * 是否成功
     */
    public boolean isSuccess() {
        return status == ExecutionStatus.SUCCEEDED;
    }

    /**
     * 是否失败
     */
    public boolean isFailed() {
        return status == ExecutionStatus.FAILED;
    }

    /**
     * 是否为终态
     */
    public boolean isTerminal() {
        return status != null && status.isTerminal();
    }

    /**
     * 是否为媒体输出
     */
    public boolean hasMedia() {
        return media != null && media.getItems() != null && !media.getItems().isEmpty();
    }

    /**
     * 是否为实体输出
     */
    public boolean hasEntities() {
        return entities != null && !entities.isEmpty();
    }

    /**
     * 获取第一个媒体项
     */
    public MediaItem getFirstMediaItem() {
        return hasMedia() ? media.getFirstItem() : null;
    }

    /**
     * 获取外部任务 ID
     */
    public String getExternalTaskId() {
        return metadata != null ? metadata.getExternalTaskId() : null;
    }

    // ==================== 从 Map 构建 ====================

    /**
     * 从 Groovy 脚本返回的 Map 构建 StandardResponse
     * 这是与 Groovy 脚本交互的核心方法
     */
    @SuppressWarnings("unchecked")
    public static StandardResponse fromMap(Map<String, Object> map) {
        if (map == null) {
            return failure(null, ErrorInfo.CODE_INTERNAL_ERROR, "响应为空");
        }

        StandardResponse.StandardResponseBuilder builder = StandardResponse.builder();

        // 解析 outputType
        Object outputTypeObj = map.get("outputType");
        if (outputTypeObj != null) {
            builder.outputType(OutputType.fromCode(outputTypeObj.toString()));
        }

        // 解析 status
        Object statusObj = map.get("status");
        if (statusObj != null) {
            builder.status(parseStatus(statusObj.toString()));
        } else {
            // 默认成功
            builder.status(ExecutionStatus.SUCCEEDED);
        }

        // 解析 error
        Object errorObj = map.get("error");
        if (errorObj instanceof Map) {
            Map<String, Object> errorMap = (Map<String, Object>) errorObj;
            builder.error(ErrorInfo.builder()
                    .code(getStringOrNull(errorMap, "code"))
                    .message(getStringOrNull(errorMap, "message"))
                    .retryable(getBooleanOrDefault(errorMap, "retryable", false))
                    .detail(getStringOrNull(errorMap, "detail"))
                    .build());
        }

        // 解析 media
        Object mediaObj = map.get("media");
        if (mediaObj instanceof Map) {
            builder.media(parseMediaOutput((Map<String, Object>) mediaObj));
        }

        // 解析 entities
        Object entitiesObj = map.get("entities");
        if (entitiesObj instanceof List) {
            List<EntityOutput> entities = new ArrayList<>();
            for (Object entityObj : (List<?>) entitiesObj) {
                if (entityObj instanceof Map) {
                    entities.add(parseEntityOutput((Map<String, Object>) entityObj));
                }
            }
            builder.entities(entities);
        }

        // 解析 textContent：支持 textContent 和 text.content 两种格式
        Object textContentObj = map.get("textContent");
        if (textContentObj != null) {
            builder.textContent(textContentObj.toString());
        } else {
            // Groovy 脚本常用格式: text: [content: "..."]
            Object textObj = map.get("text");
            if (textObj instanceof Map) {
                Object contentObj = ((Map<?, ?>) textObj).get("content");
                if (contentObj != null) {
                    builder.textContent(contentObj.toString());
                }
            } else if (textObj instanceof String) {
                builder.textContent((String) textObj);
            }
        }

        // 解析 metadata
        Object metadataObj = map.get("metadata");
        if (metadataObj instanceof Map) {
            builder.metadata(parseMetadata((Map<String, Object>) metadataObj));
        } else {
            builder.metadata(new ResponseMetadata());
        }

        return builder.build();
    }

    // ==================== 私有辅助方法 ====================

    private static ExecutionStatus parseStatus(String status) {
        if (status == null) {
            return ExecutionStatus.SUCCEEDED;
        }
        return switch (status.toUpperCase()) {
            case "SUCCESS", "SUCCEEDED", "COMPLETED", "DONE" -> ExecutionStatus.SUCCEEDED;
            case "FAILED", "ERROR", "FAILURE" -> ExecutionStatus.FAILED;
            case "PENDING", "SUBMITTED", "QUEUED" -> ExecutionStatus.PENDING;
            case "RUNNING", "PROCESSING", "IN_PROGRESS" -> ExecutionStatus.RUNNING;
            case "CANCELLED", "CANCELED" -> ExecutionStatus.CANCELLED;
            case "TIMEOUT" -> ExecutionStatus.TIMEOUT;
            default -> ExecutionStatus.SUCCEEDED;
        };
    }

    @SuppressWarnings("unchecked")
    private static MediaOutput parseMediaOutput(Map<String, Object> map) {
        MediaOutput.MediaOutputBuilder builder = MediaOutput.builder();

        Object mediaTypeObj = map.get("mediaType");
        if (mediaTypeObj != null) {
            builder.mediaType(MediaType.fromCode(mediaTypeObj.toString()));
        }

        Object itemsObj = map.get("items");
        if (itemsObj instanceof List) {
            List<MediaItem> items = new ArrayList<>();
            for (Object itemObj : (List<?>) itemsObj) {
                if (itemObj instanceof Map) {
                    items.add(parseMediaItem((Map<String, Object>) itemObj));
                }
            }
            builder.items(items);
        }

        return builder.build();
    }

    private static MediaItem parseMediaItem(Map<String, Object> map) {
        return MediaItem.builder()
                .fileUrl(getStringOrNull(map, "fileUrl"))
                .fileKey(getStringOrNull(map, "fileKey"))
                .mimeType(getStringOrNull(map, "mimeType"))
                .fileSize(getLongOrNull(map, "fileSize"))
                .thumbnailUrl(getStringOrNull(map, "thumbnailUrl"))
                .width(getIntegerOrNull(map, "width"))
                .height(getIntegerOrNull(map, "height"))
                .duration(getIntegerOrNull(map, "duration"))
                .format(getStringOrNull(map, "format"))
                .bitrate(getIntegerOrNull(map, "bitrate"))
                .codec(getStringOrNull(map, "codec"))
                .frameRate(getIntegerOrNull(map, "frameRate"))
                .sampleRate(getIntegerOrNull(map, "sampleRate"))
                .channels(getIntegerOrNull(map, "channels"))
                .base64(getBooleanOrDefault(map, "base64", false))
                .modelId(getStringOrNull(map, "modelId"))
                .modelVersion(getStringOrNull(map, "modelVersion"))
                .seed(getStringOrNull(map, "seed"))
                .params(getMapOrNull(map, "params"))
                .build();
    }

    private static EntityOutput parseEntityOutput(Map<String, Object> map) {
        EntityOutput.EntityOutputBuilder builder = EntityOutput.builder();

        Object entityTypeObj = map.get("entityType");
        if (entityTypeObj != null) {
            builder.entityType(EntityType.fromCode(entityTypeObj.toString()));
        }

        // data 保持原始 Map，后续根据 entityType 转换
        builder.data(map.get("data"));

        return builder.build();
    }

    private static ResponseMetadata parseMetadata(Map<String, Object> map) {
        // 已知字段
        ResponseMetadata.ResponseMetadataBuilder builder = ResponseMetadata.builder()
                .externalTaskId(getStringOrNull(map, "externalTaskId"))
                .externalRunId(getStringOrNull(map, "externalRunId"))
                .elapsedMs(getLongOrNull(map, "elapsedMs"))
                .totalTokens(getIntegerOrNull(map, "totalTokens"))
                .inputTokens(getIntegerOrNull(map, "inputTokens"))
                .outputTokens(getIntegerOrNull(map, "outputTokens"))
                .modelUsed(getStringOrNull(map, "modelUsed"))
                .modelVersion(getStringOrNull(map, "modelVersion"))
                .raw(getMapOrNull(map, "raw"));

        // 将未识别的字段收集到 extra 中（支持 Groovy 脚本返回的自定义元数据）
        java.util.Set<String> knownKeys = java.util.Set.of(
                "externalTaskId", "externalRunId", "elapsedMs",
                "totalTokens", "inputTokens", "outputTokens",
                "modelUsed", "modelVersion", "raw", "extra"
        );
        Map<String, Object> extra = getMapOrNull(map, "extra");
        Map<String, Object> extraFields = new java.util.HashMap<>();
        if (extra != null) {
            extraFields.putAll(extra);
        }
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!knownKeys.contains(entry.getKey()) && entry.getValue() != null) {
                extraFields.put(entry.getKey(), entry.getValue());
            }
        }
        if (!extraFields.isEmpty()) {
            builder.extra(extraFields);
        }

        return builder.build();
    }

    private static String getStringOrNull(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private static Integer getIntegerOrNull(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long getLongOrNull(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Boolean getBooleanOrDefault(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(value.toString());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMapOrNull(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }
}
