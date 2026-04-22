package com.actionow.agent.tool.response;

import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 统一工具响应
 * 所有工具方法返回此类型，确保 LLM 能够一致地解析工具执行结果
 *
 * @author Actionow
 */
@Data
@Builder
public class ToolResponse {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 响应消息（成功提示或错误信息）
     */
    private String message;

    /**
     * 响应数据
     */
    private Map<String, Object> data;

    /**
     * 错误码（失败时）
     */
    private String errorCode;

    // ==================== 成功响应工厂方法 ====================

    /**
     * 创建成功响应（仅消息）
     */
    public static ToolResponse success(String message) {
        return ToolResponse.builder()
                .success(true)
                .message(message)
                .build();
    }

    /**
     * 创建成功响应（带单个数据项）
     */
    public static ToolResponse success(String message, String key, Object value) {
        Map<String, Object> data = new HashMap<>();
        data.put(key, value);
        return ToolResponse.builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    /**
     * 创建成功响应（带多个数据项）
     */
    public static ToolResponse success(String message, Map<String, Object> data) {
        return ToolResponse.builder()
                .success(true)
                .message(message)
                .data(new HashMap<>(data))
                .build();
    }

    /**
     * 创建成功响应（仅数据，无消息）
     */
    public static ToolResponse successData(String key, Object value) {
        Map<String, Object> data = new HashMap<>();
        data.put(key, value);
        return ToolResponse.builder()
                .success(true)
                .data(data)
                .build();
    }

    /**
     * 创建成功响应（带数据和计数）
     */
    public static ToolResponse successWithCount(String key, Object value, int count) {
        Map<String, Object> data = new HashMap<>();
        data.put(key, value);
        data.put("count", count);
        return ToolResponse.builder()
                .success(true)
                .data(data)
                .build();
    }

    // ==================== 错误响应工厂方法 ====================

    /**
     * 创建错误响应（仅消息）
     */
    public static ToolResponse error(String message) {
        return ToolResponse.builder()
                .success(false)
                .message(message)
                .errorCode("TOOL_ERROR")
                .build();
    }

    /**
     * 创建错误响应（带错误码）
     */
    public static ToolResponse error(String errorCode, String message) {
        return ToolResponse.builder()
                .success(false)
                .message(message)
                .errorCode(errorCode)
                .build();
    }

    /**
     * 创建验证错误响应
     */
    public static ToolResponse validationError(String fieldName) {
        return ToolResponse.builder()
                .success(false)
                .message(fieldName + "不能为空")
                .errorCode("VALIDATION_ERROR")
                .build();
    }

    /**
     * 创建权限错误响应
     */
    public static ToolResponse permissionError(String message) {
        return ToolResponse.builder()
                .success(false)
                .message(message)
                .errorCode("PERMISSION_DENIED")
                .build();
    }

    /**
     * 创建资源不存在错误响应
     */
    public static ToolResponse notFound(String resourceType, String resourceId) {
        return ToolResponse.builder()
                .success(false)
                .message(resourceType + "不存在: " + resourceId)
                .errorCode("NOT_FOUND")
                .build();
    }

    // ==================== 转换方法 ====================

    /**
     * 转换为 Map（兼容现有返回格式）
     * 用于与旧代码兼容或直接序列化
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        if (message != null) {
            result.put("message", message);
        }
        if (data != null) {
            result.putAll(data);
        }
        if (!success && errorCode != null) {
            result.put("errorCode", errorCode);
        }
        return result;
    }

    /**
     * 添加额外数据
     */
    public ToolResponse withData(String key, Object value) {
        if (this.data == null) {
            this.data = new HashMap<>();
        }
        this.data.put(key, value);
        return this;
    }

    /**
     * 添加多个额外数据
     */
    public ToolResponse withData(Map<String, Object> additionalData) {
        if (this.data == null) {
            this.data = new HashMap<>();
        }
        this.data.putAll(additionalData);
        return this;
    }
}
