package com.actionow.ai.plugin.cache;

import com.actionow.ai.entity.ModelProvider;
import com.actionow.ai.plugin.model.PluginConfig;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * 提供商配置缓存
 * 使用Caffeine实现本地缓存，减少数据库查询
 *
 * @author Actionow
 */
@Slf4j
@Component
public class ProviderConfigCache {

    // 默认缓存配置
    private static final int DEFAULT_MAX_SIZE = 1000;
    private static final Duration DEFAULT_EXPIRE_AFTER_WRITE = Duration.ofMinutes(10);
    private static final Duration DEFAULT_EXPIRE_AFTER_ACCESS = Duration.ofMinutes(5);

    // 提供商实体缓存
    private final Cache<String, ModelProvider> providerCache;

    // 插件配置缓存（从提供商实体转换而来）
    private final Cache<String, PluginConfig> pluginConfigCache;

    // 提供商ID到名称的缓存
    private final Cache<String, String> providerNameCache;

    public ProviderConfigCache() {
        this.providerCache = buildCache("provider");
        this.pluginConfigCache = buildCache("pluginConfig");
        this.providerNameCache = buildCache("providerName");
        log.info("ProviderConfigCache initialized with maxSize={}, expireAfterWrite={}, expireAfterAccess={}",
            DEFAULT_MAX_SIZE, DEFAULT_EXPIRE_AFTER_WRITE, DEFAULT_EXPIRE_AFTER_ACCESS);
    }

    /**
     * 构建缓存
     */
    private <V> Cache<String, V> buildCache(String cacheName) {
        return Caffeine.newBuilder()
            .maximumSize(DEFAULT_MAX_SIZE)
            .expireAfterWrite(DEFAULT_EXPIRE_AFTER_WRITE)
            .expireAfterAccess(DEFAULT_EXPIRE_AFTER_ACCESS)
            .recordStats()
            .removalListener((String key, V value, RemovalCause cause) -> {
                if (cause != RemovalCause.REPLACED) {
                    log.debug("Cache [{}] entry removed: key={}, cause={}", cacheName, key, cause);
                }
            })
            .build();
    }

    /**
     * 获取或加载提供商配置
     *
     * @param providerId 提供商ID
     * @param loader 加载函数
     * @return 提供商实体
     */
    public ModelProvider getOrLoadProvider(String providerId, Function<String, ModelProvider> loader) {
        return providerCache.get(providerId, loader);
    }

    /**
     * 获取提供商配置（不加载）
     */
    public Optional<ModelProvider> getProvider(String providerId) {
        return Optional.ofNullable(providerCache.getIfPresent(providerId));
    }

    /**
     * 放入提供商配置
     */
    public void putProvider(String providerId, ModelProvider provider) {
        providerCache.put(providerId, provider);
    }

    /**
     * 获取或加载插件配置
     *
     * @param providerId 提供商ID
     * @param loader 加载函数
     * @return 插件配置
     */
    public PluginConfig getOrLoadPluginConfig(String providerId, Function<String, PluginConfig> loader) {
        return pluginConfigCache.get(providerId, loader);
    }

    /**
     * 获取插件配置（不加载）
     */
    public Optional<PluginConfig> getPluginConfig(String providerId) {
        return Optional.ofNullable(pluginConfigCache.getIfPresent(providerId));
    }

    /**
     * 放入插件配置
     */
    public void putPluginConfig(String providerId, PluginConfig config) {
        pluginConfigCache.put(providerId, config);
    }

    /**
     * 获取或加载提供商名称
     */
    public String getOrLoadProviderName(String providerId, Function<String, String> loader) {
        return providerNameCache.get(providerId, loader);
    }

    /**
     * 失效指定提供商的所有缓存
     */
    public void invalidate(String providerId) {
        providerCache.invalidate(providerId);
        pluginConfigCache.invalidate(providerId);
        providerNameCache.invalidate(providerId);
        log.debug("Invalidated cache for provider: {}", providerId);
    }

    /**
     * 失效所有缓存
     */
    public void invalidateAll() {
        providerCache.invalidateAll();
        pluginConfigCache.invalidateAll();
        providerNameCache.invalidateAll();
        log.info("All provider caches invalidated");
    }

    /**
     * 获取缓存统计信息
     */
    public CacheStats getStats() {
        return new CacheStats(
            new SingleCacheStats(
                providerCache.estimatedSize(),
                providerCache.stats().hitCount(),
                providerCache.stats().missCount(),
                providerCache.stats().hitRate()
            ),
            new SingleCacheStats(
                pluginConfigCache.estimatedSize(),
                pluginConfigCache.stats().hitCount(),
                pluginConfigCache.stats().missCount(),
                pluginConfigCache.stats().hitRate()
            ),
            new SingleCacheStats(
                providerNameCache.estimatedSize(),
                providerNameCache.stats().hitCount(),
                providerNameCache.stats().missCount(),
                providerNameCache.stats().hitRate()
            )
        );
    }

    /**
     * 获取所有缓存的提供商ID
     */
    public ConcurrentMap<String, ModelProvider> getAllCachedProviders() {
        return providerCache.asMap();
    }

    /**
     * 预热缓存
     */
    public void warmUp(Iterable<ModelProvider> providers) {
        int count = 0;
        for (ModelProvider provider : providers) {
            if (provider.getEnabled() != null && provider.getEnabled()) {
                providerCache.put(provider.getId(), provider);
                providerNameCache.put(provider.getId(), provider.getName());
                count++;
            }
        }
        log.info("Cache warmed up with {} providers", count);
    }

    /**
     * 缓存统计信息
     */
    public record CacheStats(
        SingleCacheStats providerCache,
        SingleCacheStats pluginConfigCache,
        SingleCacheStats providerNameCache
    ) {}

    /**
     * 单个缓存的统计信息
     */
    public record SingleCacheStats(
        long size,
        long hitCount,
        long missCount,
        double hitRate
    ) {}
}
