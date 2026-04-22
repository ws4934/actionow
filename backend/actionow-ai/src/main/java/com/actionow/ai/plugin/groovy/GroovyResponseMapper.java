package com.actionow.ai.plugin.groovy;

import com.actionow.ai.dto.standard.*;
import com.actionow.ai.plugin.groovy.binding.BindingFactory;
import com.actionow.ai.plugin.groovy.binding.ResponseHelper;
import com.actionow.ai.plugin.model.PluginConfig;
import com.actionow.ai.plugin.model.PluginExecutionRequest;
import com.actionow.ai.plugin.model.PluginExecutionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Groovy脚本响应映射器
 * 负责使用Groovy脚本映射API响应并构建执行结果
 * 支持新的 StandardResponse 格式，同时保持向后兼容
 *
 * @author Actionow
 */
@Slf4j
@RequiredArgsConstructor
public class GroovyResponseMapper {

    private final GroovyScriptEngine scriptEngine;
    private final BindingFactory bindingFactory;

    /**
     * 使用Groovy脚本映射响应
     *
     * @param config      插件配置
     * @param rawResponse 原始响应
     * @param request     执行请求
     * @return 映射后的响应
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> mapResponse(PluginConfig config,
                                           Map<String, Object> rawResponse,
                                           PluginExecutionRequest request) {
        String script = getResponseMapperScript(config);
        if (!StringUtils.hasText(script)) {
            // 无脚本，直接返回原始响应
            return rawResponse;
        }

        // 创建脚本执行上下文
        GroovyScriptContext context = GroovyScriptContext.forResponseMapper(
            request.getInputs(),
            config.toMap(),
            rawResponse
        );

        context.getExtras().put("executionId", request.getExecutionId());
        context.getExtras().put("responseMode", request.getResponseMode().name());

        // 注入扩展绑定（包括 oss）
        injectBindings(context, request);

        // 注入 ResponseHelper
        context.setResp(new ResponseHelper(context.getJson(), context.getOss()));

        Map<String, Object> mappedResponse = executeResponseMapper(config, rawResponse, context);

        return mappedResponse;
    }

    /**
     * 映射轮询响应
     * 注意：轮询场景优先使用 customLogicScript，因为它通常包含针对轮询响应的映射逻辑
     *
     * @param config         插件配置
     * @param statusResponse 状态响应
     * @param externalTaskId 外部任务ID
     * @param externalRunId  外部运行ID
     * @param request        执行请求（可为null）
     * @return 映射后的响应
     */
    public Map<String, Object> mapPollResponse(PluginConfig config,
                                               Map<String, Object> statusResponse,
                                               String externalTaskId,
                                               String externalRunId,
                                               PluginExecutionRequest request) {
        GroovyScriptContext context = GroovyScriptContext.forResponseMapper(
            null,
            config.toMap(),
            statusResponse
        );
        context.getExtras().put("pollMode", true);
        context.getExtras().put("externalTaskId", externalTaskId);
        context.getExtras().put("externalRunId", externalRunId);

        // 注入扩展绑定（包括 oss）
        if (request != null) {
            injectBindings(context, request);
        } else {
            // 轮询场景没有 request，使用默认上下文注入
            injectBindingsWithDefaults(context, config.getProviderId());
        }

        // 注入 ResponseHelper
        context.setResp(new ResponseHelper(context.getJson(), context.getOss()));

        // 轮询场景优先使用 customLogicScript，如果没有则回退到 responseMapperScript
        return executePollingResponseMapper(config, statusResponse, context);
    }

    /**
     * 映射轮询响应（向后兼容方法）
     */
    public Map<String, Object> mapPollResponse(PluginConfig config,
                                               Map<String, Object> statusResponse,
                                               String externalTaskId,
                                               String externalRunId) {
        return mapPollResponse(config, statusResponse, externalTaskId, externalRunId, null);
    }

