package com.actionow.agent.saa.hook;

import com.actionow.agent.saa.interceptor.SelfDialogueGuardInterceptor;
import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;

import java.util.List;

/**
 * 自言自语兜底检测 Hook。
 *
 * <p>配合 {@link SelfDialogueGuardInterceptor} 使用；挂接到 ReactAgent hooks 链，
 * 检测模型是否在输出中替用户作答（与提示词铁律形成双重防线）。
 *
 * @author Actionow
 */
public class SelfDialogueGuardHook extends AgentHook {

    @Override
    public String getName() {
        return "SelfDialogueGuardHook";
    }

    @Override
    public List<ModelInterceptor> getModelInterceptors() {
        return List.of(new SelfDialogueGuardInterceptor());
    }
}
