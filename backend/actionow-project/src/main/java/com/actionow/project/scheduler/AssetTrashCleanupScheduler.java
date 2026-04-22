package com.actionow.project.scheduler;

import com.actionow.common.core.context.UserContext;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.redis.lock.DistributedLockService;
import com.actionow.project.config.ProjectRuntimeConfigService;
import com.actionow.project.mapper.WorkspaceSchemaMapper;
import com.actionow.project.service.AssetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 素材回收站清理定时任务
 * 定期清理超过保留期的已删除素材
 * 遍历所有活跃工作空间进行清理
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssetTrashCleanupScheduler {

    private static final String GLOBAL_LOCK_KEY = "lock:asset:trash:cleanup:global";
    private static final String WORKSPACE_LOCK_PREFIX = "lock:asset:trash:cleanup:workspace:";
    private static final long GLOBAL_LEASE_MINUTES = 30L;
    private static final long WORKSPACE_LEASE_MINUTES = 10L;

    private final AssetService assetService;
    private final WorkspaceSchemaMapper workspaceSchemaMapper;
    private final ProjectRuntimeConfigService projectRuntimeConfig;
    private final DistributedLockService distributedLockService;

    /**
     * 每天凌晨3点执行清理任务
     * 遍历所有活跃工作空间进行清理
     */
    @Scheduled(cron = "${actionow.asset.trash.cleanup-cron:0 0 3 * * ?}")
    public void cleanupExpiredTrash() {
        if (!projectRuntimeConfig.isTrashAutoCleanupEnabled()) {
            log.debug("素材回收站自动清理已禁用");
            return;
        }

        int retentionDays = projectRuntimeConfig.getTrashRetentionDays();
        log.info("开始清理过期的回收站素材，保留天数: {}", retentionDays);

        try {
            int totalCleanedCount = cleanupAllWorkspaces(retentionDays);
            if (totalCleanedCount > 0) {
                log.info("回收站清理完成，共清理 {} 个过期素材", totalCleanedCount);
            } else {
                log.debug("回收站清理完成，无过期素材");
            }
        } catch (Exception e) {
            log.error("回收站清理任务执行失败", e);
        }
    }

    /**
     * 手动触发清理（供内部API调用）
     * 遍历所有活跃工作空间进行清理
     *
     * @return 清理的素材数量
     */
    public int triggerCleanup() {
        int retentionDays = projectRuntimeConfig.getTrashRetentionDays();
        log.info("手动触发回收站清理，保留天数: {}", retentionDays);
        return cleanupAllWorkspaces(retentionDays);
    }

    /**
     * 手动触发清理（指定保留天数）
     * 遍历所有活跃工作空间进行清理
     *
     * @param days 保留天数
     * @return 清理的素材数量
     */
    public int triggerCleanup(int days) {
        log.info("手动触发回收站清理，保留天数: {}", days);
        return cleanupAllWorkspaces(days);
    }

    /**
     * 遍历所有活跃工作空间进行清理
     *
     * @param days 保留天数
     * @return 清理的素材总数
     */
    private int cleanupAllWorkspaces(int days) {
        // 全局锁：保证同一时刻集群内只有一个实例执行整体清理
        boolean acquired = distributedLockService.tryLock(
                GLOBAL_LOCK_KEY, 0L, GLOBAL_LEASE_MINUTES, TimeUnit.MINUTES);
        if (!acquired) {
            log.info("其他实例正在执行回收站清理，跳过本次触发");
            return 0;
        }

        try {
            List<String> schemas = workspaceSchemaMapper.selectAllActiveSchemas();
            if (schemas == null || schemas.isEmpty()) {
                log.debug("没有活跃的工作空间需要清理");
                return 0;
            }

            log.debug("开始清理 {} 个工作空间的回收站", schemas.size());

            int totalCleanedCount = 0;
            for (String schemaName : schemas) {
                try {
                    int cleanedCount = cleanupWorkspace(schemaName, days);
                    totalCleanedCount += cleanedCount;
                    if (cleanedCount > 0) {
                        log.debug("工作空间 {} 清理了 {} 个过期素材", schemaName, cleanedCount);
                    }
                } catch (Exception e) {
                    log.error("清理工作空间 {} 的回收站失败: {}", schemaName, e.getMessage());
                }
            }

            return totalCleanedCount;
        } finally {
            distributedLockService.unlock(GLOBAL_LOCK_KEY);
        }
    }

    /**
     * 清理指定工作空间的回收站
     *
     * @param schemaName 租户Schema名称
     * @param days       保留天数
     * @return 清理的素材数量
     */
    private int cleanupWorkspace(String schemaName, int days) {
        String workspaceLockKey = WORKSPACE_LOCK_PREFIX + schemaName;
        boolean acquired = distributedLockService.tryLock(
                workspaceLockKey, 0L, WORKSPACE_LEASE_MINUTES, TimeUnit.MINUTES);
        if (!acquired) {
            log.info("工作空间 {} 的回收站清理已在其他实例执行，跳过", schemaName);
            return 0;
        }

        UserContext context = new UserContext();
        context.setTenantSchema(schemaName);
        context.setUserId("SYSTEM");
        UserContextHolder.setContext(context);

        try {
            return assetService.cleanupExpiredTrash(days);
        } finally {
            UserContextHolder.clear();
            distributedLockService.unlock(workspaceLockKey);
        }
    }
}
