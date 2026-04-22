package com.actionow.ai.plugin.groovy;

import com.actionow.ai.plugin.groovy.binding.AssetBinding;
import com.actionow.ai.plugin.groovy.binding.BindingFactory;
import com.actionow.ai.plugin.groovy.binding.RequestHelper;
import com.actionow.ai.plugin.model.PluginConfig;
import com.actionow.ai.plugin.model.PluginExecutionRequest;
import com.actionow.ai.plugin.model.ResponseMode;
import com.actionow.ai.service.AssetInputResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Groovy脚本请求构建器
 * 负责使用Groovy脚本构建HTTP请求体
 *
 * @author Actionow
 */
@Slf4j
@RequiredArgsConstructor
public class GroovyRequestBuilder {

    private final GroovyScriptEngine scriptEngine;
    private final AssetInputResolver assetInputResolver;

    /**
     * 使用Groovy脚本构建请求体
     *
     * @param config  插件配置
     * @param request 执行请求
     * @return 构建的请求体
     */
    public Object buildRequestBody(PluginConfig config, PluginExecutionRequest request) {
        // 如果使用原始请求体，直接返回
        if (request.isUseRawBody() && request.getRawRequestBody() != null) {
            return request.getRawRequestBody();
        }

        String script = getRequestBuilderScript(config);
        if (!StringUtils.hasText(script)) {
            // 无脚本，使用默认逻辑
            return request.getInputs() != null ? request.getInputs() : new HashMap<>();
        }

        // 创建脚本执行上下文
        GroovyScriptContext context = GroovyScriptContext.forRequestBuilder(
            request.getInputs(),
            config.toMap()
        );

        // 添加额外上下文
        context.getExtras().put("executionId", request.getExecutionId());
        context.getExtras().put("responseMode", request.getResponseMode().name());
        if (config.getCustomHeaders() != null) {
            context.setHeaders(config.getCustomHeaders());
        }

        // 创建并设置 asset binding，支持管理员在脚本中手动调用素材解析
        AssetBinding assetBinding = createAssetBinding(request);
        context.setAsset(assetBinding);

        // 注入 RequestHelper（基于 inputSchema 自动构建请求体）
        context.setReq(new RequestHelper(config.getInputSchema(), request.getInputs()));

        // 执行脚本
        return scriptEngine.executeRequestBuilder(script, context);
    }

    /**
     * 构建异步请求体（带回调信息）
     *
     * @param config  插件配置
     * @param request 执行请求
     * @return 构建的请求体
     */
    public Object buildAsyncRequestBody(PluginConfig config, PluginExecutionRequest request) {
        Object requestBody = buildRequestBody(config, request);

        // 添加回调信息
        if (requestBody instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> bodyMap = (Map<String, Object>) requestBody;
            if (request.getResponseMode() == ResponseMode.CALLBACK && request.getCallbackUrl() != null) {
                bodyMap.put("callback_url", request.getCallbackUrl());
                if (request.getCallbackMetadata() != null) {
                    bodyMap.put("callback_metadata", request.getCallbackMetadata());
                }
            }
        }

        return requestBody;
    }

    /**
     * 获取请求构建脚本
     * 优先使用内联脚本，其次使用模板引用
     *
     * @param config 插件配置
     * @return 脚本内容
     */
    private String getRequestBuilderScript(PluginConfig config) {
        if (StringUtils.hasText(config.getRequestBuilderScript())) {
            return config.getRequestBuilderScript();
        }
        // 模板引用由上层服务解析后填入 requestBuilderScript
        return null;
    }

    /**
     * 创建 Asset Binding
     * 用于在脚本中手动解析素材ID
     *
     * @param request 执行请求
     * @return AssetBinding 实例
     */
    private AssetBinding createAssetBinding(PluginExecutionRequest request) {
        AssetBinding assetBinding = new AssetBinding();
        assetBinding.setContext(request.getWorkspaceId(), request.getExecutionId());
        assetBinding.setAssetInputResolver(assetInputResolver);
        return assetBinding;
    }
}
