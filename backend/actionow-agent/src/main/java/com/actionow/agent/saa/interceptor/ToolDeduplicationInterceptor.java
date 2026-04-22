package com.actionow.agent.saa.interceptor;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 工具去重拦截器
 *
 * <p>SkillsInterceptor 在每轮 ReAct 循环中扫描完整对话历史的 read_skill 调用，
 * 导致已加载 Skill 的工具被反复追加到 dynamicToolCallbacks 中。
 * 本拦截器在 SkillsInterceptor 之后运行，按工具名去重，保留首次出现的实例。
 *
 * @author Actionow
 */
@Slf4j
public class ToolDeduplicationInterceptor extends ModelInterceptor {

    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler next) {
        List<ToolCallback> callbacks = request.getDynamicToolCallbacks();
        if (callbacks == null || callbacks.size() <= 1) {
            return next.call(request);
        }

        Set<String> seen = new LinkedHashSet<>();
        List<ToolCallback> deduped = new ArrayList<>(callbacks.size());
        for (ToolCallback cb : callbacks) {
            String name = cb.getToolDefinition().name();
            if (seen.add(name)) {
                deduped.add(cb);
            }
        }

        if (deduped.size() == callbacks.size()) {
            return next.call(request);
        }

        log.debug("Deduplicated dynamicToolCallbacks: {} → {} (removed {} duplicates)",
                callbacks.size(), deduped.size(), callbacks.size() - deduped.size());

        ModelRequest dedupedRequest = ModelRequest.builder(request)
                .dynamicToolCallbacks(deduped)
                .build();
        return next.call(dedupedRequest);
    }

    @Override
    public String getName() {
        return "ToolDeduplicationInterceptor";
    }
}
