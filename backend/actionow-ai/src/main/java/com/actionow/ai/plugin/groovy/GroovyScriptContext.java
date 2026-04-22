package com.actionow.ai.plugin.groovy;

import com.actionow.ai.plugin.groovy.binding.*;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Groovy脚本执行上下文
 * 定义脚本中可用的变量和工具
 *
 * @author Actionow
 */
@Data
@Builder
public class GroovyScriptContext {

    /**
     * 用户输入参数
     */
    @Builder.Default
    private Map<String, Object> inputs = new HashMap<>();

    /**
     * 提供商配置
     */
    @Builder.Default
    private Map<String, Object> config = new HashMap<>();

    /**
     * 自定义请求头
     */
    @Builder.Default
    private Map<String, String> headers = new HashMap<>();

    /**
     * API基础URL
     */
    private String baseUrl;

    /**
     * API端点
     */
    private String endpoint;

    /**
     * 执行ID
     */
    private String executionId;

    /**
     * 提供商ID
     */
    private String providerId;

    /**
     * 工作空间ID
     */
    private String workspaceId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 执行时间戳
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * 原始响应（用于响应映射脚本）
     */
    private Object response;

    /**
     * JSON工具绑定
     */
    @Builder.Default
    private JsonBinding json = new JsonBinding();

    /**
     * HTTP工具绑定
     */
    @Builder.Default
    private HttpBinding http = new HttpBinding();

    /**
     * 加密工具绑定
     */
    @Builder.Default
    private CryptoBinding crypto = new CryptoBinding();

    /**
     * 日志工具绑定
     */
    @Builder.Default
    private LogBinding log = new LogBinding();

    /**
     * OSS工具绑定（需要通过BindingFactory设置）
     */
    private OssBinding oss;

    /**
     * 数据库操作绑定（需要通过BindingFactory设置）
     */
    private DbBinding db;

    /**
     * 消息通知绑定（需要通过BindingFactory设置）
     */
    private NotifyBinding notify;

    /**
     * 资产处理绑定（需要通过BindingFactory设置）
     */
    private AssetBinding asset;

    /**
     * LLM 调用绑定（需要通过BindingFactory设置）
     */
    private LlmBinding llm;

    /**
     * 响应处理辅助工具（自动注入）
     */
    private ResponseHelper resp;

    /**
     * 请求构建辅助工具（自动注入）
     */
    private RequestHelper req;

    /**
     * 租户Schema
     */
    private String tenantSchema;

    /**
     * 额外上下文数据（用于传递扩展参数）
     */
    @Builder.Default
    private Map<String, Object> extras = new HashMap<>();

    /**
     * 获取额外上下文（延迟初始化）
     */
    public Map<String, Object> getExtras() {
        if (extras == null) {
            extras = new HashMap<>();
        }
        return extras;
    }

    /**
     * 转换为Groovy绑定变量Map
     */
    public Map<String, Object> toBindings() {
        Map<String, Object> bindings = new HashMap<>();

        // 数据变量
        bindings.put("inputs", inputs != null ? inputs : new HashMap<>());
        bindings.put("config", config != null ? config : new HashMap<>());
        bindings.put("headers", headers != null ? headers : new HashMap<>());
        bindings.put("baseUrl", baseUrl);
        bindings.put("endpoint", endpoint);

        // 元数据
        bindings.put("executionId", executionId);
        bindings.put("providerId", providerId);
        bindings.put("workspaceId", workspaceId);
        bindings.put("userId", userId);
        bindings.put("tenantSchema", tenantSchema);
        bindings.put("timestamp", timestamp);

        // 响应数据（用于响应映射）
        if (response != null) {
            bindings.put("response", response);
        }

        // 基础工具绑定
        bindings.put("json", json);
        bindings.put("http", http);
        bindings.put("crypto", crypto);
        bindings.put("log", log);

        // 扩展工具绑定（如果已设置）
        if (oss != null) {
            bindings.put("oss", oss);
        }
        if (db != null) {
            bindings.put("db", db);
        }
        if (notify != null) {
            bindings.put("notify", notify);
        }
        if (asset != null) {
            bindings.put("asset", asset);
        }
        if (llm != null) {
            bindings.put("llm", llm);
        }

        // 辅助工具绑定
        if (resp != null) {
            bindings.put("resp", resp);
        }
        if (req != null) {
            bindings.put("req", req);
        }

        // 额外上下文
        if (extras != null && !extras.isEmpty()) {
            bindings.put("extras", extras);
            // 也将extras中的值平铺到顶层
            bindings.putAll(extras);
        }

        return bindings;
    }

    /**
     * 创建请求构建上下文（带headers）
     */
    public static GroovyScriptContext forRequestBuilder(Map<String, Object> inputs,
                                                        Map<String, Object> config,
                                                        Map<String, String> headers) {
        return GroovyScriptContext.builder()
                .inputs(inputs)
                .config(config)
                .headers(headers)
                .build();
    }

    /**
     * 创建请求构建上下文（不带headers）
     */
    public static GroovyScriptContext forRequestBuilder(Map<String, Object> inputs,
                                                        Map<String, Object> config) {
        return GroovyScriptContext.builder()
                .inputs(inputs)
                .config(config)
                .build();
    }

    /**
     * 创建响应映射上下文
     */
    public static GroovyScriptContext forResponseMapper(Object response,
                                                        Map<String, Object> config) {
        return GroovyScriptContext.builder()
                .response(response)
                .config(config)
                .build();
    }

    /**
     * 创建响应映射上下文（带inputs）
     */
    public static GroovyScriptContext forResponseMapper(Map<String, Object> inputs,
                                                        Map<String, Object> config,
                                                        Object response) {
        return GroovyScriptContext.builder()
                .inputs(inputs)
                .config(config)
                .response(response)
                .build();
    }

    /**
     * 应用BindingHolder中的扩展绑定
     *
     * @param holder 绑定持有者
     * @return 当前上下文（支持链式调用）
     */
    public GroovyScriptContext withBindings(BindingFactory.BindingHolder holder) {
        if (holder != null) {
            if (holder.getOss() != null) {
                this.oss = holder.getOss();
            }
            if (holder.getDb() != null) {
                this.db = holder.getDb();
            }
            if (holder.getNotify() != null) {
                this.notify = holder.getNotify();
            }
            if (holder.getAsset() != null) {
                this.asset = holder.getAsset();
            }
            if (holder.getLlm() != null) {
                this.llm = holder.getLlm();
            }
        }
        return this;
    }

    /**
     * 创建带完整上下文的执行环境
     *
     * @param inputs       输入参数
     * @param config       配置
     * @param executionId  执行ID
     * @param workspaceId  工作空间ID
     * @param userId       用户ID
     * @param tenantSchema 租户Schema
     * @return 上下文
     */
    public static GroovyScriptContext forExecution(Map<String, Object> inputs,
                                                    Map<String, Object> config,
                                                    String executionId,
                                                    String workspaceId,
                                                    String userId,
                                                    String tenantSchema) {
        return GroovyScriptContext.builder()
                .inputs(inputs)
                .config(config)
                .executionId(executionId)
                .workspaceId(workspaceId)
                .userId(userId)
                .tenantSchema(tenantSchema)
                .build();
    }
}
