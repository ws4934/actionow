package com.actionow.ai.plugin.groovy.binding;

import com.actionow.ai.feign.ProjectFeignClient;
import com.actionow.ai.llm.service.LlmProviderService;
import com.actionow.ai.service.AssetInputResolver;
import com.actionow.common.oss.service.OssService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Binding工厂
 * 负责创建和配置各种Groovy脚本绑定实例
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BindingFactory {

    private final OssService ossService;
    private final ProjectFeignClient projectFeignClient;
    private final RabbitTemplate rabbitTemplate;
    private final AssetInputResolver assetInputResolver;
    private final LlmProviderService llmProviderService;
    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    /**
     * 创建并配置绑定上下文
     *
     * @param context 执行上下文
     * @return 绑定持有者
     */
    public BindingHolder createBindings(BindingContext context) {
        log.debug("[BindingFactory] Creating bindings for execution: {}", context.getExecutionId());

        // 创建基础绑定（这些不需要上下文）
        JsonBinding jsonBinding = new JsonBinding();
        HttpBinding httpBinding = new HttpBinding();
        CryptoBinding cryptoBinding = new CryptoBinding();
        LogBinding logBinding = new LogBinding();

        // 创建需要上下文的绑定
        OssBinding ossBinding = createOssBinding(context);
        DbBinding dbBinding = createDbBinding(context);
        NotifyBinding notifyBinding = createNotifyBinding(context);
        AssetBinding assetBinding = createAssetBinding(context);
        LlmBinding llmBinding = createLlmBinding(context);

        return BindingHolder.builder()
                .json(jsonBinding)
                .http(httpBinding)
                .crypto(cryptoBinding)
                .log(logBinding)
                .oss(ossBinding)
                .db(dbBinding)
                .notify(notifyBinding)
                .asset(assetBinding)
                .llm(llmBinding)
                .build();
    }

    /**
     * 创建OSS绑定
     */
    private OssBinding createOssBinding(BindingContext context) {
        OssBinding ossBinding = new OssBinding(ossService);
        ossBinding.setContext(context.getWorkspaceId(), context.getExecutionId());
        return ossBinding;
    }

    /**
     * 创建数据库绑定
     */
    private DbBinding createDbBinding(BindingContext context) {
        DbBinding dbBinding = new DbBinding(projectFeignClient);
        dbBinding.setContext(context.getWorkspaceId(), context.getUserId(), context.getTenantSchema());
        return dbBinding;
    }

    /**
     * 创建通知绑定
     */
    private NotifyBinding createNotifyBinding(BindingContext context) {
        NotifyBinding notifyBinding = new NotifyBinding(rabbitTemplate);
        notifyBinding.setContext(
                context.getWorkspaceId(),
                context.getUserId(),
                context.getExecutionId(),
                context.getTenantSchema()
        );
        return notifyBinding;
    }

    /**
     * 创建资产处理绑定
     */
    private AssetBinding createAssetBinding(BindingContext context) {
        AssetBinding assetBinding = new AssetBinding();
        assetBinding.setContext(context.getWorkspaceId(), context.getExecutionId());
        assetBinding.setAssetInputResolver(assetInputResolver);
        return assetBinding;
    }

    /**
     * 创建 LLM 调用绑定
     */
    private LlmBinding createLlmBinding(BindingContext context) {
        LlmBinding llmBinding = new LlmBinding(llmProviderService, objectMapper, webClientBuilder);
        llmBinding.setContext(context.getWorkspaceId(), context.getUserId(), context.getTenantSchema());
        llmBinding.setDefaults(
                context.getDefaultLlmProviderId(),
                context.getDefaultSystemPrompt(),
                context.getDefaultResponseSchema()
        );
        return llmBinding;
    }

    /**
     * 绑定上下文
     */
    @lombok.Data
    @lombok.Builder
    public static class BindingContext {
        /**
         * 工作空间ID
         */
        private String workspaceId;

        /**
         * 用户ID
         */
        private String userId;

        /**
         * 执行ID（任务ID）
         */
        private String executionId;

        /**
         * 提供商ID
         */
        private String providerId;

        /**
         * 租户Schema
         */
        private String tenantSchema;

        /**
         * 是否需要数据库访问权限
         */
        @lombok.Builder.Default
        private boolean requiresDbAccess = false;

        /**
         * 是否需要通知权限
         */
        @lombok.Builder.Default
        private boolean requiresNotify = true;

        /**
         * 是否需要OSS访问权限
         */
        @lombok.Builder.Default
        private boolean requiresOss = true;

        // ========== TEXT 类型默认配置 ==========

        /**
         * 默认 LLM Provider ID（TEXT 类型使用）
         */
        private String defaultLlmProviderId;

        /**
         * 默认系统提示词（TEXT 类型使用）
         */
        private String defaultSystemPrompt;

        /**
         * 默认结构化输出 Schema（TEXT 类型使用）
         */
        private java.util.Map<String, Object> defaultResponseSchema;
    }

    /**
     * 绑定持有者
     * 持有所有可用的绑定实例
     */
    @lombok.Data
    @lombok.Builder
    public static class BindingHolder {
        /**
         * JSON工具绑定
         */
        private JsonBinding json;

        /**
         * HTTP工具绑定
         */
        private HttpBinding http;

        /**
         * 加密工具绑定
         */
        private CryptoBinding crypto;

        /**
         * 日志工具绑定
         */
        private LogBinding log;

        /**
         * OSS工具绑定
         */
        private OssBinding oss;

        /**
         * 数据库操作绑定
         */
        private DbBinding db;

        /**
         * 通知绑定
         */
        private NotifyBinding notify;

        /**
         * 资产处理绑定
         */
        private AssetBinding asset;

        /**
         * LLM 调用绑定
         */
        private LlmBinding llm;

        /**
         * 转换为Groovy绑定Map
         */
        public java.util.Map<String, Object> toBindingsMap() {
            java.util.Map<String, Object> bindings = new java.util.HashMap<>();

            // 基础绑定（始终可用）
            if (json != null) bindings.put("json", json);
            if (http != null) bindings.put("http", http);
            if (crypto != null) bindings.put("crypto", crypto);
            if (log != null) bindings.put("log", log);

            // 扩展绑定
            if (oss != null) bindings.put("oss", oss);
            if (db != null) bindings.put("db", db);
            if (notify != null) bindings.put("notify", notify);
            if (asset != null) bindings.put("asset", asset);
            if (llm != null) bindings.put("llm", llm);

            return bindings;
        }
    }
}
