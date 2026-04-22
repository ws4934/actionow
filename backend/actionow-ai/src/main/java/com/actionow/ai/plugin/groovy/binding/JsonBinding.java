package com.actionow.ai.plugin.groovy.binding;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * JSON工具绑定
 * 提供脚本中可用的JSON操作方法
 *
 * @author Actionow
 */
@Slf4j
public class JsonBinding {

    private final JsonSlurper slurper = new JsonSlurper();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 解析JSON字符串
     *
     * @param jsonString JSON字符串
     * @return 解析后的对象（Map或List）
     */
    public Object parse(String jsonString) {
        if (jsonString == null || jsonString.isBlank()) {
            return null;
        }
        try {
            return slurper.parseText(jsonString);
        } catch (Exception e) {
            log.warn("Failed to parse JSON: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 使用JSONPath提取数据
     *
     * @param object JSON对象（Map/List/String）
     * @param path   JSONPath表达式
     * @return 提取的值
     */
    public Object path(Object object, String path) {
        if (object == null || path == null) {
            return null;
        }

        try {
            Object source = object;

            // 如果是字符串，先解析
            if (object instanceof String) {
                source = parse((String) object);
                if (source == null) {
                    return null;
                }
            }

            // 使用JsonPath提取
            String jsonString = objectMapper.writeValueAsString(source);
            return JsonPath.read(jsonString, path);

        } catch (PathNotFoundException e) {
            return null;
        } catch (Exception e) {
            log.warn("[JsonBinding.path] Failed to extract: path={}, error={}", path, e.getMessage());
            return null;
        }
    }

    /**
     * 序列化对象为JSON字符串
     *
     * @param object 要序列化的对象
     * @return JSON字符串
     */
    public String stringify(Object object) {
        if (object == null) {
            return "null";
        }
        try {
            return JsonOutput.toJson(object);
        } catch (Exception e) {
            log.warn("Failed to stringify object: {}", e.getMessage());
            return object.toString();
        }
    }

    /**
     * 格式化JSON字符串（美化输出）
     *
     * @param object 要格式化的对象
     * @return 格式化后的JSON字符串
     */
    public String prettyPrint(Object object) {
        if (object == null) {
            return "null";
        }
        try {
            return JsonOutput.prettyPrint(stringify(object));
        } catch (Exception e) {
            return stringify(object);
        }
    }

    /**
     * 检查是否为有效JSON
     *
     * @param jsonString JSON字符串
     * @return 是否有效
     */
    public boolean isValid(String jsonString) {
        if (jsonString == null || jsonString.isBlank()) {
            return false;
        }
        try {
            slurper.parseText(jsonString);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 合并两个Map
     *
     * @param base    基础Map
     * @param overlay 覆盖Map
     * @return 合并后的Map
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> merge(Map<String, Object> base, Map<String, Object> overlay) {
        if (base == null) {
            return overlay;
        }
        if (overlay == null) {
            return base;
        }

        Map<String, Object> result = new java.util.HashMap<>(base);
        overlay.forEach((key, value) -> {
            if (value instanceof Map && result.get(key) instanceof Map) {
                result.put(key, merge((Map<String, Object>) result.get(key), (Map<String, Object>) value));
            } else {
                result.put(key, value);
            }
        });
        return result;
    }

    /**
     * 获取嵌套值
     *
     * @param map  Map对象
     * @param keys 键路径（点分隔）
     * @return 值
     */
    @SuppressWarnings("unchecked")
    public Object get(Map<String, Object> map, String keys) {
        if (map == null || keys == null) {
            return null;
        }

        String[] parts = keys.split("\\.");
        Object current = map;

        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
            } else if (current instanceof List) {
                try {
                    int index = Integer.parseInt(part);
                    current = ((List<?>) current).get(index);
                } catch (NumberFormatException | IndexOutOfBoundsException e) {
                    return null;
                }
            } else {
                return null;
            }
        }

        return current;
    }
}
