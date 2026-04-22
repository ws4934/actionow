package com.actionow.ai.plugin.handler;

import com.actionow.ai.plugin.model.*;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 响应处理器接口
 * 定义不同响应模式的处理逻辑
 *
 * @author Actionow
 */
public interface ResponseHandler {

    /**
     * 获取支持的响应模式
     *
     * @return 响应模式
     */
    ResponseMode getResponseMode();

    /**
     * 处理响应数据
     *
     * @param rawResponse 原始响应
     * @param config 插件配置
     * @return 处理后的执行结果
     */
    PluginExecutionResult handleResponse(Object rawResponse, PluginConfig config);

    /**
     * 解析响应中的资产
     *
     * @param outputs 输出数据
     * @param config 插件配置
     * @return 生成的资产列表
     */
    default java.util.List<PluginExecutionResult.GeneratedAsset> extractAssets(
            Map<String, Object> outputs, PluginConfig config) {
        return java.util.Collections.emptyList();
    }

    /**
     * 应用响应映射规则
     *
     * @param rawResponse 原始响应
     * @param responseMapping 映射规则
     * @return 映射后的输出
     */
    Map<String, Object> applyResponseMapping(Object rawResponse, Map<String, Object> responseMapping);

    /**
     * 流式响应处理器接口
     */
    interface StreamingResponseHandler extends ResponseHandler {
        /**
         * 处理流式响应
         *
         * @param eventFlux 事件流
         * @param config 插件配置
         * @return 处理后的事件流
         */
        Flux<PluginStreamEvent> handleStreamResponse(Flux<String> eventFlux, PluginConfig config);
    }

    /**
     * 异步响应处理器接口（回调/轮询）
     */
    interface AsyncResponseHandler extends ResponseHandler {
        /**
         * 处理异步提交响应
         *
         * @param submitResponse 提交响应
         * @param config 插件配置
         * @return 包含外部任务ID的结果
         */
        PluginExecutionResult handleSubmitResponse(Object submitResponse, PluginConfig config);

        /**
         * 处理状态查询响应
         *
         * @param statusResponse 状态响应
         * @param config 插件配置
         * @return 执行结果
         */
        PluginExecutionResult handleStatusResponse(Object statusResponse, PluginConfig config);
    }
}