    /**
     * 注入扩展绑定到上下文
     */
    private void injectBindings(GroovyScriptContext context, PluginExecutionRequest request) {
        try {
            BindingFactory.BindingContext bindingContext = BindingFactory.BindingContext.builder()
                    .workspaceId(request.getWorkspaceId())
                    .userId(request.getUserId())
                    .executionId(request.getExecutionId())
                    .providerId(request.getProviderId())
                    .requiresOss(true)
                    .build();

            BindingFactory.BindingHolder holder = bindingFactory.createBindings(bindingContext);
            context.withBindings(holder);
            log.debug("[GroovyResponseMapper] 成功注入扩展绑定: executionId={}, hasOss={}",
                    request.getExecutionId(), holder.getOss() != null);
        } catch (Exception e) {
            log.warn("[GroovyResponseMapper] 注入扩展绑定失败，脚本将无法使用 oss 等扩展功能: {}", e.getMessage());
        }
    }

    /**
     * 使用默认值注入扩展绑定
     */
    private void injectBindingsWithDefaults(GroovyScriptContext context, String providerId) {
        try {
            BindingFactory.BindingContext bindingContext = BindingFactory.BindingContext.builder()
                    .workspaceId("system")
                    .userId("system")
                    .executionId("poll-" + System.currentTimeMillis())
                    .providerId(providerId)
                    .requiresOss(true)
                    .build();

            BindingFactory.BindingHolder holder = bindingFactory.createBindings(bindingContext);
            context.withBindings(holder);
        } catch (Exception e) {
            log.warn("[GroovyResponseMapper] 注入默认扩展绑定失败: {}", e.getMessage());
        }
    }

    /**
     * 执行响应映射脚本
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> executeResponseMapper(PluginConfig config,
                                                      Map<String, Object> rawResponse,
                                                      GroovyScriptContext context) {
        String script = getResponseMapperScript(config);
        if (!StringUtils.hasText(script)) {
            return rawResponse;
        }

        Object result = scriptEngine.executeResponseMapper(script, rawResponse, context);

        if (result instanceof Map) {
            return (Map<String, Object>) result;
        }

        // 如果返回非Map，包装成标准格式
        Map<String, Object> wrappedResult = new HashMap<>();
        wrappedResult.put("result", result);
        return wrappedResult;
    }

    /**
     * 执行轮询响应映射脚本
     * 优先使用 customLogicScript（专门用于轮询场景的脚本），如果没有则回退到 responseMapperScript
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> executePollingResponseMapper(PluginConfig config,
                                                              Map<String, Object> rawResponse,
                                                              GroovyScriptContext context) {
        String script = getPollingMapperScript(config);
        if (!StringUtils.hasText(script)) {
            return rawResponse;
        }

        Object result = scriptEngine.executeResponseMapper(script, rawResponse, context);

        if (result instanceof Map) {
            return (Map<String, Object>) result;
        }

        // 如果返回非Map，包装成标准格式
        Map<String, Object> wrappedResult = new HashMap<>();
        wrappedResult.put("result", result);
        return wrappedResult;
    }

    // ==================== 新标准响应支持 ====================

    /**
     * 构建标准响应
     * 从 Groovy 脚本返回的 Map 构建 StandardResponse
     *
     * @param mappedResponse 映射后的响应
     * @param config         插件配置
     * @return 标准响应
     */
    public StandardResponse buildStandardResponse(Map<String, Object> mappedResponse, PluginConfig config) {
        // 检查是否为新格式（包含 outputType 字段）
        if (isStandardFormat(mappedResponse)) {
            StandardResponse response = StandardResponse.fromMap(mappedResponse);

            // 校验响应格式
            List<String> errors = StandardResponseValidator.validate(response);
            if (!errors.isEmpty()) {
                log.warn("响应格式校验失败: {}", errors);
                // 不抛异常，仅记录警告，保持向后兼容
            }

            return response;
        }

        // 旧格式兼容：转换为标准格式
        return convertLegacyToStandard(mappedResponse, config);
    }

    /**
     * 检查是否为标准格式
     */
    private boolean isStandardFormat(Map<String, Object> response) {
        Object outputType = response.get("outputType");
        return outputType != null && StringUtils.hasText(outputType.toString())
                && response.containsKey("status");
    }

