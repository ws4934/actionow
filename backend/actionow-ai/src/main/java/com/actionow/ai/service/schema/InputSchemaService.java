package com.actionow.ai.service.schema;

import com.actionow.ai.dto.schema.*;
import com.actionow.ai.entity.ModelProvider;
import com.actionow.ai.service.ModelProviderService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 输入Schema服务
 * 提供Schema验证、转换、模板管理等功能
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InputSchemaService {

    private final SchemaValidator schemaValidator;
    private final ModelProviderService modelProviderService;
    private final ObjectMapper objectMapper;

    /**
     * 验证输入数据
     *
     * @param providerId 模型提供商ID
     * @param input      用户输入
     * @return 验证结果
     */
    public SchemaValidator.ValidationResult validateInput(String providerId, Map<String, Object> input) {
        ModelProvider provider = modelProviderService.getById(providerId);
        if (provider == null) {
            return SchemaValidator.ValidationResult.failure(
                    SchemaValidator.ValidationError.custom("providerId", "模型提供商不存在")
            );
        }

        List<InputParamDefinition> schema = convertToParamDefinitions(provider.getInputSchema());
        List<InputParamGroup> groups = convertToParamGroups(provider.getInputGroups());
        List<ExclusiveGroup> exclusiveGroups = convertToExclusiveGroups(provider.getExclusiveGroups());

        return schemaValidator.validate(input, schema, groups, exclusiveGroups);
    }

    /**
     * 获取模型提供商的完整Schema信息（给前端渲染表单用）
     *
     * @param providerId 模型提供商ID
     * @return Schema信息
     */
    public FormSchema getFormSchema(String providerId) {
        ModelProvider provider = modelProviderService.getById(providerId);
        if (provider == null) {
            return null;
        }

        return buildFormSchema(provider);
    }

    /**
     * 构建表单Schema
     */
    public FormSchema buildFormSchema(ModelProvider provider) {
        List<InputParamDefinition> params = convertToParamDefinitions(provider.getInputSchema());
        List<InputParamGroup> groups = convertToParamGroups(provider.getInputGroups());
        List<ExclusiveGroup> exclusiveGroups = convertToExclusiveGroups(provider.getExclusiveGroups());

        // 按分组组织参数
        Map<String, List<InputParamDefinition>> groupedParams = new LinkedHashMap<>();

        // 首先处理无分组的参数
        List<InputParamDefinition> ungroupedParams = params.stream()
                .filter(p -> p.getGroup() == null || p.getGroup().isEmpty())
                .sorted(Comparator.comparingInt(p -> p.getOrder() != null ? p.getOrder() : 0))
                .collect(Collectors.toList());

        if (!ungroupedParams.isEmpty()) {
            groupedParams.put("_default", ungroupedParams);
        }

        // 处理有分组的参数
        for (InputParamGroup group : groups) {
            List<InputParamDefinition> groupParams = params.stream()
                    .filter(p -> group.getName().equals(p.getGroup()))
                    .sorted(Comparator.comparingInt(p -> p.getOrder() != null ? p.getOrder() : 0))
                    .collect(Collectors.toList());
            groupedParams.put(group.getName(), groupParams);
        }

        // 排序分组
        List<InputParamGroup> sortedGroups = new ArrayList<>(groups);
        sortedGroups.sort(Comparator.comparingInt(g -> g.getOrder() != null ? g.getOrder() : 0));

        // 排序互斥组
        List<ExclusiveGroup> sortedExclusiveGroups = new ArrayList<>(exclusiveGroups);
        sortedExclusiveGroups.sort(Comparator.comparingInt(g -> g.getOrder() != null ? g.getOrder() : 0));

        return FormSchema.builder()
                .providerId(provider.getId())
                .providerName(provider.getName())
                .providerType(provider.getProviderType())
                .params(params)
                .groups(sortedGroups)
                .exclusiveGroups(sortedExclusiveGroups)
                .groupedParams(groupedParams)
                .build();
    }

    /**
     * 合并用户输入和默认值
     *
     * @param providerId 模型提供商ID
     * @param input      用户输入
     * @return 合并后的输入
     */
    public Map<String, Object> mergeWithDefaults(String providerId, Map<String, Object> input) {
        ModelProvider provider = modelProviderService.getById(providerId);
        if (provider == null) {
            return input;
        }

        List<InputParamDefinition> schema = convertToParamDefinitions(provider.getInputSchema());
        Map<String, Object> merged = new LinkedHashMap<>();

        // 先填充默认值
        for (InputParamDefinition param : schema) {
            if (param.getDefaultValue() != null) {
                merged.put(param.getName(), param.getDefaultValue());
            }
        }

        // 覆盖用户输入
        if (input != null) {
            merged.putAll(input);
        }

        return merged;
    }

    /**
     * 过滤输入，只保留Schema中定义的参数
     *
     * @param providerId 模型提供商ID
     * @param input      用户输入
     * @return 过滤后的输入
     */
    public Map<String, Object> filterInput(String providerId, Map<String, Object> input) {
        ModelProvider provider = modelProviderService.getById(providerId);
        if (provider == null || input == null) {
            return input;
        }

        List<InputParamDefinition> schema = convertToParamDefinitions(provider.getInputSchema());
        Set<String> allowedParams = schema.stream()
                .map(InputParamDefinition::getName)
                .collect(Collectors.toSet());

        return input.entrySet().stream()
                .filter(e -> allowedParams.contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b, LinkedHashMap::new));
    }

    /**
     * 获取必填参数列表
     *
     * @param providerId 模型提供商ID
     * @return 必填参数名称列表
     */
    public List<String> getRequiredParams(String providerId) {
        ModelProvider provider = modelProviderService.getById(providerId);
        if (provider == null) {
            return Collections.emptyList();
        }

        List<InputParamDefinition> schema = convertToParamDefinitions(provider.getInputSchema());
        return schema.stream()
                .filter(p -> Boolean.TRUE.equals(p.getRequired()))
                .map(InputParamDefinition::getName)
                .collect(Collectors.toList());
    }

    /**
     * 获取参数类型映射
     *
     * @param providerId 模型提供商ID
     * @return 参数名 -> 参数类型
     */
    public Map<String, InputParamType> getParamTypes(String providerId) {
        ModelProvider provider = modelProviderService.getById(providerId);
        if (provider == null) {
            return Collections.emptyMap();
        }

        List<InputParamDefinition> schema = convertToParamDefinitions(provider.getInputSchema());
        return schema.stream()
                .collect(Collectors.toMap(
                        InputParamDefinition::getName,
                        InputParamDefinition::getTypeEnum,
                        (a, b) -> b
                ));
    }

    /**
     * 获取文件类型参数
     *
     * @param providerId 模型提供商ID
     * @return 文件类型参数列表
     */
    public List<InputParamDefinition> getFileParams(String providerId) {
        ModelProvider provider = modelProviderService.getById(providerId);
        if (provider == null) {
            return Collections.emptyList();
        }

        List<InputParamDefinition> schema = convertToParamDefinitions(provider.getInputSchema());
        return schema.stream()
                .filter(p -> p.getTypeEnum().isFileType())
                .collect(Collectors.toList());
    }

    // ========== 类型转换方法 ==========

    /**
     * 转换为参数定义列表
     */
    private List<InputParamDefinition> convertToParamDefinitions(List<Map<String, Object>> rawSchema) {
        if (CollectionUtils.isEmpty(rawSchema)) {
            return Collections.emptyList();
        }

        return rawSchema.stream()
                .map(this::convertToParamDefinition)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 转换单个参数定义
     */
    private InputParamDefinition convertToParamDefinition(Map<String, Object> raw) {
        try {
            return objectMapper.convertValue(raw, InputParamDefinition.class);
        } catch (Exception e) {
            log.warn("Failed to convert param definition: {}", raw, e);
            return null;
        }
    }

    /**
     * 转换为参数分组列表
     */
    private List<InputParamGroup> convertToParamGroups(List<Map<String, Object>> rawGroups) {
        if (CollectionUtils.isEmpty(rawGroups)) {
            return Collections.emptyList();
        }

        return rawGroups.stream()
                .map(raw -> {
                    try {
                        return objectMapper.convertValue(raw, InputParamGroup.class);
                    } catch (Exception e) {
                        log.warn("Failed to convert param group: {}", raw, e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 转换为互斥组列表
     */
    private List<ExclusiveGroup> convertToExclusiveGroups(List<Map<String, Object>> rawGroups) {
        if (CollectionUtils.isEmpty(rawGroups)) {
            return Collections.emptyList();
        }

        return rawGroups.stream()
                .map(raw -> {
                    try {
                        return objectMapper.convertValue(raw, ExclusiveGroup.class);
                    } catch (Exception e) {
                        log.warn("Failed to convert exclusive group: {}", raw, e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 表单Schema（给前端用）
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class FormSchema {
        private String providerId;
        private String providerName;
        private String providerType;
        private List<InputParamDefinition> params;
        private List<InputParamGroup> groups;
        private List<ExclusiveGroup> exclusiveGroups;
        private Map<String, List<InputParamDefinition>> groupedParams;
    }
}
