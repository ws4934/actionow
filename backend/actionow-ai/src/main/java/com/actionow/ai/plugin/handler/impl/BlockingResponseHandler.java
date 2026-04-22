package com.actionow.ai.plugin.handler.impl;

import com.actionow.ai.plugin.handler.ResponseHandler;
import com.actionow.ai.plugin.model.PluginConfig;
import com.actionow.ai.plugin.model.PluginExecutionResult;
import com.actionow.ai.plugin.model.ResponseMode;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 阻塞响应处理器
 * 处理同步阻塞请求的响应
 *
 * @author Actionow
 */
@Slf4j
public class BlockingResponseHandler implements ResponseHandler {

    private static final Configuration JSON_PATH_CONFIG = Configuration.builder()
        .options(Option.DEFAULT_PATH_LEAF_TO_NULL, Option.SUPPRESS_EXCEPTIONS)
        .build();

    @Override
    public ResponseMode getResponseMode() {
        return ResponseMode.BLOCKING;
    }

    @Override
    public PluginExecutionResult handleResponse(Object rawResponse, PluginConfig config) {
        if (rawResponse == null) {
            return PluginExecutionResult.failure(null, "EMPTY_RESPONSE", "响应为空");
        }

        try {
            // 处理响应，子类通过Groovy脚本实现自定义映射
            Map<String, Object> outputs = applyResponseMapping(rawResponse, null);

            // 检查是否有错误
            String errorCode = extractString(outputs, "errorCode");
            String errorMessage = extractString(outputs, "errorMessage");
            if (errorCode != null || errorMessage != null) {
                return PluginExecutionResult.builder()
                    .status(PluginExecutionResult.ExecutionStatus.FAILED)
                    .errorCode(errorCode)
                    .errorMessage(errorMessage)
                    .outputs(outputs)
                    .rawResponse(rawResponse)
                    .completedAt(LocalDateTime.now())
                    .build();
            }

            // 提取资产
            List<PluginExecutionResult.GeneratedAsset> assets = extractAssets(outputs, config);

            return PluginExecutionResult.builder()
                .status(PluginExecutionResult.ExecutionStatus.SUCCEEDED)
                .outputs(outputs)
                .assets(assets)
                .rawResponse(rawResponse)
                .completedAt(LocalDateTime.now())
                .build();

        } catch (Exception e) {
            log.error("Failed to handle blocking response: {}", e.getMessage(), e);
            return PluginExecutionResult.failure(null, "PARSE_ERROR", "响应解析失败: " + e.getMessage());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> applyResponseMapping(Object rawResponse, Map<String, Object> responseMapping) {
        if (responseMapping == null || responseMapping.isEmpty()) {
            // 无映射规则，直接返回原始响应
            if (rawResponse instanceof Map) {
                return (Map<String, Object>) rawResponse;
            }
            return Map.of("result", rawResponse);
        }

        Map<String, Object> result = new HashMap<>();
        Object jsonDoc = rawResponse;

        for (Map.Entry<String, Object> entry : responseMapping.entrySet()) {
            String outputKey = entry.getKey();
            Object mappingValue = entry.getValue();

            if (mappingValue instanceof String jsonPath) {
                // JSONPath映射
                if (jsonPath.startsWith("$")) {
                    try {
                        Object value = JsonPath.using(JSON_PATH_CONFIG).parse(jsonDoc).read(jsonPath);
                        result.put(outputKey, value);
                    } catch (Exception e) {
                        log.warn("JSONPath extraction failed for {}: {}", jsonPath, e.getMessage());
                        result.put(outputKey, null);
                    }
                } else {
                    // 直接取字段
                    if (rawResponse instanceof Map<?, ?> map) {
                        result.put(outputKey, map.get(jsonPath));
                    }
                }
            } else {
                // 固定值
                result.put(outputKey, mappingValue);
            }
        }

        return result;
    }

    @Override
    public List<PluginExecutionResult.GeneratedAsset> extractAssets(
            Map<String, Object> outputs, PluginConfig config) {

        List<PluginExecutionResult.GeneratedAsset> assets = new ArrayList<>();

        // 尝试从常见字段提取资产
        extractAssetFromField(outputs, "url", "IMAGE", assets);
        extractAssetFromField(outputs, "imageUrl", "IMAGE", assets);
        extractAssetFromField(outputs, "image_url", "IMAGE", assets);
        extractAssetFromField(outputs, "videoUrl", "VIDEO", assets);
        extractAssetFromField(outputs, "video_url", "VIDEO", assets);
        extractAssetFromField(outputs, "audioUrl", "AUDIO", assets);
        extractAssetFromField(outputs, "audio_url", "AUDIO", assets);

        // 从数组中提取
        extractAssetsFromArray(outputs, "images", "IMAGE", assets);
        extractAssetsFromArray(outputs, "videos", "VIDEO", assets);
        extractAssetsFromArray(outputs, "audios", "AUDIO", assets);
        extractAssetsFromArray(outputs, "files", null, assets);

        return assets;
    }

    private void extractAssetFromField(Map<String, Object> outputs, String field,
                                       String assetType, List<PluginExecutionResult.GeneratedAsset> assets) {
        Object value = outputs.get(field);
        if (value instanceof String url && !url.isEmpty()) {
            assets.add(PluginExecutionResult.GeneratedAsset.builder()
                .assetType(assetType)
                .url(url)
                .base64(isBase64(url))
                .build());
        }
    }

    @SuppressWarnings("unchecked")
    private void extractAssetsFromArray(Map<String, Object> outputs, String field,
                                        String defaultType, List<PluginExecutionResult.GeneratedAsset> assets) {
        Object value = outputs.get(field);
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String url) {
                    assets.add(PluginExecutionResult.GeneratedAsset.builder()
                        .assetType(defaultType)
                        .url(url)
                        .base64(isBase64(url))
                        .build());
                } else if (item instanceof Map<?, ?> map) {
                    String url = (String) map.get("url");
                    if (url == null) url = (String) map.get("data");
                    if (url != null) {
                        Object typeObj = map.get("type");
                        String type = typeObj instanceof String ? (String) typeObj : defaultType;
                        assets.add(PluginExecutionResult.GeneratedAsset.builder()
                            .assetType(type)
                            .url(url)
                            .base64(isBase64(url))
                            .mimeType((String) map.get("mimeType"))
                            .fileName((String) map.get("fileName"))
                            .build());
                    }
                }
            }
        }
    }

    private boolean isBase64(String data) {
        return data != null && (data.startsWith("data:") || data.length() > 200 && !data.startsWith("http"));
    }

    private String extractString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof String s ? s : null;
    }
}
