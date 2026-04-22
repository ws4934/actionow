package com.actionow.ai.plugin.groovy.binding;

import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 请求构建辅助工具
 * 基于 InputSchema 自动构建 API 请求体，消除 Groovy 模板中的硬编码字段映射。
 *
 * 通过 Groovy 上下文的 {@code req} 变量暴露给脚本。
 *
 * @author Actionow
 */
@Slf4j
public class RequestHelper {

    /**
     * InputSchema 参数定义列表（每个元素为一个参数定义 Map）
     */
    private final List<Map<String, Object>> inputSchema;

    /**
     * 用户输入参数
     */
    private final Map<String, Object> inputs;

    /**
     * schema 中参数名 → 参数定义 的索引
     */
    private final Map<String, Map<String, Object>> schemaIndex;

    public RequestHelper(List<Map<String, Object>> inputSchema, Map<String, Object> inputs) {
        this.inputSchema = inputSchema != null ? inputSchema : List.of();
        this.inputs = inputs != null ? inputs : Map.of();
        this.schemaIndex = buildIndex(this.inputSchema);
    }

    /**
     * 根据 InputSchema 自动构建请求体。
     * <ul>
     *   <li>遍历 schema 中每个参数，从 inputs 取值</li>
     *   <li>使用 {@code apiFieldName}（若有）作为输出 key，否则使用 {@code name}</li>
     *   <li>自动应用 {@code defaultValue}（当 inputs 中无值时）</li>
     *   <li>透传 schema 中未定义但 inputs 中存在的参数</li>
     * </ul>
     *
     * @return 请求体 Map
     */
    public Map<String, Object> buildBody() {
        return buildBody(null);
    }

    /**
     * 构建请求体，支持额外字段覆盖。
     *
     * @param overrides 额外字段（覆盖自动构建的值），可为 null
     * @return 请求体 Map
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> buildBody(Map<String, Object> overrides) {
        Map<String, Object> body = new LinkedHashMap<>();
        Set<String> processedInputKeys = new HashSet<>();

        // Phase 1: 按 schema 定义顺序构建
        for (Map<String, Object> param : inputSchema) {
            String paramName = (String) param.get("name");
            if (paramName == null || paramName.isBlank()) continue;

            // 输出 key：优先 apiFieldName，否则 name
            String apiField = (String) param.get("apiFieldName");
            String outputKey = (apiField != null && !apiField.isBlank()) ? apiField : paramName;

            // 取值
            Object value = inputs.get(paramName);
            processedInputKeys.add(paramName);

            // 无值时使用 defaultValue
            if (value == null) {
                Object defaultValue = param.get("defaultValue");
                if (defaultValue != null) {
                    value = defaultValue;
                }
            }

            // 跳过 null 值
            if (value == null) continue;

            // 类型转换
            String type = (String) param.get("type");
            value = convertValue(value, type);

            body.put(outputKey, value);
        }

        // Phase 2: 透传 schema 中未定义的 inputs 参数
        for (Map.Entry<String, Object> entry : inputs.entrySet()) {
            if (!processedInputKeys.contains(entry.getKey()) && entry.getValue() != null) {
                body.put(entry.getKey(), entry.getValue());
            }
        }

        // Phase 3: 应用覆盖
        if (overrides != null) {
            body.putAll(overrides);
        }

        return body;
    }

    /**
     * 获取所有必填参数名。
     */
    public List<String> getRequired() {
        return inputSchema.stream()
                .filter(p -> Boolean.TRUE.equals(p.get("required")))
                .map(p -> (String) p.get("name"))
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 取值，如果 inputs 中无值则使用 schema 中的 defaultValue。
     */
    public Object getWithDefault(String paramName) {
        Object value = inputs.get(paramName);
        if (value != null) return value;

        Map<String, Object> param = schemaIndex.get(paramName);
        if (param != null) {
            return param.get("defaultValue");
        }
        return null;
    }

    /**
     * 获取参数类型。
     */
    public String getParamType(String paramName) {
        Map<String, Object> param = schemaIndex.get(paramName);
        return param != null ? (String) param.get("type") : null;
    }

    /**
     * 获取 SELECT 类型的选项列表。
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getOptions(String paramName) {
        Map<String, Object> param = schemaIndex.get(paramName);
        if (param == null) return null;
        Object options = param.get("options");
        return options instanceof List<?> ? (List<Map<String, Object>>) options : null;
    }

    /**
     * 检查参数是否在 schema 中定义。
     */
    public boolean hasParam(String paramName) {
        return schemaIndex.containsKey(paramName);
    }

    /**
     * 获取完整参数定义。
     */
    public Map<String, Object> getParam(String paramName) {
        return schemaIndex.get(paramName);
    }

    // ==================== 内部方法 ====================

    private Map<String, Map<String, Object>> buildIndex(List<Map<String, Object>> schema) {
        Map<String, Map<String, Object>> index = new LinkedHashMap<>();
        for (Map<String, Object> param : schema) {
            String name = (String) param.get("name");
            if (name != null) index.put(name, param);
        }
        return index;
    }

    /**
     * 基于参数类型做基本转换。
     */
    private Object convertValue(Object value, String type) {
        if (type == null || value == null) return value;

        return switch (type) {
            case "NUMBER" -> {
                if (value instanceof Number) yield value;
                try {
                    String s = value.toString();
                    yield s.contains(".") ? Double.parseDouble(s) : Long.parseLong(s);
                } catch (NumberFormatException e) {
                    yield value;
                }
            }
            case "BOOLEAN" -> {
                if (value instanceof Boolean) yield value;
                yield Boolean.parseBoolean(value.toString());
            }
            default -> value;
        };
    }
}
