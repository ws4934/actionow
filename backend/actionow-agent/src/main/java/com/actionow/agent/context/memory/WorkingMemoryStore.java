package com.actionow.agent.context.memory;

import com.actionow.agent.metrics.AgentMetrics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session-scoped Working Memory（工作记忆）
 * <p>
 * 借鉴 SAA 的 OverAllState / MemoryStore 设计：
 * 为每个 Session 维护一份结构化的键值存储，供 Agent 在上下文被压缩后仍能取回关键参考数据。
 * <p>
 * 存储架构：ConcurrentHashMap（L1 内存缓存） + Redis Hash（L2 持久化）。
 * <ul>
 *   <li>写入时 write-through：同时写入内存和 Redis</li>
 *   <li>读取时 read-through：内存优先，miss 时从 Redis 加载整个 session 并回填内存</li>
 *   <li>Redis 不可用时自动降级为纯内存模式（无需改动调用方）</li>
 *   <li>Redis key TTL = 24h，自动清理孤儿 session</li>
 * </ul>
 *
 * @author Actionow
 */
@Slf4j
@Component
public class WorkingMemoryStore {

    /** L1: sessionId → memory entries (ordered by insertion) */
    private final Map<String, Map<String, MemoryEntry>> sessionMemories = new ConcurrentHashMap<>();

    /** 每个 session 最大 entry 数 */
    private static final int MAX_ENTRIES_PER_SESSION = 30;

    /** 单条目最大字符数（超过此限制的数据截断存储，防止内存膨胀） */
    private static final int MAX_ENTRY_CHARS = 50_000;

    /** Redis Hash key 前缀 */
    private static final String REDIS_KEY_PREFIX = "wm:session:";

    /** Redis Hash TTL（自动清理孤儿 session） */
    private static final Duration REDIS_TTL = Duration.ofHours(24);

    @Nullable
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AgentMetrics agentMetrics;

