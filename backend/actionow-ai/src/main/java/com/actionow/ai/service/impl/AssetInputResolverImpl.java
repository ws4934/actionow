package com.actionow.ai.service.impl;

import com.actionow.ai.dto.AssetInfo;
import com.actionow.ai.dto.schema.InputFileConfig;
import com.actionow.ai.dto.schema.InputParamDefinition;
import com.actionow.ai.dto.schema.InputParamType;
import com.actionow.ai.feign.ProjectFeignClient;
import com.actionow.ai.service.AssetInputResolver;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.result.Result;
import com.actionow.common.oss.service.OssService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 素材输入解析服务实现
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetInputResolverImpl implements AssetInputResolver {

    private final ProjectFeignClient projectFeignClient;
    private final OssService ossService;

    /**
     * HTTP 客户端（用于下载文件转Base64）
     */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /**
     * 默认预签名URL过期时间（秒）
     */
    private static final int DEFAULT_PRESIGNED_EXPIRE_SECONDS = 3600;

    @Override
    public Map<String, Object> resolveAssetInputs(Map<String, Object> inputs, List<InputParamDefinition> inputSchema) {
        log.info("[AssetInputResolver] 开始解析输入引用, inputKeys={}, schemaFields={}",
                inputs != null ? inputs.keySet() : "null",
                inputSchema != null ? inputSchema.stream().map(InputParamDefinition::getName).toList() : "null");

        if (inputs == null || inputs.isEmpty()) {
            log.debug("[AssetInputResolver] 输入为空，跳过解析");
            return inputs != null ? inputs : new HashMap<>();
        }

        // 如果没有inputSchema，使用自动检测模式（仅检测素材）
        if (inputSchema == null || inputSchema.isEmpty()) {
            log.info("[AssetInputResolver] inputSchema为空，启用自动检测模式");
            return autoDetectAndResolveAssets(inputs);
        }

        Map<String, Object> resolvedInputs = new HashMap<>(inputs);

        // Phase 1: 解析文件/素材类型引用（替换ID为URL/Base64）
        resolveFileReferences(resolvedInputs, inputSchema);

        // Phase 2: 解析实体类型引用（角色、场景、道具、风格、分镜）
        resolveEntityReferences(resolvedInputs, inputSchema);

        return resolvedInputs;
    }

    /**
     * 解析文件/素材引用
     * 将素材ID替换为URL或Base64
     */
    private void resolveFileReferences(Map<String, Object> resolvedInputs, List<InputParamDefinition> inputSchema) {
        Set<String> assetIdsToResolve = new HashSet<>();
        Map<String, InputParamDefinition> fileParamMap = new HashMap<>();

        for (InputParamDefinition param : inputSchema) {
            InputParamType type = param.getTypeEnum();
            if (!type.isFileType()) {
                continue;
            }

            Object inputValue = resolvedInputs.get(param.getName());
            if (inputValue == null) {
                continue;
            }

            log.debug("[AssetInputResolver] 找到文件参数: name={}, type={}", param.getName(), type);
            fileParamMap.put(param.getName(), param);

            if (type.isListType()) {
                if (inputValue instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof String assetId && isAssetId(assetId)) {
                            assetIdsToResolve.add(assetId);
                        }
                    }
                }
            } else {
                if (inputValue instanceof String assetId && isAssetId(assetId)) {
                    assetIdsToResolve.add(assetId);
                }
            }
        }

        if (assetIdsToResolve.isEmpty()) {
            if (!fileParamMap.isEmpty()) {
                log.info("[AssetInputResolver] 文件参数无需解析的素材ID");
            }
            return;
        }

        log.info("[AssetInputResolver] 解析 {} 个素材引用", assetIdsToResolve.size());
        Map<String, AssetInfo> assetInfoMap = batchGetAssetInfo(new ArrayList<>(assetIdsToResolve));

        for (Map.Entry<String, InputParamDefinition> entry : fileParamMap.entrySet()) {
            String paramName = entry.getKey();
            InputParamDefinition param = entry.getValue();
            InputParamType type = param.getTypeEnum();
            InputFileConfig fileConfig = param.getFileConfig();
            Object inputValue = resolvedInputs.get(paramName);

            if (inputValue == null) {
                continue;
            }

            Object resolvedValue;
            if (type.isListType()) {
                resolvedValue = resolveAssetListInternal(inputValue, fileConfig, assetInfoMap);
            } else {
                resolvedValue = resolveAssetInternal(inputValue, fileConfig, assetInfoMap);
            }
            resolvedInputs.put(paramName, resolvedValue);
        }
    }

    /**
     * 解析实体引用（角色、场景、道具、风格、分镜）
     * 根据 inputSchema 中的实体类型，批量从 Project 服务获取实体数据，
     * 结果存入 inputs._entities 字段供 Groovy 脚本使用
     */
    private void resolveEntityReferences(Map<String, Object> resolvedInputs, List<InputParamDefinition> inputSchema) {
        // 收集实体ID，按批量查询键分组
        Map<String, List<String>> batchQueryRequest = new HashMap<>();
        // 记录每个字段对应的实体响应键
        Map<String, String> fieldToResponseKey = new HashMap<>();
        Map<String, Object> fieldOriginalValues = new HashMap<>();

        for (InputParamDefinition param : inputSchema) {
            InputParamType type = param.getTypeEnum();
            if (!type.isEntityType()) {
                continue;
            }

            Object inputValue = resolvedInputs.get(param.getName());
            if (inputValue == null) {
                continue;
            }

            String batchKey = type.getEntityBatchKey();
            String responseKey = type.getEntityResponseKey();
            if (batchKey == null) {
                continue;
            }

            fieldToResponseKey.put(param.getName(), responseKey);
            fieldOriginalValues.put(param.getName(), inputValue);

            // 收集实体ID
            if (type.isListType()) {
                if (inputValue instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof String strItem && isEntityId(strItem)) {
                            batchQueryRequest.computeIfAbsent(batchKey, k -> new ArrayList<>()).add(strItem);
                        }
                    }
                }
            } else {
                if (inputValue instanceof String strValue && isEntityId(strValue)) {
                    batchQueryRequest.computeIfAbsent(batchKey, k -> new ArrayList<>()).add(strValue);
                }
            }
        }

        if (batchQueryRequest.isEmpty()) {
            return;
        }

        log.info("[AssetInputResolver] 解析实体引用: {}", batchQueryRequest.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size())));

        try {
            Result<Map<String, List<Map<String, Object>>>> result =
                    projectFeignClient.batchQueryEntities(batchQueryRequest);

            if (!result.isSuccess() || result.getData() == null) {
                log.warn("[AssetInputResolver] 批量查询实体失败: {}", result.getMessage());
                return;
            }

            Map<String, List<Map<String, Object>>> entityData = result.getData();

            // 构建 ID → 实体数据 的映射（按响应键分组）
            Map<String, Map<String, Map<String, Object>>> entityMaps = new HashMap<>();
            for (Map.Entry<String, List<Map<String, Object>>> entry : entityData.entrySet()) {
                Map<String, Map<String, Object>> idMap = new HashMap<>();
                for (Map<String, Object> entity : entry.getValue()) {
                    String id = (String) entity.get("id");
                    if (id != null) {
                        idMap.put(id, entity);
                    }
                }
                entityMaps.put(entry.getKey(), idMap);
            }

            // 构建 _entities Map
            Map<String, Object> entitiesMap = new HashMap<>();
            for (Map.Entry<String, String> entry : fieldToResponseKey.entrySet()) {
                String fieldName = entry.getKey();
                String responseKey = entry.getValue();
                Map<String, Map<String, Object>> idMap = entityMaps.getOrDefault(responseKey, Collections.emptyMap());
                Object originalValue = fieldOriginalValues.get(fieldName);

                if (originalValue instanceof List<?> list) {
                    List<Map<String, Object>> resolvedList = new ArrayList<>();
                    for (Object item : list) {
                        if (item instanceof String strItem) {
                            Map<String, Object> entityInfo = idMap.get(strItem);
                            if (entityInfo != null) {
                                resolvedList.add(entityInfo);
                            }
                        }
                    }
                    if (!resolvedList.isEmpty()) {
                        entitiesMap.put(fieldName, resolvedList);
                    }
                } else if (originalValue instanceof String strValue) {
                    Map<String, Object> entityInfo = idMap.get(strValue);
                    if (entityInfo != null) {
                        entitiesMap.put(fieldName, entityInfo);
                    } else {
                        log.warn("[AssetInputResolver] 实体未找到: field={}, id={}", fieldName, strValue);
                    }
                }
            }

            if (!entitiesMap.isEmpty()) {
                resolvedInputs.put("_entities", entitiesMap);
                log.info("[AssetInputResolver] 已解析 {} 个实体引用: {}", entitiesMap.size(), entitiesMap.keySet());
            }
        } catch (Exception e) {
            log.error("[AssetInputResolver] 解析实体引用异常", e);
        }
    }

    /**
     * 判断是否为实体ID（UUID格式，用于实体引用类型）
     */
    private boolean isEntityId(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return value.matches("^[0-9a-fA-F-]{36}$");
    }

    @Override
    public String resolveAssetToUrl(String assetId, InputFileConfig fileConfig) {
        Map<String, AssetInfo> assetInfoMap = batchGetAssetInfo(List.of(assetId));
        AssetInfo assetInfo = assetInfoMap.get(assetId);

        if (assetInfo == null) {
            log.warn("[AssetInputResolver] Asset not found: {}", assetId);
            return null;
        }

        return getAssetUrl(assetInfo, fileConfig);
    }

    @Override
    public String resolveAssetToBase64(String assetId, InputFileConfig fileConfig) {
        Map<String, AssetInfo> assetInfoMap = batchGetAssetInfo(List.of(assetId));
        AssetInfo assetInfo = assetInfoMap.get(assetId);

        if (assetInfo == null) {
            log.warn("[AssetInputResolver] Asset not found: {}", assetId);
            return null;
        }

        return downloadAndConvertToBase64(assetInfo, fileConfig);
    }

    @Override
    public Map<String, AssetInfo> batchGetAssetInfo(List<String> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            log.debug("[AssetInputResolver] batchGetAssetInfo: assetIds is null or empty");
            return new HashMap<>();
        }

        String tenantSchema = UserContextHolder.getTenantSchema();
        String workspaceId = UserContextHolder.getWorkspaceId();
        log.info("[AssetInputResolver] batchGetAssetInfo: 请求获取 {} 个素材信息, ids={}, tenantSchema={}, workspaceId={}",
                assetIds.size(), assetIds, tenantSchema, workspaceId);

        Result<List<Map<String, Object>>> result = projectFeignClient.batchGetAssets(assetIds);
        log.debug("[AssetInputResolver] batchGetAssetInfo: Feign调用返回, success={}, code={}, message={}",
                result.isSuccess(), result.getCode(), result.getMessage());

        if (!result.isSuccess() || result.getData() == null) {
            throw new RuntimeException(String.format(
                    "[AssetInputResolver] 获取素材失败: code=%s, message=%s, tenantSchema=%s, workspaceId=%s",
                    result.getCode(), result.getMessage(), tenantSchema, workspaceId));
        }

        log.debug("[AssetInputResolver] batchGetAssetInfo: 返回 {} 条素材数据", result.getData().size());
        if (!result.getData().isEmpty()) {
            log.debug("[AssetInputResolver] batchGetAssetInfo: 第一条数据示例: {}", result.getData().get(0));
        }

        Map<String, AssetInfo> assetInfoMap = result.getData().stream()
                .map(AssetInfo::fromMap)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(AssetInfo::getId, info -> info, (a, b) -> a));

        log.info("[AssetInputResolver] batchGetAssetInfo: 成功解析 {} 个素材信息", assetInfoMap.size());
        for (Map.Entry<String, AssetInfo> entry : assetInfoMap.entrySet()) {
            log.debug("[AssetInputResolver] batchGetAssetInfo: 素材 id={}, fileUrl={}",
                    entry.getKey(), entry.getValue().getFileUrl());
        }

        // 检查是否有未找到的素材
        List<String> missingIds = assetIds.stream()
                .filter(id -> !assetInfoMap.containsKey(id))
                .toList();
        if (!missingIds.isEmpty()) {
            log.error("[AssetInputResolver] batchGetAssetInfo: {} 个素材未找到: {}, tenantSchema={}, workspaceId={}",
                    missingIds.size(), missingIds, tenantSchema, workspaceId);
        }

        return assetInfoMap;
    }

    @Override
    public Object resolveAsset(String assetId, InputFileConfig fileConfig) {
        Map<String, AssetInfo> assetInfoMap = batchGetAssetInfo(List.of(assetId));
        return resolveAssetInternal(assetId, fileConfig, assetInfoMap);
    }

    @Override
    public List<Object> resolveAssetList(List<String> assetIds, InputFileConfig fileConfig) {
        if (assetIds == null || assetIds.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, AssetInfo> assetInfoMap = batchGetAssetInfo(assetIds);
        return assetIds.stream()
                .map(id -> resolveAssetInternal(id, fileConfig, assetInfoMap))
                .collect(Collectors.toList());
    }

    /**
     * 内部方法：解析单个素材
     * 当素材ID无法解析时抛出异常，防止将原始UUID传递给下游API
     */
    private Object resolveAssetInternal(Object inputValue, InputFileConfig fileConfig,
                                        Map<String, AssetInfo> assetInfoMap) {
        if (inputValue == null) {
            return null;
        }

        // 如果不是素材ID，直接返回原值（可能已经是URL或Base64）
        if (!(inputValue instanceof String assetId) || !isAssetId(assetId)) {
            return inputValue;
        }

        AssetInfo assetInfo = assetInfoMap.get(assetId);
        if (assetInfo == null) {
            throw new RuntimeException("[AssetInputResolver] 素材不存在，无法解析: assetId=" + assetId
                    + ", tenantSchema=" + UserContextHolder.getTenantSchema()
                    + ", workspaceId=" + UserContextHolder.getWorkspaceId());
        }

        InputFileConfig.InputFormat format = getInputFormat(fileConfig);

        return switch (format) {
            case BASE64 -> downloadAndConvertToBase64(assetInfo, fileConfig);
            case URL, BOTH -> getAssetUrl(assetInfo, fileConfig);
        };
    }

    /**
     * 内部方法：解析素材列表
     */
    @SuppressWarnings("unchecked")
    private List<Object> resolveAssetListInternal(Object inputValue, InputFileConfig fileConfig,
                                                   Map<String, AssetInfo> assetInfoMap) {
        if (inputValue == null) {
            return new ArrayList<>();
        }

        if (!(inputValue instanceof List<?> list)) {
            // 单个值转列表
            return List.of(resolveAssetInternal(inputValue, fileConfig, assetInfoMap));
        }

        return list.stream()
                .map(item -> resolveAssetInternal(item, fileConfig, assetInfoMap))
                .collect(Collectors.toList());
    }

    /**
     * 获取素材URL
     */
    private String getAssetUrl(AssetInfo assetInfo, InputFileConfig fileConfig) {
        boolean requirePresigned = fileConfig != null && Boolean.TRUE.equals(fileConfig.getRequirePresignedUrl());

        if (requirePresigned) {
            // 获取预签名URL
            int expireSeconds = fileConfig.getPresignedUrlExpireSeconds() != null
                    ? fileConfig.getPresignedUrlExpireSeconds()
                    : DEFAULT_PRESIGNED_EXPIRE_SECONDS;

            try {
                Result<String> result = projectFeignClient.getAssetDownloadUrl(assetInfo.getId(), expireSeconds);
                if (result.isSuccess() && result.getData() != null) {
                    return result.getData();
                }
            } catch (Exception e) {
                log.error("[AssetInputResolver] Failed to get presigned URL for asset: {}", assetInfo.getId(), e);
            }

            // 降级到永久URL
            return assetInfo.getFileUrl();
        }

        // 直接返回永久URL
        return assetInfo.getFileUrl();
    }

    /**
     * 下载文件并转换为Base64
     */
    private String downloadAndConvertToBase64(AssetInfo assetInfo, InputFileConfig fileConfig) {
        String downloadUrl = assetInfo.getFileUrl();
        if (downloadUrl == null || downloadUrl.isBlank()) {
            log.error("[AssetInputResolver] Asset has no file URL: {}", assetInfo.getId());
            return null;
        }

        try {
            com.actionow.ai.util.SafeUrlValidator.validate(downloadUrl);

            // 下载文件
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(downloadUrl))
                    .timeout(Duration.ofMinutes(5))
                    .GET()
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                log.error("[AssetInputResolver] Failed to download file: {}, status: {}",
                        downloadUrl, response.statusCode());
                return null;
            }

            byte[] data = response.body();
            String base64 = Base64.getEncoder().encodeToString(data);

            // 是否需要添加Data URI前缀
            boolean includePrefix = fileConfig != null && Boolean.TRUE.equals(fileConfig.getIncludeDataUriPrefix());
            if (includePrefix) {
                String mimeType = assetInfo.getMimeType();
                if (mimeType == null || mimeType.isBlank()) {
                    mimeType = detectMimeType(assetInfo);
                }
                return "data:" + mimeType + ";base64," + base64;
            }

            return base64;

        } catch (IOException | InterruptedException e) {
            log.error("[AssetInputResolver] Failed to download and convert to base64: {}", assetInfo.getId(), e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    /**
     * 获取输入格式
     */
    private InputFileConfig.InputFormat getInputFormat(InputFileConfig fileConfig) {
        if (fileConfig == null || fileConfig.getInputFormat() == null) {
            return InputFileConfig.InputFormat.URL;
        }
        return fileConfig.getInputFormat();
    }

    /**
     * 自动检测并解析素材
     * 当没有inputSchema时，自动扫描所有输入值，检测并解析素材ID
     *
     * @param inputs 输入参数
     * @return 解析后的输入参数
     */
    private Map<String, Object> autoDetectAndResolveAssets(Map<String, Object> inputs) {
        // 收集所有可能的素材ID
        Set<String> detectedAssetIds = new HashSet<>();
        Map<String, Boolean> fieldIsListMap = new HashMap<>();

        for (Map.Entry<String, Object> entry : inputs.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof List<?> list) {
                // 检查列表中的每个元素
                boolean hasAssetId = false;
                for (Object item : list) {
                    if (item instanceof String strItem && isAssetId(strItem)) {
                        detectedAssetIds.add(strItem);
                        hasAssetId = true;
                    }
                }
                if (hasAssetId) {
                    fieldIsListMap.put(key, true);
                    log.debug("[AssetInputResolver] 自动检测到列表字段 '{}' 包含素材ID", key);
                }
            } else if (value instanceof String strValue && isAssetId(strValue)) {
                detectedAssetIds.add(strValue);
                fieldIsListMap.put(key, false);
                log.debug("[AssetInputResolver] 自动检测到字段 '{}' 是素材ID: {}", key, strValue);
            }
        }

        if (detectedAssetIds.isEmpty()) {
            log.info("[AssetInputResolver] 自动检测模式：未发现素材ID");
            return inputs;
        }

        log.info("[AssetInputResolver] 自动检测模式：发现 {} 个素材ID，字段: {}",
                detectedAssetIds.size(), fieldIsListMap.keySet());

        // 批量获取素材信息
        Map<String, AssetInfo> assetInfoMap = batchGetAssetInfo(new ArrayList<>(detectedAssetIds));
        if (assetInfoMap.isEmpty()) {
            log.warn("[AssetInputResolver] 自动检测模式：未能获取素材信息");
            return inputs;
        }

        // 解析素材
        Map<String, Object> resolvedInputs = new HashMap<>(inputs);
        for (Map.Entry<String, Boolean> entry : fieldIsListMap.entrySet()) {
            String key = entry.getKey();
            boolean isList = entry.getValue();
            Object value = inputs.get(key);

            if (isList && value instanceof List<?> list) {
                List<Object> resolvedList = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof String strItem && isAssetId(strItem)) {
                        AssetInfo info = assetInfoMap.get(strItem);
                        if (info != null && info.getFileUrl() != null) {
                            resolvedList.add(info.getFileUrl());
                            log.debug("[AssetInputResolver] 自动解析: {} -> {}", strItem, info.getFileUrl());
                        } else {
                            resolvedList.add(item); // 保留原值
                            log.warn("[AssetInputResolver] 自动解析失败，素材不存在: {}", strItem);
                        }
                    } else {
                        resolvedList.add(item); // 非素材ID，保留原值
                    }
                }
                resolvedInputs.put(key, resolvedList);
            } else if (value instanceof String strValue && isAssetId(strValue)) {
                AssetInfo info = assetInfoMap.get(strValue);
                if (info != null && info.getFileUrl() != null) {
                    resolvedInputs.put(key, info.getFileUrl());
                    log.debug("[AssetInputResolver] 自动解析: {} -> {}", strValue, info.getFileUrl());
                } else {
                    log.warn("[AssetInputResolver] 自动解析失败，素材不存在: {}", strValue);
                }
            }
        }

        log.info("[AssetInputResolver] 自动检测模式完成，已解析 {} 个字段", fieldIsListMap.size());
        return resolvedInputs;
    }

    /**
     * 判断是否为素材ID（UUID格式）
     */
    private boolean isAssetId(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        // 素材ID是UUIDv7格式
        // 排除已经是URL或Base64的情况
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return false;
        }
        if (value.startsWith("data:")) {
            return false;
        }
        if (value.length() > 100) {
            // Base64数据通常很长
            return false;
        }

        // 简单的UUID格式检查
        return value.matches("^[0-9a-fA-F-]{36}$");
    }

    /**
     * 检测MIME类型
     */
    private String detectMimeType(AssetInfo assetInfo) {
        String type = assetInfo.getAssetType();
        if (type == null) {
            return "application/octet-stream";
        }

        return switch (type.toUpperCase()) {
            case "IMAGE" -> "image/png";
            case "VIDEO" -> "video/mp4";
            case "AUDIO" -> "audio/mpeg";
            case "DOCUMENT" -> "application/pdf";
            default -> "application/octet-stream";
        };
    }
}