    /**
     * 将旧格式响应转换为标准格式
     */
    private StandardResponse convertLegacyToStandard(Map<String, Object> mappedResponse, PluginConfig config) {
        // 解析状态
        String statusStr = extractString(mappedResponse, "status", "success");
        PluginExecutionResult.ExecutionStatus status = parseStatus(statusStr);

        // 解析错误信息
        if (status == PluginExecutionResult.ExecutionStatus.FAILED || mappedResponse.containsKey("error")) {
            return StandardResponse.failure(
                OutputType.MEDIA_SINGLE,  // 默认类型
                ErrorInfo.builder()
                    .code(extractString(mappedResponse, "errorCode", ErrorInfo.CODE_PROVIDER_ERROR))
                    .message(extractString(mappedResponse, "error",
                             extractString(mappedResponse, "errorMessage", "未知错误")))
                    .build(),
                buildMetadataFromLegacy(mappedResponse)
            );
        }

        // 尝试提取媒体信息
        MediaItem mediaItem = extractMediaItemFromLegacy(mappedResponse);
        if (mediaItem != null && StringUtils.hasText(mediaItem.getFileUrl())) {
            // 推断媒体类型
            MediaType mediaType = inferMediaType(mediaItem, config);

            return StandardResponse.mediaSingle(
                MediaOutput.builder()
                    .mediaType(mediaType)
                    .items(List.of(mediaItem))
                    .build(),
                buildMetadataFromLegacy(mappedResponse)
            );
        }

        // 无法识别为媒体，作为通用输出处理
        return StandardResponse.builder()
            .outputType(OutputType.MEDIA_SINGLE)
            .status(status)
            .metadata(buildMetadataFromLegacy(mappedResponse))
            .build();
    }

    /**
     * 从旧格式中提取媒体项
     */
    private MediaItem extractMediaItemFromLegacy(Map<String, Object> response) {
        MediaItem.MediaItemBuilder builder = MediaItem.builder();

        // 尝试多种字段名
        String fileUrl = extractString(response, "fileUrl", null);
        if (fileUrl == null) fileUrl = extractString(response, "file_url", null);
        if (fileUrl == null) fileUrl = extractString(response, "url", null);
        if (fileUrl == null) fileUrl = extractString(response, "imageUrl", null);
        if (fileUrl == null) fileUrl = extractString(response, "image_url", null);
        if (fileUrl == null) fileUrl = extractString(response, "videoUrl", null);
        if (fileUrl == null) fileUrl = extractString(response, "video_url", null);
        if (fileUrl == null) fileUrl = extractString(response, "audioUrl", null);
        if (fileUrl == null) fileUrl = extractString(response, "audio_url", null);

        if (fileUrl == null) {
            return null;
        }
        builder.fileUrl(fileUrl);

        // OSS 文件路径（fileKey）
        String fileKey = extractString(response, "fileKey", null);
        if (fileKey == null) fileKey = extractString(response, "file_key", null);
        if (fileKey == null) fileKey = extractString(response, "objectKey", null);
        if (fileKey == null) fileKey = extractString(response, "object_key", null);
        builder.fileKey(fileKey);

        // MIME 类型
        String mimeType = extractString(response, "mimeType", null);
        if (mimeType == null) mimeType = extractString(response, "mime_type", null);
        if (mimeType == null) mimeType = extractString(response, "contentType", null);
        if (mimeType == null) mimeType = extractString(response, "content_type", null);
        builder.mimeType(mimeType);

        // 文件大小
        builder.fileSize(extractLong(response, "fileSize", extractLong(response, "file_size", null)));

        // 缩略图
        String thumbnailUrl = extractString(response, "thumbnailUrl", null);
        if (thumbnailUrl == null) thumbnailUrl = extractString(response, "thumbnail_url", null);
        if (thumbnailUrl == null) thumbnailUrl = extractString(response, "thumbnail", null);
        builder.thumbnailUrl(thumbnailUrl);

        // 尺寸
        builder.width(extractInteger(response, "width", null));
        builder.height(extractInteger(response, "height", null));
        builder.duration(extractInteger(response, "duration", null));

        // 生成信息
        builder.seed(extractString(response, "seed", null));
        builder.modelId(extractString(response, "modelId", extractString(response, "model_id", null)));

        // metaInfo
        @SuppressWarnings("unchecked")
        Map<String, Object> metaInfo = (Map<String, Object>) response.get("metaInfo");
        if (metaInfo == null) {
            metaInfo = (Map<String, Object>) response.get("meta_info");
        }
        if (metaInfo != null) {
            if (builder.build().getWidth() == null) {
                builder.width(extractInteger(metaInfo, "width", null));
            }
            if (builder.build().getHeight() == null) {
                builder.height(extractInteger(metaInfo, "height", null));
            }
            builder.format(extractString(metaInfo, "format", null));
            builder.codec(extractString(metaInfo, "codec", null));
            builder.frameRate(extractInteger(metaInfo, "frameRate", extractInteger(metaInfo, "frame_rate", null)));
            builder.bitrate(extractInteger(metaInfo, "bitrate", null));
            builder.sampleRate(extractInteger(metaInfo, "sampleRate", extractInteger(metaInfo, "sample_rate", null)));
            builder.channels(extractInteger(metaInfo, "channels", null));
        }

        return builder.build();
    }

