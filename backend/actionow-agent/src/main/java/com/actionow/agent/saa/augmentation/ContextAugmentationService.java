package com.actionow.agent.saa.augmentation;

import com.actionow.agent.config.AgentRuntimeConfigService;
import com.actionow.agent.config.SaaAgentProperties;
import com.actionow.agent.core.scope.AgentContext;
import com.actionow.agent.core.scope.AgentContextHolder;
import com.actionow.agent.core.scope.AgentScope;
import com.actionow.agent.entity.AgentSessionEntity;
import com.actionow.agent.feign.ProjectFeignClient;
import com.actionow.agent.saa.session.SaaSessionService;
import com.actionow.common.core.result.Result;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 上下文增强服务（SAA v2 版）
 * 适配 v1 ContextAugmentationService，替换 ADK 依赖：
 * - AdkConfig → SaaAgentProperties
 * - ActionowSessionService → SaaSessionService
 *
 * <p>注意：对话历史由 JdbcChatMemoryRepository 自动管理，本类仅负责 RAG 增强和上下文信息注入。
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContextAugmentationService {

    private final SaaAgentProperties saaProperties;
    private final AgentRuntimeConfigService agentRuntimeConfig;
    private final SaaSessionService sessionService;
    private final ProjectFeignClient projectFeignClient;

    /** RAG VectorStore — 仅 rag-enabled=true 时注入 */
    @Autowired(required = false)
    private VectorStore vectorStore;

    /** 基础上下文缓存：key = "scriptId:episodeId"，60s TTL */
    private final Cache<String, String> contextInfoCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60))
            .maximumSize(200)
            .build();

    /** 实体名称缓存：key = "entityType:entityId"，120s TTL（实体名称变更更低频） */
    private final Cache<String, String> entityNameCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(120))
            .maximumSize(500)
            .build();

    @Value("${actionow.context.rag-similarity-threshold:0.6}")
    private double ragSimilarityThreshold;

    @Value("${actionow.context.max-rag-documents:10}")
    private int ragMaxDocuments;

    @Value("${actionow.context.rag-query-timeout-ms:3000}")
    private long ragQueryTimeoutMs;

    /**
     * 对输入消息进行增强（仅 RAG，不注入上下文信息）。
     * 上下文信息现在通过 {@link #buildContextSystemPrompt} 作为 SystemMessage 独立注入，
     * 不再污染用户消息文本。
     */
    public String augmentInput(String userMessage, AgentSessionEntity session) {
        if (agentRuntimeConfig.isRagEnabled()) {
            return augmentWithRAG(session.getWorkspaceId(), userMessage);
        }
        return userMessage;
    }

    /**
     * 构建上下文系统提示词（作为 SystemMessage 独立注入，不拼入用户消息）。
     * 包含：作用域、剧本/章节概要、锚点实体、已有实体概况。
     *
     * @return 上下文系统提示词文本，无需注入时返回 null
     */
    public String buildContextSystemPrompt(AgentSessionEntity session) {
        if (session == null) {
            return null;
        }
        try {
            return buildContextInfo(session);
        } catch (Exception e) {
            log.warn("构建上下文系统提示词失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * RAG 增强 — 使用 Spring AI VectorStore 进行语义搜索
     */
    public String augmentWithRAG(String workspaceId, String message) {
        if (workspaceId == null || !agentRuntimeConfig.isRagEnabled() || vectorStore == null) {
            return message;
        }

        try {
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(message)
                    .topK(ragMaxDocuments)
                    .similarityThreshold(ragSimilarityThreshold)
                    .filterExpression("workspaceId == '" + workspaceId + "'")
                    .build();

            List<Document> docs = vectorStore.similaritySearch(searchRequest);

            if (docs == null || docs.isEmpty()) {
                return message;
            }

            StringBuilder sb = new StringBuilder("【参考资料】\n");
            for (Document doc : docs) {
                Object docType = doc.getMetadata().get("documentType");
                sb.append("- [").append(docType != null ? docType : "document").append("] ");
                sb.append(doc.getFormattedContent()).append("\n");
            }
            sb.append("\n【用户问题】\n").append(message);

            log.debug("RAG augmented with {} documents (threshold: {})", docs.size(), ragSimilarityThreshold);
            return sb.toString();

        } catch (Exception e) {
            log.warn("RAG augmentation failed: {}", e.getMessage());
            return message;
        }
    }

    /**
     * 构建上下文信息文本块（不含用户消息）。
     * 包含：作用域、剧本/章节概要、锚点实体名称、已有实体概况。
     * 基础部分按 (scriptId, episodeId) 缓存 60s；锚点实体名称按 (type, id) 独立缓存 120s。
     *
     * @return 上下文信息文本，无内容时返回 null
     */
    private String buildContextInfo(AgentSessionEntity sessionEntity) {
        if (sessionEntity.getScriptId() == null) {
            return null;
        }

        String episodeId = resolveEpisodeId(sessionEntity);
        String cacheKey = sessionEntity.getScriptId() + ":" + (episodeId != null ? episodeId : "");

        // 基础上下文（script + episode + entity counts）从缓存获取
        String baseContext = contextInfoCache.get(cacheKey, k -> {
            StringBuilder contextInfo = new StringBuilder("【当前工作上下文】\n");
            contextInfo.append("- 作用域: SCRIPT\n");
            appendScriptContext(contextInfo, sessionEntity.getScriptId());
            appendEpisodeContext(contextInfo, episodeId);
            appendEntitySummary(contextInfo, sessionEntity.getScriptId());
            return contextInfo.toString();
        });

        // 追加锚点实体信息（当用户从特定实体页面发起对话时）
        String anchorContext = buildAnchorEntityContext(sessionEntity);
        if (anchorContext != null) {
            return baseContext + anchorContext;
        }
        return baseContext;
    }

    /**
     * 从 session 或 AgentContext 解析 episodeId。
     */
    private String resolveEpisodeId(AgentSessionEntity sessionEntity) {
        String episodeId = sessionEntity.getEpisodeId();
        if (episodeId == null) {
            AgentContext agentContext = AgentContextHolder.getContext();
            if (agentContext != null) {
                episodeId = agentContext.getEpisodeId();
            }
        }
        return episodeId;
    }

    /**
     * 构建锚点实体上下文（当前聚焦的角色/场景/道具/分镜等）。
     * 从 AgentContext 获取锚点 ID，通过 Feign + 缓存解析名称。
     */
    private String buildAnchorEntityContext(AgentSessionEntity sessionEntity) {
        AgentContext ctx = AgentContextHolder.getContext();
        StringBuilder sb = new StringBuilder();

        // 从 session 和 AgentContext 双重取锚点
        appendAnchorEntity(sb, "当前聚焦角色",
                firstNonNull(sessionEntity.getCharacterId(), ctx != null ? ctx.getCharacterId() : null),
                "character");
        appendAnchorEntity(sb, "当前聚焦场景",
                firstNonNull(sessionEntity.getSceneId(), ctx != null ? ctx.getSceneId() : null),
                "scene");
        appendAnchorEntity(sb, "当前聚焦道具",
                firstNonNull(sessionEntity.getPropId(), ctx != null ? ctx.getPropId() : null),
                "prop");
        appendAnchorEntity(sb, "当前聚焦分镜",
                firstNonNull(sessionEntity.getStoryboardId(), ctx != null ? ctx.getStoryboardId() : null),
                "storyboard");
        appendAnchorEntity(sb, "当前聚焦风格",
                firstNonNull(sessionEntity.getStyleId(), ctx != null ? ctx.getStyleId() : null),
                "style");

        return sb.isEmpty() ? null : sb.toString();
    }

    private void appendAnchorEntity(StringBuilder sb, String label, String entityId, String entityType) {
        if (entityId == null) return;
        String name = resolveEntityName(entityType, entityId);
        if (name == null) return; // 无法解析的实体不注入上下文，避免无意义的"未知"信息
        sb.append("- ").append(label).append(": ").append(name);
        sb.append(" (ID: ").append(entityId).append(")\n");
    }

    /**
     * 通过 Feign 解析实体名称，结果缓存 120s。
     */
    private String resolveEntityName(String entityType, String entityId) {
        return entityNameCache.get(entityType + ":" + entityId, k -> {
            try {
                Result<Map<String, Object>> result = switch (entityType) {
                    case "character" -> projectFeignClient.getCharacter(entityId);
                    case "scene" -> projectFeignClient.getScene(entityId);
                    case "prop" -> projectFeignClient.getProp(entityId);
                    case "storyboard" -> projectFeignClient.getStoryboard(entityId);
                    case "style" -> projectFeignClient.getStyle(entityId);
                    default -> null;
                };
                if (result != null && result.isSuccess() && result.getData() != null) {
                    Object name = result.getData().get("name");
                    if (name == null) name = result.getData().get("title");
                    return name != null ? name.toString() : null;
                }
            } catch (Exception e) {
                log.debug("Failed to resolve {} name for id {}: {}", entityType, entityId, e.getMessage());
            }
            return null;
        });
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }

    /**
     * 注入剧本名称与概要
     */
    private void appendScriptContext(StringBuilder sb, String scriptId) {
        String scriptName = null;
        String scriptSynopsis = null;
        AgentContext agentContext = AgentContextHolder.getContext();
        if (agentContext != null) {
            scriptName = agentContext.getScriptName();
        }
        try {
            Result<Map<String, Object>> result = projectFeignClient.getScript(scriptId);
            if (result != null && result.isSuccess() && result.getData() != null) {
                Map<String, Object> data = result.getData();
                if (scriptName == null) {
                    Object title = data.get("title");
                    if (title == null) title = data.get("name");
                    scriptName = title != null ? title.toString() : null;
                }
                Object synopsis = data.get("synopsis");
                if (synopsis != null && !synopsis.toString().isBlank()) {
                    scriptSynopsis = synopsis.toString();
                }
            }
        } catch (Exception e) {
            log.debug("Failed to query script details for {}: {}", scriptId, e.getMessage());
        }

        sb.append("- 当前剧本: ").append(scriptName != null ? scriptName : "未知");
        sb.append(" (ID: ").append(scriptId).append(")\n");
        if (scriptSynopsis != null) {
            sb.append("- 剧本概要: ").append(truncate(scriptSynopsis, 200)).append("\n");
        }
    }

    /**
     * 注入当前章节标题与概要
     */
    private void appendEpisodeContext(StringBuilder sb, String episodeId) {
        if (episodeId == null) return;

        try {
            Result<Map<String, Object>> result = projectFeignClient.getEpisode(episodeId);
            if (result != null && result.isSuccess() && result.getData() != null) {
                Map<String, Object> data = result.getData();
                Object title = data.get("title");
                Object synopsis = data.get("synopsis");
                if (title != null) {
                    sb.append("- 当前章节: ").append(title).append("\n");
                }
                if (synopsis != null && !synopsis.toString().isBlank()) {
                    sb.append("- 章节概要: ").append(truncate(synopsis.toString(), 300)).append("\n");
                }
            }
        } catch (Exception e) {
            log.debug("Failed to query episode details for {}: {}", episodeId, e.getMessage());
        }
    }

    /**
     * 注入当前剧本下已有实体概况（角色、场景、道具数量及名称概览）
     */
    private void appendEntitySummary(StringBuilder sb, String scriptId) {
        try {
            StringBuilder entitySummary = new StringBuilder();
            appendEntityCount(entitySummary, "角色",
                    projectFeignClient.queryCharacters(null, scriptId, null, null, null, 1, 1, null, null));
            appendEntityCount(entitySummary, "场景",
                    projectFeignClient.queryScenes(null, scriptId, null, null, 1, 1, null, null));
            appendEntityCount(entitySummary, "道具",
                    projectFeignClient.queryProps(null, scriptId, null, null, 1, 1, null, null));
            if (!entitySummary.isEmpty()) {
                sb.append("- 已有实体: ").append(entitySummary).append("\n");
            }
        } catch (Exception e) {
            log.debug("Failed to query entity summary for script {}: {}", scriptId, e.getMessage());
        }
    }

    private void appendEntityCount(StringBuilder sb, String label, Result<Map<String, Object>> result) {
        if (result != null && result.isSuccess() && result.getData() != null) {
            Object total = result.getData().get("total");
            if (total != null) {
                long count = total instanceof Number ? ((Number) total).longValue() : 0L;
                if (count > 0) {
                    if (!sb.isEmpty()) sb.append(", ");
                    sb.append(label).append(" ").append(count).append(" 个");
                }
            }
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
}
