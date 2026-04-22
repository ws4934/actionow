package com.actionow.agent.feign.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 可用 AI Provider 响应
 * 对应 actionow-ai 的 AvailableProviderResponse
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailableProviderResponse {

    /**
     * Provider ID
     */
    private String id;

    /**
     * Provider 名称
     */
    private String name;

    /**
     * Provider 描述
     */
    private String description;

    /**
     * Provider 类型：IMAGE, VIDEO, AUDIO, TEXT
     */
    private String providerType;

    /**
     * 图标 URL
     */
    private String iconUrl;

    /**
     * 每次调用消耗积分
     */
    private Long creditCost;

    /**
     * 优先级
     */
    private Integer priority;

    /**
     * 支持流式
     */
    private Boolean supportsStreaming;

    /**
     * 支持阻塞
     */
    private Boolean supportsBlocking;

    /**
     * 支持回调
     */
    private Boolean supportsCallback;

    /**
     * 支持轮询
     */
    private Boolean supportsPolling;

    /**
     * 输入参数定义
     */
    private List<Map<String, Object>> inputSchema;

    /**
     * 参数分组信息
     */
    private List<Map<String, Object>> inputGroups;

    /**
     * 互斥参数组
     */
    private List<Map<String, Object>> exclusiveGroups;
}
