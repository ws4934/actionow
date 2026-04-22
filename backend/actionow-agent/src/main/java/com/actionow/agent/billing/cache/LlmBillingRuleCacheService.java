package com.actionow.agent.billing.cache;

import com.actionow.agent.billing.entity.LlmBillingRule;
import com.actionow.agent.billing.mapper.LlmBillingRuleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * LLM 计费规则缓存服务
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmBillingRuleCacheService {

    private static final String CACHE_KEY_PREFIX = "llm:billing:rule:";
    private static final String CACHE_KEY_EFFECTIVE_PREFIX = "llm:billing:rule:effective:";
    private static final String CACHE_KEY_ALL_EFFECTIVE = "llm:billing:rule:all:effective";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    private final RedisTemplate<String, Object> redisTemplate;
    private final LlmBillingRuleMapper mapper;

    /**
     * 获取 LLM Provider 当前有效的计费规则（带缓存）
     */
    public Optional<LlmBillingRule> getEffective(String llmProviderId) {
        String cacheKey = CACHE_KEY_EFFECTIVE_PREFIX + llmProviderId;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return Optional.of((LlmBillingRule) cached);
        }

        LlmBillingRule rule = mapper.selectEffectiveRule(llmProviderId);
        if (rule != null) {
            redisTemplate.opsForValue().set(cacheKey, rule, CACHE_TTL);
            return Optional.of(rule);
        }
        return Optional.empty();
    }

    /**
     * 获取所有当前有效的计费规则（带缓存）
     */
    @SuppressWarnings("unchecked")
    public List<LlmBillingRule> getAllEffective() {
        Object cached = redisTemplate.opsForValue().get(CACHE_KEY_ALL_EFFECTIVE);
        if (cached != null) {
            return (List<LlmBillingRule>) cached;
        }

        List<LlmBillingRule> rules = mapper.selectAllEffective();
        redisTemplate.opsForValue().set(CACHE_KEY_ALL_EFFECTIVE, rules, CACHE_TTL);
        return rules;
    }

    /**
     * 刷新指定 Provider 的缓存
     */
    public void refresh(String llmProviderId) {
        String cacheKey = CACHE_KEY_EFFECTIVE_PREFIX + llmProviderId;
        redisTemplate.delete(cacheKey);
        log.info("刷新 LLM 计费规则缓存: llmProviderId={}", llmProviderId);
    }

    /**
     * 刷新所有缓存
     */
    public void refreshAll() {
        redisTemplate.delete(CACHE_KEY_ALL_EFFECTIVE);
        log.info("刷新所有 LLM 计费规则缓存");
    }

    /**
     * 删除缓存
     */
    public void evict(String llmProviderId) {
        String cacheKey = CACHE_KEY_EFFECTIVE_PREFIX + llmProviderId;
        redisTemplate.delete(cacheKey);
        refreshAll();
    }
}
