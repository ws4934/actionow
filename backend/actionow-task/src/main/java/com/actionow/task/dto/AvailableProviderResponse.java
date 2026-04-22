package com.actionow.task.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 可用模型提供商响应
 * 对应 AI 模块的 ModelProvider
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailableProviderResponse {

    /**
     * 提供商 ID
     */
    private String id;

    /**
     * 提供商名称
     */
    private String name;

    /**
     * 提供商描述
     */
    private String description;

    /**
     * 生成类型: IMAGE/VIDEO/AUDIO/TEXT
     */
    private String providerType;

    /**
     * 应用图标
     */
    private String iconUrl;

    /**
     * 每次执行消耗积分（静态兜底值）
     */
    private Long creditCost;

    /**
     * 动态积分计算规则
     */
    private Map<String, Object> pricingRules;

    /**
     * 动态积分计算 Groovy 脚本
     */
    private String pricingScript;

    /**
     * 超时时间（毫秒）
     */
    private Integer timeout;

    /**
     * 优先级（越高越优先）
     */
    private Integer priority;

    /**
     * 是否支持流式响应
     */
    private Boolean supportsStreaming;

    /**
     * 是否支持阻塞响应
     */
    private Boolean supportsBlocking;

    /**
     * 是否支持回调模式
     */
    private Boolean supportsCallback;

    /**
     * 是否支持轮询模式
     */
    private Boolean supportsPolling;

    /**
     * 输入参数定义列表
     */
    private List<Map<String, Object>> inputSchema;

    /**
     * 输入参数分组列表
     */
    private List<Map<String, Object>> inputGroups;

    /**
     * 互斥参数组列表
     */
    private List<Map<String, Object>> exclusiveGroups;
}
