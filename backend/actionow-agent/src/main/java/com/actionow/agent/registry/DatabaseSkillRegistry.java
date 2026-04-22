package com.actionow.agent.registry;

import com.alibaba.cloud.ai.graph.skills.SkillMetadata;
import com.alibaba.cloud.ai.graph.skills.registry.AbstractSkillRegistry;
import com.actionow.agent.entity.AgentSkillEntity;
import com.actionow.agent.mapper.AgentSkillMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 数据库驱动的多租户 Skill 注册表（v2）
 *
 * <h3>缓存架构</h3>
 * <ul>
 *   <li>{@code systemSkills} — SYSTEM scope Skill，全租户共享</li>
 *   <li>{@code workspaceSkillsMap} — WORKSPACE scope Skill，按工作空间隔离</li>
 *   <li>{@code entityCache} — 原始实体快照（供 SaaAgentFactory 访问扩展字段）</li>
 * </ul>
 *
 * <h3>可见性规则</h3>
 * <ul>
 *   <li>{@code getEffectiveSkills(workspaceId)} = SYSTEM Skill + 同工作空间 WORKSPACE Skill（同名时 WORKSPACE 优先）</li>
 *   <li>{@code getSkillsByNames(names, workspaceId)} = 在 effective 范围内按名称过滤</li>
 * </ul>
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseSkillRegistry extends AbstractSkillRegistry {

    private final AgentSkillMapper skillMapper;

    /** SYSTEM scope Skill（全租户共享）*/
    private volatile Map<String, SkillMetadata> systemSkills = Map.of();

    /** WORKSPACE scope Skill（workspaceId → name → metadata）*/
    private volatile Map<String, Map<String, SkillMetadata>> workspaceSkillsMap = Map.of();

    /** 原始实体快照，Key = "{name}:{workspaceId}"（workspaceId 可为 null）*/
    private volatile Map<String, AgentSkillEntity> entityCache = Map.of();

    @PostConstruct
    public void init() {
        try {
            reload();
        } catch (Exception e) {
            log.warn("Skills 初始化失败（数据库可能尚未就绪），将在首次请求时重试: {}", e.getMessage());
        }
    }

    // ==================== AbstractSkillRegistry 抽象方法 ====================

    @Override
    protected void loadSkillsToRegistry() {
        skills.clear();

        List<AgentSkillEntity> entities = skillMapper.selectAllEnabled();
        Map<String, SkillMetadata> nextSystem = new LinkedHashMap<>();
        Map<String, Map<String, SkillMetadata>> nextWorkspace = new LinkedHashMap<>();
        Map<String, AgentSkillEntity> nextEntityCache = new LinkedHashMap<>();

        for (AgentSkillEntity entity : entities) {
            if (entity == null || !StringUtils.hasText(entity.getName())) {
                log.warn("跳过无效 Skill 记录: id={}", entity != null ? entity.getId() : null);
                continue;
            }

            SkillMetadata metadata = buildMetadata(entity);
            String entityKey = entity.getName() + ":" + entity.getWorkspaceId();

            if ("WORKSPACE".equals(entity.getScope()) && StringUtils.hasText(entity.getWorkspaceId())) {
                nextWorkspace
                        .computeIfAbsent(entity.getWorkspaceId(), k -> new LinkedHashMap<>())
                        .putIfAbsent(entity.getName(), metadata);
            } else {
                // SYSTEM scope（含 scope=null 的历史数据）
                if (!nextSystem.containsKey(entity.getName())) {
                    nextSystem.put(entity.getName(), metadata);
                    skills.put(entity.getName(), metadata);   // 保持父类 AbstractSkillRegistry 兼容
                } else {
                    log.warn("重复 SYSTEM Skill name={}，保留第一条 id={}, 忽略 id={}",
                            entity.getName(),
                            nextEntityCache.getOrDefault(entity.getName() + ":null",
                                    nextEntityCache.get(entity.getName() + ":")).getId(),
                            entity.getId());
                    continue;
                }
            }
            nextEntityCache.putIfAbsent(entityKey, entity);
        }

        systemSkills = Map.copyOf(nextSystem);
        workspaceSkillsMap = Map.copyOf(nextWorkspace);
        entityCache = Map.copyOf(nextEntityCache);

        log.info("Skills loaded: {} SYSTEM + {} workspaces (raw rows: {})",
                systemSkills.size(), workspaceSkillsMap.size(), entities.size());
    }

    // ==================== SkillRegistry 接口方法 ====================

    @Override
    public String readSkillContent(String name) throws IOException {
        AgentSkillEntity entity = findEntityForCurrentContext(name);
        if (entity == null) {
            throw new IOException("Skill not found: " + name);
        }
        return assembleFullContent(entity);
    }

    @Override
    public String getSkillLoadInstructions() {
        return "调用 read_skill(skill_name) 按需加载专家技能。加载后你将获得该领域的专业指导和专用工具。" +
               "当任务涉及多个领域（如同时创建角色、场景和道具），可一次加载多个相关技能。" +
               "优先加载并执行，而非反复向用户确认。可用技能见下方列表。";
    }

    @Override
    public String getRegistryType() {
        return "database";
    }

    @Override
    public SystemPromptTemplate getSystemPromptTemplate() {
        String template = """
                ## 可用技能
                以下技能可通过 `read_skill(skill_name)` 按需加载。加载后将注入对应领域的专业指导和专用工具。
                仅加载当前任务需要的技能，一次加载一个。

                {skills_list}
                """;
        return new SystemPromptTemplate(template);
    }

    // ==================== 多租户查询方法 ====================

    /**
     * 获取当前工作空间的有效 Skill 列表
     * = SYSTEM Skill + 当前 Workspace Skill（同名时 WORKSPACE 优先）
     *
     * @param workspaceId 工作空间 ID（null 则仅返回 SYSTEM Skill）
     */
    public Map<String, SkillMetadata> getEffectiveSkills(String workspaceId) {
        Map<String, SkillMetadata> result = new LinkedHashMap<>(systemSkills);
        if (StringUtils.hasText(workspaceId)) {
            Map<String, SkillMetadata> wsSkills = workspaceSkillsMap.getOrDefault(workspaceId, Map.of());
            result.putAll(wsSkills);   // WORKSPACE 同名覆盖 SYSTEM
        }
        return result;
    }

    /**
     * 按名称列表获取 Skill 子集（按需加载用）
     *
     * @param names       目标 Skill 名称集合（null/empty 返回全量 effective）
     * @param workspaceId 工作空间 ID
     */
    public Map<String, SkillMetadata> getSkillsByNames(
            Collection<String> names, String workspaceId) {
        Map<String, SkillMetadata> effective = getEffectiveSkills(workspaceId);
        if (names == null || names.isEmpty()) {
            return effective;
        }
        Map<String, SkillMetadata> result = new LinkedHashMap<>();
        for (String name : names) {
            SkillMetadata meta = effective.get(name);
            if (meta != null) {
                result.put(name, meta);
            } else {
                log.warn("请求的 Skill '{}' 在工作空间 {} 中不可见，已跳过", name, workspaceId);
            }
        }
        return result;
    }

    /**
     * 获取工作空间维度的实体快照（供 SaaAgentFactory.buildGroupedToolsForSkills 使用）
     *
     * @param workspaceId 工作空间 ID（null 则仅返回 SYSTEM 实体）
     */
    public Map<String, AgentSkillEntity> getCacheSnapshotForWorkspace(String workspaceId) {
        // 合并 SYSTEM + WORKSPACE 实体（WORKSPACE 同名覆盖 SYSTEM）
        Map<String, AgentSkillEntity> result = new LinkedHashMap<>();

        // 先加入 SYSTEM entities
        entityCache.forEach((key, entity) -> {
            if (!"WORKSPACE".equals(entity.getScope())) {
                result.putIfAbsent(entity.getName(), entity);
            }
        });

        // 再覆盖 WORKSPACE entities（仅当前 workspace）
        if (StringUtils.hasText(workspaceId)) {
            entityCache.forEach((key, entity) -> {
                if ("WORKSPACE".equals(entity.getScope())
                        && workspaceId.equals(entity.getWorkspaceId())) {
                    result.put(entity.getName(), entity);
                }
            });
        }
        return result;
    }

    /**
     * 获取指定 Skill 列表的版本快照（name → updatedAt epoch millis）
     * 用于 Mission 创建时快照版本，以及执行时检测变更。
     */
    public Map<String, Long> getSkillVersions(List<String> skillNames, String workspaceId) {
        if (skillNames == null || skillNames.isEmpty()) {
            return Map.of();
        }
        Map<String, AgentSkillEntity> snapshot = getCacheSnapshotForWorkspace(workspaceId);
        Map<String, Long> versions = new LinkedHashMap<>();
        for (String name : skillNames) {
            AgentSkillEntity entity = snapshot.get(name);
            if (entity != null && entity.getUpdatedAt() != null) {
                versions.put(name, entity.getUpdatedAt()
                        .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
            }
        }
        return versions;
    }

    // ==================== 扩展方法（兼容 v1 调用方）====================

    /**
     * 获取指定 Skill 的 outputSchema（供 StructuredOutputTools 进行 JSON 校验）
     */
    public Optional<Map<String, Object>> getOutputSchema(String skillName) {
        AgentSkillEntity entity = findEntityForCurrentContext(skillName);
        if (entity == null) return Optional.empty();
        return Optional.ofNullable(entity.getOutputSchema());
    }

    /**
     * 获取指定 Skill 的 groupedToolIds（供 SaaAgentFactory 构建 SkillsAgentHook）
     */
    public Optional<List<String>> getGroupedToolIds(String skillName) {
        AgentSkillEntity entity = findEntityForCurrentContext(skillName);
        if (entity == null) return Optional.empty();
        return Optional.ofNullable(entity.getGroupedToolIds());
    }

    /**
     * 获取全量实体快照（v1 兼容，无 workspaceId 隔离）
     *
     * @deprecated 请使用 {@link #getCacheSnapshotForWorkspace(String)}
     */
    @Deprecated
    public Map<String, AgentSkillEntity> getCacheSnapshot() {
        return getCacheSnapshotForWorkspace(null);
    }

    // ==================== 私有方法 ====================

    private SkillMetadata buildMetadata(AgentSkillEntity entity) {
        return SkillMetadata.builder()
                .name(entity.getName())
                .description(Objects.requireNonNullElse(entity.getDescription(), ""))
                .skillPath("db://" + entity.getName())
                .fullContent(Objects.requireNonNullElse(entity.getContent(), ""))
                .source("database")
                .build();
    }

    /**
     * 组装完整 Skill 内容（正文 + 参考资料 + 示例）
     */
    private String assembleFullContent(AgentSkillEntity entity) {
        StringBuilder sb = new StringBuilder(
                Objects.requireNonNullElse(entity.getContent(), ""));

        appendReferences(sb, entity.getReferences());
        appendExamples(sb, entity.getExamples());

        return sb.toString();
    }

    private void appendReferences(StringBuilder sb, List<Map<String, Object>> references) {
        if (references == null || references.isEmpty()) {
            return;
        }
        sb.append("\n\n## 参考资料\n");
        references.stream()
                .sorted((a, b) -> Integer.compare(resolvePriority(b), resolvePriority(a)))
                .forEach(ref -> {
                    Object title = ref.get("title");
                    Object type = ref.get("type");
                    Object kind = ref.get("kind");
                    Object description = ref.get("description");
                    Object content = ref.get("content");
                    Object url = ref.get("url");
                    if (title != null) {
                        sb.append("### ").append(title);
                        if (type != null) sb.append(" [").append(type).append("]");
                        if (kind != null) sb.append(" - ").append(kind);
                        sb.append("\n");
                    }
                    if (description != null) {
                        sb.append(description).append("\n");
                    }
                    if (content != null) {
                        sb.append(content).append("\n");
                    }
                    if (url != null) {
                        sb.append("参考链接: ").append(url).append("\n");
                    }
                    sb.append("\n");
                });
    }

    private void appendExamples(StringBuilder sb, List<Map<String, Object>> examples) {
        if (examples == null || examples.isEmpty()) {
            return;
        }
        sb.append("\n\n## 使用示例\n");
        examples.stream()
                .sorted((a, b) -> Integer.compare(resolvePriority(b), resolvePriority(a)))
                .forEach(ex -> {
                    Object title = ex.get("title");
                    Object type = ex.get("type");
                    Object intent = ex.get("intent");
                    Object input = ex.get("input");
                    Object output = ex.get("output");
                    Object content = ex.get("content");
                    if (title != null) {
                        sb.append("### ").append(title);
                        if (type != null) sb.append(" [").append(type).append("]");
                        if (intent != null) sb.append(" - ").append(intent);
                        sb.append("\n");
                    }
                    if (input != null) {
                        sb.append("输入示例:\n").append(input).append("\n");
                    }
                    if (output != null) {
                        sb.append("输出示例:\n").append(output).append("\n");
                    }
                    if (content != null) {
                        sb.append(content).append("\n");
                    }
                    sb.append("\n");
                });
    }

    private int resolvePriority(Map<String, Object> item) {
        if (item == null) {
            return 0;
        }
        Object priority = item.get("priority");
        if (priority instanceof Number number) {
            return number.intValue();
        }
        if (priority instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * 从 entityCache 查找实体（优先当前请求的 workspaceId）
     * 通过 AgentContextHolder 获取当前请求的工作空间
     */
    private AgentSkillEntity findEntityForCurrentContext(String name) {
        // 尝试从 AgentContextHolder 获取当前工作空间
        String workspaceId = null;
        try {
            var ctx = com.actionow.agent.core.scope.AgentContextHolder.getContext();
            if (ctx != null) workspaceId = ctx.getWorkspaceId();
        } catch (Exception ignored) { /* 无 context 时降级为 SYSTEM */ }

        return findEntity(name, workspaceId);
    }

    /**
     * 从缓存中查找 Skill 实体
     * WORKSPACE 同名优先于 SYSTEM
     */
    private AgentSkillEntity findEntity(String name, String workspaceId) {
        // 优先查 WORKSPACE
        if (StringUtils.hasText(workspaceId)) {
            AgentSkillEntity wsEntity = entityCache.get(name + ":" + workspaceId);
            if (wsEntity != null) return wsEntity;
        }
        // 回退到 SYSTEM（key = "{name}:null"）
        AgentSkillEntity sysEntity = entityCache.get(name + ":null");
        if (sysEntity != null) return sysEntity;
        // 兼容旧 key 格式（无冒号）
        return entityCache.get(name + ":");
    }
}