    /**
     * 从旧格式中构建元数据
     */
    private ResponseMetadata buildMetadataFromLegacy(Map<String, Object> response) {
        return ResponseMetadata.builder()
            .externalTaskId(extractString(response, "taskId", extractString(response, "task_id", null)))
            .externalRunId(extractString(response, "runId", extractString(response, "run_id", null)))
            .elapsedMs(extractLong(response, "elapsedMs", extractLong(response, "elapsed_ms", null)))
            .totalTokens(extractInteger(response, "totalTokens", extractInteger(response, "total_tokens", null)))
            .modelUsed(extractString(response, "modelUsed", extractString(response, "model_used", null)))
            .raw(response)
            .build();
    }

    /**
     * 推断媒体类型
     */
    private MediaType inferMediaType(MediaItem item, PluginConfig config) {
        // 1. 先从 MIME 类型推断
        if (StringUtils.hasText(item.getMimeType())) {
            MediaType fromMime = MediaType.fromMimeType(item.getMimeType());
            if (fromMime != null) {
                return fromMime;
            }
        }

        // 2. 从配置的 providerName 推断
        if (config != null && StringUtils.hasText(config.getProviderName())) {
            String providerName = config.getProviderName().toUpperCase();
            if (providerName.contains("IMAGE")) return MediaType.IMAGE;
            if (providerName.contains("VIDEO")) return MediaType.VIDEO;
            if (providerName.contains("AUDIO")) return MediaType.AUDIO;
        }

        // 3. 从 URL 扩展名推断
        String url = item.getFileUrl();
        if (url != null) {
            String lower = url.toLowerCase();
            if (lower.matches(".*\\.(jpg|jpeg|png|gif|webp|bmp|svg).*")) return MediaType.IMAGE;
            if (lower.matches(".*\\.(mp4|avi|mov|mkv|webm|flv).*")) return MediaType.VIDEO;
            if (lower.matches(".*\\.(mp3|wav|aac|flac|ogg|m4a).*")) return MediaType.AUDIO;
        }

        // 默认图片
        return MediaType.IMAGE;
    }

    // ==================== 原有方法（保持向后兼容）====================

    /**
     * 构建执行结果
     *
     * @param mappedResponse 映射后的响应
     * @param config         插件配置
     * @return 执行结果
     */
    public PluginExecutionResult buildExecutionResult(Map<String, Object> mappedResponse, PluginConfig config) {
        // 先尝试构建标准响应
        StandardResponse standardResponse = buildStandardResponse(mappedResponse, config);

        // 转换为 PluginExecutionResult
        return convertToPluginExecutionResult(standardResponse, mappedResponse);
    }

