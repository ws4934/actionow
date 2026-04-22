package com.actionow.project.service.version;

import com.actionow.common.core.context.UserContext;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.project.config.ProjectRuntimeConfigService;
import com.actionow.project.mapper.WorkspaceSchemaMapper;
import com.actionow.project.mapper.version.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 版本清理服务
 * 定期清理过多的历史版本，防止版本表无限增长
 * 遍历所有活跃工作空间进行清理
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VersionCleanupService {

    private final WorkspaceSchemaMapper workspaceSchemaMapper;
    private final ScriptVersionMapper scriptVersionMapper;
    private final EpisodeVersionMapper episodeVersionMapper;
    private final StoryboardVersionMapper storyboardVersionMapper;
    private final CharacterVersionMapper characterVersionMapper;
    private final SceneVersionMapper sceneVersionMapper;
    private final PropVersionMapper propVersionMapper;
    private final StyleVersionMapper styleVersionMapper;
    private final AssetVersionMapper assetVersionMapper;
    private final ProjectRuntimeConfigService projectRuntimeConfig;

    /**
     * 定时清理任务 - 每天凌晨3点执行
     * 遍历所有活跃工作空间进行清理
     */
    @Scheduled(cron = "${actionow.version.cleanup-cron:0 0 3 * * ?}")
    public void scheduledCleanup() {
        log.info("开始执行版本清理任务...");

        // 查询所有活跃工作空间的Schema
        List<String> schemas = workspaceSchemaMapper.selectAllActiveSchemas();
        if (schemas == null || schemas.isEmpty()) {
            log.debug("没有活跃的工作空间需要清理");
            return;
        }

        log.info("开始清理 {} 个工作空间的历史版本", schemas.size());

        int totalCleaned = 0;
        for (String schemaName : schemas) {
            try {
                int cleanedCount = cleanupWorkspace(schemaName);
                totalCleaned += cleanedCount;
                if (cleanedCount > 0) {
                    log.debug("工作空间 {} 清理了 {} 个历史版本", schemaName, cleanedCount);
                }
            } catch (Exception e) {
                log.error("清理工作空间 {} 的历史版本失败: {}", schemaName, e.getMessage(), e);
            }
        }

        log.info("版本清理任务完成，共清理 {} 个版本记录", totalCleaned);
    }

    /**
     * 清理指定工作空间的历史版本
     *
     * @param schemaName 租户Schema名称
     * @return 清理的版本数量
     */
    private int cleanupWorkspace(String schemaName) {
        // 设置租户上下文
        UserContext context = new UserContext();
        context.setTenantSchema(schemaName);
        context.setUserId("SYSTEM");
        UserContextHolder.setContext(context);

        try {
            return cleanupAllVersionTables();
        } finally {
            // 清除租户上下文
            UserContextHolder.clear();
        }
    }

    /**
     * 清理所有版本表
     *
     * @return 清理的总记录数
     */
    public int cleanupAllVersionTables() {
        int maxKeepCount = projectRuntimeConfig.getVersionMaxKeepCount();
        int threshold = projectRuntimeConfig.getVersionCleanupThreshold();
        int batchSize = projectRuntimeConfig.getVersionCleanupBatchSize();

        int total = 0;
        total += cleanupScriptVersions(threshold, batchSize, maxKeepCount);
        total += cleanupEpisodeVersions(threshold, batchSize, maxKeepCount);
        total += cleanupStoryboardVersions(threshold, batchSize, maxKeepCount);
        total += cleanupCharacterVersions(threshold, batchSize, maxKeepCount);
        total += cleanupSceneVersions(threshold, batchSize, maxKeepCount);
        total += cleanupPropVersions(threshold, batchSize, maxKeepCount);
        total += cleanupStyleVersions(threshold, batchSize, maxKeepCount);
        total += cleanupAssetVersions(threshold, batchSize, maxKeepCount);
        return total;
    }

    @Transactional(rollbackFor = Exception.class)
    int cleanupScriptVersions(int threshold, int batchSize, int keepCount) {
        int totalCleaned = 0;
        List<String> entityIds;

        do {
            entityIds = scriptVersionMapper.selectScriptIdsNeedingCleanup(threshold, batchSize);
            for (String entityId : entityIds) {
                int deleted = scriptVersionMapper.deleteOldVersions(entityId, keepCount);
                totalCleaned += deleted;
                if (deleted > 0) {
                    log.debug("清理剧本 {} 的历史版本，删除 {} 条记录", entityId, deleted);
                }
            }
        } while (!entityIds.isEmpty());

        if (totalCleaned > 0) {
            log.info("剧本版本清理完成，共删除 {} 条记录", totalCleaned);
        }
        return totalCleaned;
    }

    @Transactional(rollbackFor = Exception.class)
    int cleanupEpisodeVersions(int threshold, int batchSize, int keepCount) {
        int totalCleaned = 0;
        List<String> entityIds;

        do {
            entityIds = episodeVersionMapper.selectEpisodeIdsNeedingCleanup(threshold, batchSize);
            for (String entityId : entityIds) {
                int deleted = episodeVersionMapper.deleteOldVersions(entityId, keepCount);
                totalCleaned += deleted;
                if (deleted > 0) {
                    log.debug("清理剧集 {} 的历史版本，删除 {} 条记录", entityId, deleted);
                }
            }
        } while (!entityIds.isEmpty());

        if (totalCleaned > 0) {
            log.info("剧集版本清理完成，共删除 {} 条记录", totalCleaned);
        }
        return totalCleaned;
    }

    @Transactional(rollbackFor = Exception.class)
    int cleanupStoryboardVersions(int threshold, int batchSize, int keepCount) {
        int totalCleaned = 0;
        List<String> entityIds;

        do {
            entityIds = storyboardVersionMapper.selectStoryboardIdsNeedingCleanup(threshold, batchSize);
            for (String entityId : entityIds) {
                int deleted = storyboardVersionMapper.deleteOldVersions(entityId, keepCount);
                totalCleaned += deleted;
                if (deleted > 0) {
                    log.debug("清理分镜 {} 的历史版本，删除 {} 条记录", entityId, deleted);
                }
            }
        } while (!entityIds.isEmpty());

        if (totalCleaned > 0) {
            log.info("分镜版本清理完成，共删除 {} 条记录", totalCleaned);
        }
        return totalCleaned;
    }

    @Transactional(rollbackFor = Exception.class)
    int cleanupCharacterVersions(int threshold, int batchSize, int keepCount) {
        int totalCleaned = 0;
        List<String> entityIds;

        do {
            entityIds = characterVersionMapper.selectCharacterIdsNeedingCleanup(threshold, batchSize);
            for (String entityId : entityIds) {
                int deleted = characterVersionMapper.deleteOldVersions(entityId, keepCount);
                totalCleaned += deleted;
                if (deleted > 0) {
                    log.debug("清理角色 {} 的历史版本，删除 {} 条记录", entityId, deleted);
                }
            }
        } while (!entityIds.isEmpty());

        if (totalCleaned > 0) {
            log.info("角色版本清理完成，共删除 {} 条记录", totalCleaned);
        }
        return totalCleaned;
    }

    @Transactional(rollbackFor = Exception.class)
    int cleanupSceneVersions(int threshold, int batchSize, int keepCount) {
        int totalCleaned = 0;
        List<String> entityIds;

        do {
            entityIds = sceneVersionMapper.selectSceneIdsNeedingCleanup(threshold, batchSize);
            for (String entityId : entityIds) {
                int deleted = sceneVersionMapper.deleteOldVersions(entityId, keepCount);
                totalCleaned += deleted;
                if (deleted > 0) {
                    log.debug("清理场景 {} 的历史版本，删除 {} 条记录", entityId, deleted);
                }
            }
        } while (!entityIds.isEmpty());

        if (totalCleaned > 0) {
            log.info("场景版本清理完成，共删除 {} 条记录", totalCleaned);
        }
        return totalCleaned;
    }

    @Transactional(rollbackFor = Exception.class)
    int cleanupPropVersions(int threshold, int batchSize, int keepCount) {
        int totalCleaned = 0;
        List<String> entityIds;

        do {
            entityIds = propVersionMapper.selectPropIdsNeedingCleanup(threshold, batchSize);
            for (String entityId : entityIds) {
                int deleted = propVersionMapper.deleteOldVersions(entityId, keepCount);
                totalCleaned += deleted;
                if (deleted > 0) {
                    log.debug("清理道具 {} 的历史版本，删除 {} 条记录", entityId, deleted);
                }
            }
        } while (!entityIds.isEmpty());

        if (totalCleaned > 0) {
            log.info("道具版本清理完成，共删除 {} 条记录", totalCleaned);
        }
        return totalCleaned;
    }

    @Transactional(rollbackFor = Exception.class)
    int cleanupStyleVersions(int threshold, int batchSize, int keepCount) {
        int totalCleaned = 0;
        List<String> entityIds;

        do {
            entityIds = styleVersionMapper.selectStyleIdsNeedingCleanup(threshold, batchSize);
            for (String entityId : entityIds) {
                int deleted = styleVersionMapper.deleteOldVersions(entityId, keepCount);
                totalCleaned += deleted;
                if (deleted > 0) {
                    log.debug("清理风格 {} 的历史版本，删除 {} 条记录", entityId, deleted);
                }
            }
        } while (!entityIds.isEmpty());

        if (totalCleaned > 0) {
            log.info("风格版本清理完成，共删除 {} 条记录", totalCleaned);
        }
        return totalCleaned;
    }

    @Transactional(rollbackFor = Exception.class)
    int cleanupAssetVersions(int threshold, int batchSize, int keepCount) {
        int totalCleaned = 0;
        List<String> entityIds;

        do {
            entityIds = assetVersionMapper.selectAssetIdsNeedingCleanup(threshold, batchSize);
            for (String entityId : entityIds) {
                int deleted = assetVersionMapper.deleteOldVersions(entityId, keepCount);
                totalCleaned += deleted;
                if (deleted > 0) {
                    log.debug("清理素材 {} 的历史版本，删除 {} 条记录", entityId, deleted);
                }
            }
        } while (!entityIds.isEmpty());

        if (totalCleaned > 0) {
            log.info("素材版本清理完成，共删除 {} 条记录", totalCleaned);
        }
        return totalCleaned;
    }

    /**
     * 手动触发清理指定实体的版本
     *
     * @param entityType 实体类型
     * @param entityId   实体ID
     * @param keepCount  保留数量
     * @return 清理的记录数
     */
    public int cleanupEntityVersions(String entityType, String entityId, int keepCount) {
        return switch (entityType.toUpperCase()) {
            case "SCRIPT" -> scriptVersionMapper.deleteOldVersions(entityId, keepCount);
            case "EPISODE" -> episodeVersionMapper.deleteOldVersions(entityId, keepCount);
            case "STORYBOARD" -> storyboardVersionMapper.deleteOldVersions(entityId, keepCount);
            case "CHARACTER" -> characterVersionMapper.deleteOldVersions(entityId, keepCount);
            case "SCENE" -> sceneVersionMapper.deleteOldVersions(entityId, keepCount);
            case "PROP" -> propVersionMapper.deleteOldVersions(entityId, keepCount);
            case "STYLE" -> styleVersionMapper.deleteOldVersions(entityId, keepCount);
            case "ASSET" -> assetVersionMapper.deleteOldVersions(entityId, keepCount);
            default -> 0;
        };
    }
}
