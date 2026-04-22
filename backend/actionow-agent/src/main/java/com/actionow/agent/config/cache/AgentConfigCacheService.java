package com.actionow.agent.config.cache;

import com.actionow.agent.config.dto.AgentConfigResponse;
import com.actionow.agent.config.entity.AgentConfigEntity;
import com.actionow.agent.config.mapper.AgentConfigMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Agent 配置缓存服务
 * 支持热更新检测
 *
 * <h2>序列化格式</h2>
 * 使用 JSON（Jackson）写入 Redis，避免字段增删/类重命名导致旧版本 JDK 序列化的字节无法反序列化。
 * 未知字段由 {@code @JsonIgnoreProperties} 容忍；缺失字段自动为 null / 默认值。
 *
 * <h2>热更新机制</h2>
 * - 使用 Redis 存储全局版本号，本地维护 localCacheVersion
 * - hasUpdates() 检测 Redis 版本是否大于本地版本
 * - 服务启动时从 Redis 同步版本号，避免重启后版本不一致
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentConfigCacheService {

    private static final String CACHE_KEY_PREFIX = "agent:config:";
    private static final String CACHE_KEY_BY_TYPE_PREFIX = "agent:config:type:";
    private static final String CACHE_KEY_ALL_ENABLED = "agent:config:all:enabled";
    private static final String CACHE_KEY_VERSION = "agent:config:cache:version";
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final StringRedisTemplate redisTemplate;
    private final AgentConfigMapper mapper;
    private final ObjectMapper objectMapper;

    private static final TypeReference<List<AgentConfigResponse>> CONFIG_RESPONSE_LIST =
            new TypeReference<>() {};

    // 本地缓存版本号，用于热更新检测
    private final AtomicLong localCacheVersion = new AtomicLong(0);

    /**
     * 服务启动时从 Redis 同步版本号
     * 解决服务重启后 localCacheVersion 为 0 导致的版本不一致问题
     */
    @PostConstruct
    public void init() {
        try {
            String remoteVersion = redisTemplate.opsForValue().get(CACHE_KEY_VERSION);
            if (remoteVersion != null) {
                long version = Long.parseLong(remoteVersion);
                localCacheVersion.set(version);
                log.info("Agent 配置缓存服务初始化，从 Redis 同步版本号: {}", version);
            } else {
                redisTemplate.opsForValue().set(CACHE_KEY_VERSION, "0");
                log.info("Agent 配置缓存服务初始化，Redis 版本号不存在，初始化为 0");
            }
        } catch (Exception e) {
            log.warn("初始化缓存版本号失败，使用默认值 0: {}", e.getMessage());
        }
    }

    /**
     * 获取单个配置（带缓存）
     */
    public Optional<AgentConfigEntity> get(String id) {
        String cacheKey = CACHE_KEY_PREFIX + id;
        AgentConfigEntity cached = readJson(cacheKey, AgentConfigEntity.class);
        if (cached != null) {
            return Optional.of(cached);
        }

        AgentConfigEntity config = mapper.selectById(id);
        if (config != null && config.getDeleted() == 0) {
            writeJson(cacheKey, config);
            return Optional.of(config);
        }
        return Optional.empty();
    }

    /**
     * 根据 Agent 类型获取配置（带缓存）
     */
    public Optional<AgentConfigEntity> getByType(String agentType) {
        String cacheKey = CACHE_KEY_BY_TYPE_PREFIX + agentType;
        AgentConfigEntity cached = readJson(cacheKey, AgentConfigEntity.class);
        if (cached != null) {
            return Optional.of(cached);
        }

        AgentConfigEntity config = mapper.selectByAgentType(agentType);
        if (config != null) {
            writeJson(cacheKey, config);
            return Optional.of(config);
        }
        return Optional.empty();
    }

    /**
     * 获取所有启用的配置（带缓存）
     */
    public List<AgentConfigResponse> getAllEnabled() {
        List<AgentConfigResponse> cached = readJson(CACHE_KEY_ALL_ENABLED, CONFIG_RESPONSE_LIST);
        if (cached != null) {
            return cached;
        }

        List<AgentConfigEntity> configs = mapper.selectAllEnabled();
        List<AgentConfigResponse> responses = configs.stream()
                .map(AgentConfigResponse::fromEntity)
                .collect(Collectors.toList());
        writeJson(CACHE_KEY_ALL_ENABLED, responses);
        return responses;
    }

    /**
     * 检查是否有更新
     */
    public boolean hasUpdates() {
        String remoteVersion = redisTemplate.opsForValue().get(CACHE_KEY_VERSION);
        if (remoteVersion == null) {
            return false;
        }
        long remote = Long.parseLong(remoteVersion);
        return remote > localCacheVersion.get();
    }

    /**
     * 同步本地版本号
     */
    public void syncVersion() {
        String remoteVersion = redisTemplate.opsForValue().get(CACHE_KEY_VERSION);
        if (remoteVersion != null) {
            localCacheVersion.set(Long.parseLong(remoteVersion));
        }
    }

    /**
     * 获取当前 Redis 版本号
     * 用于双重版本检查机制
     */
    public long getCurrentRemoteVersion() {
        String remoteVersion = redisTemplate.opsForValue().get(CACHE_KEY_VERSION);
        if (remoteVersion != null) {
            return Long.parseLong(remoteVersion);
        }
        return 0L;
    }

    /**
     * 获取本地缓存版本号
     */
    public long getLocalVersion() {
        return localCacheVersion.get();
    }

    /**
     * 增加缓存版本号（触发热更新）
     */
    public void incrementVersion() {
        redisTemplate.opsForValue().increment(CACHE_KEY_VERSION);
        log.info("Agent 配置缓存版本号已增加");
    }

    /**
     * 刷新单个配置缓存
     */
    public void refresh(String id) {
        String cacheKey = CACHE_KEY_PREFIX + id;
        redisTemplate.delete(cacheKey);

        AgentConfigEntity config = mapper.selectById(id);
        if (config != null) {
            String typeKey = CACHE_KEY_BY_TYPE_PREFIX + config.getAgentType();
            redisTemplate.delete(typeKey);
        }

        log.info("刷新 Agent 配置缓存: id={}", id);
    }

    /**
     * 刷新指定类型的缓存
     */
    public void refreshByType(String agentType) {
        String cacheKey = CACHE_KEY_BY_TYPE_PREFIX + agentType;
        redisTemplate.delete(cacheKey);
        log.info("刷新 Agent 配置缓存: agentType={}", agentType);
    }

    /**
     * 刷新所有缓存
     */
    public void refreshAll() {
        redisTemplate.delete(CACHE_KEY_ALL_ENABLED);
        incrementVersion();
        log.info("刷新所有 Agent 配置缓存");
    }

    /**
     * 删除缓存
     */
    public void evict(String id, String agentType) {
        refresh(id);
        if (agentType != null) {
            refreshByType(agentType);
        }
        refreshAll();
    }

    /**
     * 缓存单个配置
     */
    public void cache(AgentConfigEntity config) {
        if (config == null) {
            return;
        }
        writeJson(CACHE_KEY_PREFIX + config.getId(), config);
        writeJson(CACHE_KEY_BY_TYPE_PREFIX + config.getAgentType(), config);
    }

    // ==================== JSON 读写辅助 ====================

    private <T> T readJson(String key, Class<T> type) {
        String raw = redisTemplate.opsForValue().get(key);
        if (raw == null || raw.isEmpty()) return null;
        try {
            return objectMapper.readValue(raw, type);
        } catch (JsonProcessingException e) {
            log.warn("Redis JSON 反序列化失败 key={} type={}，自动清理: {}", key, type.getSimpleName(), e.getMessage());
            redisTemplate.delete(key);
            return null;
        }
    }

    private <T> T readJson(String key, TypeReference<T> typeRef) {
        String raw = redisTemplate.opsForValue().get(key);
        if (raw == null || raw.isEmpty()) return null;
        try {
            return objectMapper.readValue(raw, typeRef);
        } catch (JsonProcessingException e) {
            log.warn("Redis JSON 反序列化失败 key={}，自动清理: {}", key, e.getMessage());
            redisTemplate.delete(key);
            return null;
        }
    }

    private void writeJson(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), CACHE_TTL);
        } catch (JsonProcessingException e) {
            log.warn("Redis JSON 序列化失败 key={}: {}", key, e.getMessage());
        }
    }
}
