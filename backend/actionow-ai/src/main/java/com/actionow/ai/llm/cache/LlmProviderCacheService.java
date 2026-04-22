package com.actionow.ai.llm.cache;

import com.actionow.ai.llm.dto.LlmProviderResponse;
import com.actionow.ai.llm.entity.LlmProvider;
import com.actionow.ai.llm.mapper.LlmProviderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * LLM Provider 缓存服务
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmProviderCacheService {

    private static final String CACHE_KEY_PREFIX = "llm:provider:";
    private static final String CACHE_KEY_ALL_ENABLED = "llm:provider:all:enabled";
    private static final String CACHE_KEY_BY_PROVIDER_PREFIX = "llm:provider:by-provider:";
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final RedisTemplate<String, Object> redisTemplate;
    private final LlmProviderMapper mapper;

    /**
     * 获取单个 Provider（带缓存）
     */
    public Optional<LlmProvider> get(String id) {
        String cacheKey = CACHE_KEY_PREFIX + id;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return Optional.of((LlmProvider) cached);
        }

        LlmProvider provider = mapper.selectById(id);
        if (provider != null && provider.getDeleted() == 0) {
            redisTemplate.opsForValue().set(cacheKey, provider, CACHE_TTL);
            return Optional.of(provider);
        }
        return Optional.empty();
    }

    /**
     * 获取所有启用的 Provider（带缓存）
     */
    @SuppressWarnings("unchecked")
    public List<LlmProviderResponse> getAllEnabled() {
        Object cached = redisTemplate.opsForValue().get(CACHE_KEY_ALL_ENABLED);
        if (cached != null) {
            return (List<LlmProviderResponse>) cached;
        }

        List<LlmProvider> providers = mapper.selectAllEnabled();
        List<LlmProviderResponse> responses = providers.stream()
                .map(LlmProviderResponse::fromEntity)
                .collect(Collectors.toList());
        redisTemplate.opsForValue().set(CACHE_KEY_ALL_ENABLED, responses, CACHE_TTL);
        return responses;
    }

    /**
     * 获取指定厂商启用的 Provider（带缓存）
     */
    @SuppressWarnings("unchecked")
    public List<LlmProviderResponse> getEnabledByProvider(String provider) {
        String cacheKey = CACHE_KEY_BY_PROVIDER_PREFIX + provider;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return (List<LlmProviderResponse>) cached;
        }

        List<LlmProvider> providers = mapper.selectEnabledByProvider(provider);
        List<LlmProviderResponse> responses = providers.stream()
                .map(LlmProviderResponse::fromEntity)
                .collect(Collectors.toList());
        redisTemplate.opsForValue().set(cacheKey, responses, CACHE_TTL);
        return responses;
    }

    /**
     * 刷新单个 Provider 缓存
     */
    public void refresh(String id) {
        String cacheKey = CACHE_KEY_PREFIX + id;
        redisTemplate.delete(cacheKey);
        log.info("刷新 LLM Provider 缓存: id={}", id);
    }

    /**
     * 刷新所有缓存
     */
    public void refreshAll() {
        // 删除所有相关缓存
        redisTemplate.delete(CACHE_KEY_ALL_ENABLED);
        // 删除厂商缓存
        String[] providers = {"GOOGLE", "OPENAI", "ANTHROPIC", "VOLCENGINE", "ZHIPU", "MOONSHOT", "BAIDU", "ALIBABA"};
        for (String provider : providers) {
            redisTemplate.delete(CACHE_KEY_BY_PROVIDER_PREFIX + provider);
        }
        log.info("刷新所有 LLM Provider 缓存");
    }

    /**
     * 缓存单个 Provider
     */
    public void cache(LlmProvider provider) {
        if (provider == null) {
            return;
        }
        String cacheKey = CACHE_KEY_PREFIX + provider.getId();
        redisTemplate.opsForValue().set(cacheKey, provider, CACHE_TTL);
    }

    /**
     * 删除缓存
     */
    public void evict(String id) {
        String cacheKey = CACHE_KEY_PREFIX + id;
        redisTemplate.delete(cacheKey);
        // 同时刷新列表缓存
        refreshAll();
    }
}