    /**
     * 将 StandardResponse 转换为 PluginExecutionResult
     */
    public PluginExecutionResult convertToPluginExecutionResult(StandardResponse standardResponse,
                                                                  Map<String, Object> rawResponse) {
        PluginExecutionResult.PluginExecutionResultBuilder builder = PluginExecutionResult.builder();

        // 设置标准响应和输出类型
        builder.standardResponse(standardResponse);
        builder.outputType(standardResponse.getOutputType());

        // 设置状态
        builder.status(standardResponse.getStatus());

        // 设置错误信息
        if (standardResponse.getError() != null) {
            builder.errorCode(standardResponse.getError().getCode());
            builder.errorMessage(standardResponse.getError().getMessage());
        }

        // 设置元数据
        ResponseMetadata metadata = standardResponse.getMetadata();
        if (metadata != null) {
            builder.externalTaskId(metadata.getExternalTaskId());
            builder.externalRunId(metadata.getExternalRunId());
            builder.elapsedTimeMs(metadata.getElapsedMs());
            builder.totalTokens(metadata.getTotalTokens());
        }

        // 构建 outputs
        Map<String, Object> outputs = buildOutputsFromStandardResponse(standardResponse);
        builder.outputs(outputs);

        // 原始响应
        builder.rawResponse(rawResponse);
        builder.completedAt(LocalDateTime.now());

        return builder.build();
    }

    /**
     * 从 StandardResponse 构建 outputs Map
     */
    private Map<String, Object> buildOutputsFromStandardResponse(StandardResponse response) {
        Map<String, Object> outputs = new HashMap<>();

        // 媒体输出
        if (response.hasMedia()) {
            MediaItem firstItem = response.getFirstMediaItem();
            if (firstItem != null) {
                outputs.put("file_url", firstItem.getFileUrl());
                outputs.put("fileUrl", firstItem.getFileUrl());
                outputs.put("mime_type", firstItem.getMimeType());
                outputs.put("mimeType", firstItem.getMimeType());
                outputs.put("file_key", firstItem.getFileKey());
                outputs.put("fileKey", firstItem.getFileKey());
                outputs.put("thumbnail_url", firstItem.getThumbnailUrl());
                outputs.put("thumbnailUrl", firstItem.getThumbnailUrl());
                outputs.put("file_size", firstItem.getFileSize());
                outputs.put("fileSize", firstItem.getFileSize());

                Map<String, Object> metaInfo = firstItem.toMetaInfo();
                outputs.put("meta_info", metaInfo);
                outputs.put("metaInfo", metaInfo);

                Map<String, Object> extraInfo = firstItem.toExtraInfo();
                outputs.put("extra_info", extraInfo);
                outputs.put("extraInfo", extraInfo);
            }

            // 批量输出时提供 items 列表
            if (response.getMedia().isBatch()) {
                outputs.put("items", response.getMedia().getItems());
            }
        }

        // 实体输出
        if (response.hasEntities()) {
            outputs.put("entities", response.getEntities());
        }

        // 文本输出
        if (StringUtils.hasText(response.getTextContent())) {
            outputs.put("text", response.getTextContent());
            outputs.put("textContent", response.getTextContent());
        }

        // 添加 outputType 便于后续处理
        if (response.getOutputType() != null) {
            outputs.put("outputType", response.getOutputType().getCode());
        }

        // 将 metadata 中的自定义字段加入 outputs（供任务结果使用）
        ResponseMetadata metadata = response.getMetadata();
        if (metadata != null && metadata.getExtra() != null) {
            outputs.put("metadata", metadata.getExtra());
        }

        return outputs;
    }

