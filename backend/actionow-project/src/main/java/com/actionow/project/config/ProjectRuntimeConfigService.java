package com.actionow.project.config;

import com.actionow.common.redis.config.RuntimeConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Project 模块运行时配置服务
 * 管理素材回收站清理、版本清理等核心运行参数
 *
 * @author Actionow
 */
@Slf4j
@Component
public class ProjectRuntimeConfigService extends RuntimeConfigService {

    public static final String TRASH_RETENTION_DAYS         = "runtime.project.trash_retention_days";
    public static final String TRASH_AUTO_CLEANUP_ENABLED   = "runtime.project.trash_auto_cleanup_enabled";
    public static final String VERSION_MAX_KEEP_COUNT       = "runtime.project.version_max_keep_count";
    public static final String VERSION_CLEANUP_THRESHOLD    = "runtime.project.version_cleanup_threshold";
    public static final String VERSION_CLEANUP_BATCH_SIZE   = "runtime.project.version_cleanup_batch_size";

    public ProjectRuntimeConfigService(StringRedisTemplate redisTemplate,
                                        RedisMessageListenerContainer listenerContainer) {
        super(redisTemplate, listenerContainer);
    }

    @Override
    protected String getPrefix() {
        return "runtime.project";
    }

    @Override
    protected void registerDefaults(Map<String, String> defaults) {
        defaults.put(TRASH_RETENTION_DAYS, "30");
        defaults.put(TRASH_AUTO_CLEANUP_ENABLED, "true");
        defaults.put(VERSION_MAX_KEEP_COUNT, "50");
        defaults.put(VERSION_CLEANUP_THRESHOLD, "60");
        defaults.put(VERSION_CLEANUP_BATCH_SIZE, "100");
    }

    public int getTrashRetentionDays() {
        return getInt(TRASH_RETENTION_DAYS);
    }

    public boolean isTrashAutoCleanupEnabled() {
        return getBoolean(TRASH_AUTO_CLEANUP_ENABLED);
    }

    public int getVersionMaxKeepCount() {
        return getInt(VERSION_MAX_KEEP_COUNT);
    }

    public int getVersionCleanupThreshold() {
        return getInt(VERSION_CLEANUP_THRESHOLD);
    }

    public int getVersionCleanupBatchSize() {
        return getInt(VERSION_CLEANUP_BATCH_SIZE);
    }
}