    @Autowired
    public WorkingMemoryStore(@Autowired(required = false) @Nullable StringRedisTemplate redisTemplate,
                              ObjectMapper objectMapper,
                              AgentMetrics agentMetrics) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.agentMetrics = agentMetrics;
        if (redisTemplate != null) {
            log.info("WorkingMemoryStore initialized with Redis persistence (TTL={})", REDIS_TTL);
        } else {
            log.info("WorkingMemoryStore initialized in-memory only (no Redis available)");
        }
    }

    /**
     * 存储一条工作记忆。
     *
     * @param sessionId 会话 ID
     * @param key       键（如 "character_list", "pdf_content"）
     * @param value     值（结构化数据或原始文本）
     * @param source    来源工具名
     * @param pinned    是否固定（不被 LRU 淘汰）
     */
    public void put(String sessionId, String key, String value, @Nullable String source, boolean pinned) {
        if (sessionId == null || key == null || value == null) {
            return;
        }

        Map<String, MemoryEntry> entries = sessionMemories.computeIfAbsent(
                sessionId, k -> Collections.synchronizedMap(new LinkedHashMap<>()));

        // 截断过大的数据，防止内存膨胀
        String storedValue = value;
        boolean truncated = false;
        if (value.length() > MAX_ENTRY_CHARS) {
            storedValue = value.substring(0, MAX_ENTRY_CHARS) + "\n... [截断，原始长度 " + value.length() + " chars]";
            truncated = true;
            log.warn("Working memory entry truncated: key={}, original={}chars, max={}",
                    key, value.length(), MAX_ENTRY_CHARS);
        }

        MemoryEntry entry = MemoryEntry.builder()
                .key(key)
                .value(storedValue)
                .source(source)
                .pinned(pinned)
                .createdAt(System.currentTimeMillis())
                .charCount(value.length())
                .truncated(truncated)
                .build();

        entries.put(key, entry);
        evictIfNeeded(entries);

        // Write-through to Redis
        persistToRedis(sessionId, key, entry);
        agentMetrics.recordWorkingMemoryPut();

        log.debug("Working memory stored: sessionId={}, key={}, source={}, pinned={}, chars={}",
                sessionId, key, source, pinned, value.length());
    }

    /**
     * 获取一条工作记忆。
     *
     * @return 记忆值，不存在时返回 null
     */
    @Nullable
    public MemoryEntry get(String sessionId, String key) {
        if (sessionId == null || key == null) {
            return null;
        }

        // L1: in-memory
        Map<String, MemoryEntry> entries = sessionMemories.get(sessionId);
        if (entries != null) {
            MemoryEntry result = entries.get(key);
            agentMetrics.recordWorkingMemoryGet(result != null);
            return result;
        }

        // L2: try loading entire session from Redis
        entries = loadSessionFromRedis(sessionId);
        if (entries != null) {
            MemoryEntry result = entries.get(key);
            agentMetrics.recordWorkingMemoryGet(result != null);
            return result;
        }

        agentMetrics.recordWorkingMemoryGet(false);
        return null;
    }

    /**
     * 列出当前 session 的所有工作记忆条目摘要。
     */
    public List<MemoryEntry> list(String sessionId) {
        if (sessionId == null) {
            return List.of();
        }

        Map<String, MemoryEntry> entries = sessionMemories.get(sessionId);
        if (entries == null) {
            // L2: try loading from Redis
            entries = loadSessionFromRedis(sessionId);
        }
        if (entries == null) {
            return List.of();
        }
        synchronized (entries) {
            return new ArrayList<>(entries.values());
        }
    }

    /**
     * 删除一条工作记忆。
     */
    public boolean remove(String sessionId, String key) {
        if (sessionId == null || key == null) return false;
        Map<String, MemoryEntry> entries = sessionMemories.get(sessionId);
        if (entries == null) return false;
        MemoryEntry removed = entries.remove(key);
        if (removed != null) {
            removeFromRedis(sessionId, key);
            log.debug("Working memory removed: sessionId={}, key={}", sessionId, key);
        }
        return removed != null;
    }

    /**
     * 清理指定 session 的全部工作记忆。
     */
    public void clearSession(String sessionId) {
        if (sessionId != null) {
            sessionMemories.remove(sessionId);
            deleteSessionFromRedis(sessionId);
            log.debug("Working memory cleared for session: {}", sessionId);
        }
    }

    /**
     * 获取 session 的记忆条目数。
     */
    public int size(String sessionId) {
        Map<String, MemoryEntry> entries = sessionMemories.get(sessionId);
        return entries != null ? entries.size() : 0;
    }

    /**
     * 生成工作记忆索引摘要（用于注入 System Message）。
     * 仅包含 key、source、char count，不包含完整 value。
     */
    @Nullable
    public String buildIndexSummary(String sessionId) {
        List<MemoryEntry> entries = list(sessionId);
        if (entries.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("## 工作记忆 (Working Memory)\n");
        sb.append("以下数据已缓存，可通过 recall_from_memory(key=\"<key>\") 获取完整内容：\n");
        for (MemoryEntry entry : entries) {
            sb.append(String.format("- **%s** (来源: %s, %d chars%s)\n",
                    entry.getKey(),
                    entry.getSource() != null ? entry.getSource() : "manual",
                    entry.getCharCount(),
                    entry.isPinned() ? ", 📌已固定" : ""));
        }
        return sb.toString();
    }

    // ==================== Redis 持久化 ====================

    private void persistToRedis(String sessionId, String key, MemoryEntry entry) {
        if (redisTemplate == null) return;
        try {
            String json = objectMapper.writeValueAsString(entry);
            String redisKey = REDIS_KEY_PREFIX + sessionId;
            redisTemplate.opsForHash().put(redisKey, key, json);
            redisTemplate.expire(redisKey, REDIS_TTL);
        } catch (JsonProcessingException e) {
            log.warn("Failed to persist working memory to Redis: sessionId={}, key={}: {}",
                    sessionId, key, e.getMessage());
        } catch (Exception e) {
            log.warn("Redis write failed (degrading to in-memory only): {}", e.getMessage());
        }
    }

    /**
     * 从 Redis 加载整个 session 的工作记忆并回填内存缓存。
     *
     * @return 加载后的 entries map，如果 Redis 中无数据或不可用则返回 null
     */
    @Nullable
    private Map<String, MemoryEntry> loadSessionFromRedis(String sessionId) {
        if (redisTemplate == null) return null;
        try {
            String redisKey = REDIS_KEY_PREFIX + sessionId;
            Map<Object, Object> rawEntries = redisTemplate.opsForHash().entries(redisKey);
            if (rawEntries.isEmpty()) {
                return null;
            }

            Map<String, MemoryEntry> entries = Collections.synchronizedMap(new LinkedHashMap<>());
            for (Map.Entry<Object, Object> raw : rawEntries.entrySet()) {
                try {
                    MemoryEntry entry = objectMapper.readValue((String) raw.getValue(), MemoryEntry.class);
                    entries.put((String) raw.getKey(), entry);
                } catch (JsonProcessingException e) {
                    log.warn("Failed to deserialize working memory entry: key={}", raw.getKey());
                }
            }

            if (!entries.isEmpty()) {
                sessionMemories.put(sessionId, entries);
                log.debug("Loaded {} working memory entries from Redis for session: {}",
                        entries.size(), sessionId);
            }
            return entries;
        } catch (Exception e) {
            log.warn("Redis read failed (using in-memory only): {}", e.getMessage());
            return null;
        }
    }

    private void removeFromRedis(String sessionId, String key) {
        if (redisTemplate == null) return;
        try {
            redisTemplate.opsForHash().delete(REDIS_KEY_PREFIX + sessionId, key);
        } catch (Exception e) {
            log.warn("Redis delete failed: {}", e.getMessage());
        }
    }

    private void deleteSessionFromRedis(String sessionId) {
        if (redisTemplate == null) return;
        try {
            redisTemplate.delete(REDIS_KEY_PREFIX + sessionId);
        } catch (Exception e) {
            log.warn("Redis session delete failed: {}", e.getMessage());
        }
    }

    // ==================== LRU 淘汰 ====================

    private void evictIfNeeded(Map<String, MemoryEntry> entries) {
        synchronized (entries) {
            while (entries.size() > MAX_ENTRIES_PER_SESSION) {
                String oldestKey = null;
                long oldestTime = Long.MAX_VALUE;
                for (Map.Entry<String, MemoryEntry> e : entries.entrySet()) {
                    if (!e.getValue().isPinned() && e.getValue().getCreatedAt() < oldestTime) {
                        oldestTime = e.getValue().getCreatedAt();
                        oldestKey = e.getKey();
                    }
                }
                if (oldestKey != null) {
                    entries.remove(oldestKey);
                    log.debug("Working memory evicted (LRU): key={}", oldestKey);
                } else {
                    break;
                }
            }
        }
    }

    /**
     * 工作记忆条目
     */
    @Data
    @Builder
    public static class MemoryEntry {
        private String key;
        private String value;
        @Nullable
        private String source;
        private boolean pinned;
        private boolean truncated;
        private long createdAt;
        /** 原始数据长度（截断前） */
        private int charCount;
    }
}