    /**
     * 构建异步提交结果
     *
     * @param mappedResponse 映射后的响应
     * @param config         插件配置
     * @return 异步提交结果
     */
    @SuppressWarnings("unchecked")
    public PluginExecutionResult buildAsyncSubmitResult(Map<String, Object> mappedResponse, PluginConfig config) {
        PluginExecutionResult.PluginExecutionResultBuilder builder = PluginExecutionResult.builder();

        String status = extractString(mappedResponse, "status", "pending");
        builder.status(parseStatus(status));

        // 提取 externalTaskId：先从顶层找，再从 metadata 中找
        String externalTaskId = extractString(mappedResponse, "taskId",
                                extractString(mappedResponse, "task_id",
                                extractString(mappedResponse, "externalTaskId", null)));
        if (externalTaskId == null && mappedResponse.get("metadata") instanceof Map) {
            Map<String, Object> metadata = (Map<String, Object>) mappedResponse.get("metadata");
            externalTaskId = extractString(metadata, "externalTaskId",
                            extractString(metadata, "taskId",
                            extractString(metadata, "task_id", null)));
        }
        builder.externalTaskId(externalTaskId);

        // 提取 externalRunId：先从顶层找，再从 metadata 中找
        String externalRunId = extractString(mappedResponse, "runId",
                               extractString(mappedResponse, "run_id",
                               extractString(mappedResponse, "externalRunId", null)));
        if (externalRunId == null && mappedResponse.get("metadata") instanceof Map) {
            Map<String, Object> metadata = (Map<String, Object>) mappedResponse.get("metadata");
            externalRunId = extractString(metadata, "externalRunId",
                           extractString(metadata, "runId",
                           extractString(metadata, "run_id", null)));
        }
        builder.externalRunId(externalRunId);

        builder.rawResponse(mappedResponse);

        return builder.build();
    }

    /**
     * 构建轮询结果
     *
     * @param mappedResponse 映射后的响应
     * @param config         插件配置
     * @return 轮询结果
     */
    public PluginExecutionResult buildPollResult(Map<String, Object> mappedResponse, PluginConfig config) {
        PluginExecutionResult result = buildExecutionResult(mappedResponse, config);

        // 检查是否仍在处理中
        String status = extractString(mappedResponse, "status", "");

        if ("pending".equalsIgnoreCase(status) || "running".equalsIgnoreCase(status) ||
            "processing".equalsIgnoreCase(status)) {
            result.setStatus(PluginExecutionResult.ExecutionStatus.PENDING);
        }

        return result;
    }

    /**
     * 解析状态
     *
     * @param status 状态字符串
     * @return 执行状态枚举
     */
    public PluginExecutionResult.ExecutionStatus parseStatus(String status) {
        if (status == null) {
            return PluginExecutionResult.ExecutionStatus.SUCCEEDED;
        }

        return switch (status.toLowerCase()) {
            case "success", "succeeded", "completed", "done" -> PluginExecutionResult.ExecutionStatus.SUCCEEDED;
            case "failed", "error", "failure" -> PluginExecutionResult.ExecutionStatus.FAILED;
            case "pending", "submitted", "queued" -> PluginExecutionResult.ExecutionStatus.PENDING;
            case "running", "processing", "in_progress" -> PluginExecutionResult.ExecutionStatus.RUNNING;
            case "cancelled", "canceled" -> PluginExecutionResult.ExecutionStatus.CANCELLED;
            default -> PluginExecutionResult.ExecutionStatus.SUCCEEDED;
        };
    }

    /**
     * 提取字符串值
     */
    public String extractString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value != null) {
            return value.toString();
        }
        return defaultValue;
    }

    /**
     * 提取整数值
     */
    public Integer extractInteger(Map<String, Object> map, String key, Integer defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 提取长整数值
     */
    public Long extractLong(Map<String, Object> map, String key, Long defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取响应映射脚本
     *
     * @param config 插件配置
     * @return 脚本内容
     */
    private String getResponseMapperScript(PluginConfig config) {
        if (StringUtils.hasText(config.getResponseMapperScript())) {
            return config.getResponseMapperScript();
        }
        return null;
    }

    /**
     * 获取轮询响应映射脚本
     * 优先使用 customLogicScript（专门用于轮询场景），如果没有则回退到 responseMapperScript
     *
     * @param config 插件配置
     * @return 脚本内容
     */
    private String getPollingMapperScript(PluginConfig config) {
        // 优先使用 customLogicScript，因为它通常是专门为轮询/回调场景编写的
        if (StringUtils.hasText(config.getCustomLogicScript())) {
            return config.getCustomLogicScript();
        }
        // 如果没有 customLogicScript，回退到 responseMapperScript
        if (StringUtils.hasText(config.getResponseMapperScript())) {
            return config.getResponseMapperScript();
        }
        return null;
    }
}
