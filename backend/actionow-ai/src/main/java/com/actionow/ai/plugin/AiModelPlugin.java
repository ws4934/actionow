package com.actionow.ai.plugin;

import com.actionow.ai.plugin.model.*;
import reactor.core.publisher.Flux;

import java.util.Set;

/**
 * AI模型插件核心接口
 * 所有AI模型插件必须实现此接口
 *
 * @author Actionow
 */
public interface AiModelPlugin {

    /**
     * 获取插件唯一标识
     * 如: "groovy", "generic-http", "openai-direct"
     *
     * @return 插件ID
     */
    String getPluginId();

    /**
     * 获取插件显示名称
     *
     * @return 插件名称
     */
    String getPluginName();

    /**
     * 获取插件版本
     *
     * @return 版本号
     */
    default String getVersion() {
        return "1.0.0";
    }

    /**
     * 获取插件描述
     *
     * @return 描述信息
     */
    default String getDescription() {
        return "";
    }

    /**
     * 获取支持的生成类型
     * 如: IMAGE, VIDEO, AUDIO, TEXT
     *
     * @return 支持的类型集合
     */
    Set<String> getSupportedTypes();

    /**
     * 获取支持的响应模式
     *
     * @return 支持的响应模式集合
     */
    Set<ResponseMode> getSupportedModes();

    /**
     * 检查插件是否支持指定的响应模式
     *
     * @param mode 响应模式
     * @return 是否支持
     */
    default boolean supportsMode(ResponseMode mode) {
        return getSupportedModes().contains(mode);
    }

    /**
     * 初始化插件
     * 在插件首次使用前调用
     *
     * @param config 插件配置
     */
    default void initialize(PluginConfig config) {
        // 默认空实现
    }

    /**
     * 销毁插件
     * 在插件卸载前调用
     */
    default void destroy() {
        // 默认空实现
    }

    /**
     * 阻塞执行
     * 同步等待结果返回
     *
     * @param config 插件配置
     * @param request 执行请求
     * @return 执行结果
     * @throws UnsupportedOperationException 如果不支持阻塞模式
     */
    PluginExecutionResult execute(PluginConfig config, PluginExecutionRequest request);

    /**
     * 流式执行
     * 返回事件流
     *
     * @param config 插件配置
     * @param request 执行请求
     * @return 事件流
     * @throws UnsupportedOperationException 如果不支持流式模式
     */
    default Flux<PluginStreamEvent> executeStream(PluginConfig config, PluginExecutionRequest request) {
        throw new UnsupportedOperationException("Plugin does not support streaming mode: " + getPluginId());
    }

    /**
     * 异步提交任务
     * 用于回调或轮询模式，提交后立即返回
     *
     * @param config 插件配置
     * @param request 执行请求
     * @return 包含外部任务ID的结果（状态为PENDING）
     * @throws UnsupportedOperationException 如果不支持异步模式
     */
    default PluginExecutionResult submitAsync(PluginConfig config, PluginExecutionRequest request) {
        throw new UnsupportedOperationException("Plugin does not support async mode: " + getPluginId());
    }

    /**
     * 轮询任务状态
     * 用于轮询模式，查询异步任务的状态
     *
     * @param config 插件配置
     * @param externalTaskId 外部任务ID
     * @param externalRunId 外部运行ID（可选）
     * @return 执行结果
     * @throws UnsupportedOperationException 如果不支持轮询
     */
    default PluginExecutionResult pollStatus(PluginConfig config, String externalTaskId, String externalRunId) {
        throw new UnsupportedOperationException("Plugin does not support polling: " + getPluginId());
    }

    /**
     * 取消任务
     *
     * @param config 插件配置
     * @param externalTaskId 外部任务ID
     * @param userId 用户ID
     * @return 是否取消成功
     */
    default boolean cancel(PluginConfig config, String externalTaskId, String userId) {
        // 默认不支持取消
        return false;
    }

    /**
     * 验证配置是否有效
     *
     * @param config 插件配置
     * @return 验证结果
     */
    default ValidationResult validateConfig(PluginConfig config) {
        return ValidationResult.success();
    }

    /**
     * 测试连接
     *
     * @param config 插件配置
     * @return 测试结果
     */
    default TestConnectionResult testConnection(PluginConfig config) {
        return TestConnectionResult.success();
    }

    /**
     * 配置验证结果
     */
    record ValidationResult(boolean valid, String message) {
        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult failure(String message) {
            return new ValidationResult(false, message);
        }
    }

    /**
     * 连接测试结果
     */
    record TestConnectionResult(boolean connected, String message, Long latencyMs) {
        public static TestConnectionResult success() {
            return new TestConnectionResult(true, null, null);
        }

        public static TestConnectionResult success(long latencyMs) {
            return new TestConnectionResult(true, null, latencyMs);
        }

        public static TestConnectionResult failure(String message) {
            return new TestConnectionResult(false, message, null);
        }
    }
}
