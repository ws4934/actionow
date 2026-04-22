package com.actionow.agent.saa.hook;

import com.alibaba.cloud.ai.graph.skills.registry.AbstractSkillRegistry;
import com.actionow.agent.registry.DatabaseSkillRegistry;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 对 DatabaseSkillRegistry 的适配器，将可见 Skill 限制到指定名称列表。
 * 用于 buildWorkerAgent 构建请求作用域 Agent 时限制 Skill 可见性。
 *
 * @author Actionow
 */
public class FilteredSkillRegistryAdapter extends AbstractSkillRegistry {

    private final DatabaseSkillRegistry delegate;
    private final Set<String> allowed;
    private final String workspaceId;

    public FilteredSkillRegistryAdapter(DatabaseSkillRegistry delegate,
                                         List<String> skillNames,
                                         String workspaceId) {
        this.delegate = delegate;
        this.allowed = new LinkedHashSet<>(skillNames);
        this.workspaceId = workspaceId;
        reload(); // initialize skills map before being passed to SkillsAgentHook
    }

    @Override
    protected void loadSkillsToRegistry() {
        skills.clear();
        delegate.getSkillsByNames(allowed, workspaceId)
                .forEach((name, meta) -> skills.put(name, meta));
    }

    @Override
    public String readSkillContent(String name) throws IOException {
        if (!allowed.contains(name)) {
            throw new IOException("Skill not accessible in this request scope: " + name);
        }
        return delegate.readSkillContent(name);
    }

    @Override
    public String getSkillLoadInstructions() {
        return delegate.getSkillLoadInstructions();
    }

    @Override
    public String getRegistryType() {
        return "filtered-database";
    }

    @Override
    public SystemPromptTemplate getSystemPromptTemplate() {
        return delegate.getSystemPromptTemplate();
    }
}
