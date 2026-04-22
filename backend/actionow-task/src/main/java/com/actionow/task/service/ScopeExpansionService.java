package com.actionow.task.service;

import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.result.Result;
import com.actionow.common.core.result.ResultCode;
import com.actionow.task.config.TaskRuntimeConfigService;
import com.actionow.task.constant.TaskConstants;
import com.actionow.task.entity.BatchJob;
import com.actionow.task.entity.BatchJobItem;
import com.actionow.task.feign.ProjectFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Scope 展开服务
 * 将作用域（Script / Episode）展开为具体的实体列表，生成 BatchJobItem
 *
 * 支持的展开模式：
 * - EPISODE: 展开为该集下所有分镜
 * - SCRIPT: 展开为该剧本下所有集的所有分镜（并行获取）
 * - CHARACTER: 展开为该剧本下所有角色
 * - SCENE: 展开为该剧本下所有场景
 * - PROP: 展开为该剧本下所有道具
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScopeExpansionService {

    private final ProjectFeignClient projectFeignClient;
    private final TaskRuntimeConfigService taskRuntimeConfig;

    /** Script 级别并行获取分镜的超时时间（秒） */
    private static final int PARALLEL_FETCH_TIMEOUT_SECONDS = 60;

    /**
     * 展开 scope 为 BatchJobItem 列表
     *
     * @param job 批量作业（含 scopeEntityType、scopeEntityId、scriptId）
     * @return 展开后的 item 列表
     * @throws BusinessException 当展开后数量超过 MAX_SCOPE_ITEMS 时
     */
    public List<BatchJobItem> expandScope(BatchJob job) {
        String scopeType = job.getScopeEntityType();
        String scopeId = job.getScopeEntityId();
        String scriptId = job.getScriptId();

        if (!StringUtils.hasText(scopeType)) {
            log.warn("Scope 展开失败: 未指定 scopeEntityType, batchJobId={}", job.getId());
            return Collections.emptyList();
        }

        List<BatchJobItem> items = switch (scopeType) {
            case TaskConstants.EntityType.EPISODE -> expandEpisode(job, scopeId);
            case TaskConstants.EntityType.SCRIPT -> expandScript(job, scriptId != null ? scriptId : scopeId);
            case TaskConstants.EntityType.CHARACTER -> expandCharacters(job, scriptId);
            case TaskConstants.EntityType.SCENE -> expandScenes(job, scriptId);
            case TaskConstants.EntityType.PROP -> expandProps(job, scriptId);
            default -> {
                log.warn("不支持的 scope 类型: {}", scopeType);
                yield Collections.emptyList();
            }
        };

        // 安全限制
        int maxScopeItems = taskRuntimeConfig.getScopeMaxItems();
        if (items.size() > maxScopeItems) {
            throw new BusinessException(ResultCode.PARAM_INVALID,
                    "Scope 展开后实体数量 " + items.size() + " 超过上限 " + maxScopeItems
                            + "，请缩小作用域或分批提交");
        }

        return items;
    }

    /**
     * 展开 Episode → 该集下所有分镜
     */
    private List<BatchJobItem> expandEpisode(BatchJob job, String episodeId) {
        List<Map<String, Object>> storyboards = fetchStoryboardsByEpisode(episodeId);
        log.info("Episode scope 展开: episodeId={}, 分镜数={}", episodeId, storyboards.size());
        return storyboards.stream()
                .map(sb -> buildItem(job, TaskConstants.EntityType.STORYBOARD, sb))
                .toList();
    }

    /**
     * 展开 Script → 该剧本下所有集的所有分镜
     * 使用 CompletableFuture 并行获取各集的分镜，提高大剧本展开性能
     */
    private List<BatchJobItem> expandScript(BatchJob job, String scriptId) {
        List<Map<String, Object>> episodes = fetchEpisodesByScript(scriptId);
        if (episodes.isEmpty()) {
            log.info("Script scope 展开: scriptId={}, 无集数据", scriptId);
            return Collections.emptyList();
        }

        // 并行获取各集的分镜
        List<CompletableFuture<List<Map<String, Object>>>> futures = episodes.stream()
                .map(ep -> {
                    String episodeId = getString(ep, "id");
                    if (episodeId == null) {
                        return CompletableFuture.completedFuture(Collections.<Map<String, Object>>emptyList());
                    }
                    return CompletableFuture.supplyAsync(() -> fetchStoryboardsByEpisode(episodeId));
                })
                .toList();

        // 等待全部完成
        List<BatchJobItem> items = new ArrayList<>();
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(PARALLEL_FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            for (CompletableFuture<List<Map<String, Object>>> future : futures) {
                List<Map<String, Object>> storyboards = future.get();
                for (Map<String, Object> sb : storyboards) {
                    items.add(buildItem(job, TaskConstants.EntityType.STORYBOARD, sb));
                }
            }
        } catch (Exception e) {
            log.error("Script scope 并行展开失败: scriptId={}", scriptId, e);
            // 降级到顺序展开
            items.clear();
            for (Map<String, Object> ep : episodes) {
                String episodeId = getString(ep, "id");
                if (episodeId == null) continue;
                List<Map<String, Object>> storyboards = fetchStoryboardsByEpisode(episodeId);
                for (Map<String, Object> sb : storyboards) {
                    items.add(buildItem(job, TaskConstants.EntityType.STORYBOARD, sb));
                }
            }
        }

        log.info("Script scope 展开: scriptId={}, 集数={}, 分镜总数={}",
                scriptId, episodes.size(), items.size());
        return items;
    }

    /**
     * 展开剧本下所有角色
     */
    private List<BatchJobItem> expandCharacters(BatchJob job, String scriptId) {
        List<Map<String, Object>> characters = fetchCharacters(scriptId);
        log.info("Character scope 展开: scriptId={}, 角色数={}", scriptId, characters.size());
        return characters.stream()
                .map(c -> buildItem(job, TaskConstants.EntityType.CHARACTER, c))
                .toList();
    }

    /**
     * 展开剧本下所有场景
     */
    private List<BatchJobItem> expandScenes(BatchJob job, String scriptId) {
        List<Map<String, Object>> scenes = fetchScenes(scriptId);
        log.info("Scene scope 展开: scriptId={}, 场景数={}", scriptId, scenes.size());
        return scenes.stream()
                .map(s -> buildItem(job, TaskConstants.EntityType.SCENE, s))
                .toList();
    }

    /**
     * 展开剧本下所有道具
     */
    private List<BatchJobItem> expandProps(BatchJob job, String scriptId) {
        List<Map<String, Object>> props = fetchProps(scriptId);
        log.info("Prop scope 展开: scriptId={}, 道具数={}", scriptId, props.size());
        return props.stream()
                .map(p -> buildItem(job, TaskConstants.EntityType.PROP, p))
                .toList();
    }

    // ==================== Feign 调用 ====================

    private List<Map<String, Object>> fetchEpisodesByScript(String scriptId) {
        try {
            Result<List<Map<String, Object>>> result = projectFeignClient.listEpisodesByScript(scriptId, null);
            return result.isSuccess() && result.getData() != null ? result.getData() : Collections.emptyList();
        } catch (Exception e) {
            log.error("获取剧本下集列表失败: scriptId={}", scriptId, e);
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> fetchStoryboardsByEpisode(String episodeId) {
        try {
            Result<List<Map<String, Object>>> result = projectFeignClient.listStoryboardsByEpisode(episodeId, null);
            return result.isSuccess() && result.getData() != null ? result.getData() : Collections.emptyList();
        } catch (Exception e) {
            log.error("获取集下分镜列表失败: episodeId={}", episodeId, e);
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> fetchCharacters(String scriptId) {
        try {
            Result<List<Map<String, Object>>> result = projectFeignClient.listAvailableCharacters(scriptId, null);
            return result.isSuccess() && result.getData() != null ? result.getData() : Collections.emptyList();
        } catch (Exception e) {
            log.error("获取角色列表失败: scriptId={}", scriptId, e);
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> fetchScenes(String scriptId) {
        try {
            Result<List<Map<String, Object>>> result = projectFeignClient.listAvailableScenes(scriptId, null);
            return result.isSuccess() && result.getData() != null ? result.getData() : Collections.emptyList();
        } catch (Exception e) {
            log.error("获取场景列表失败: scriptId={}", scriptId, e);
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> fetchProps(String scriptId) {
        try {
            Result<List<Map<String, Object>>> result = projectFeignClient.listAvailableProps(scriptId, null);
            return result.isSuccess() && result.getData() != null ? result.getData() : Collections.emptyList();
        } catch (Exception e) {
            log.error("获取道具列表失败: scriptId={}", scriptId, e);
            return Collections.emptyList();
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 从实体 Map 构建 BatchJobItem
     */
    private BatchJobItem buildItem(BatchJob job, String entityType, Map<String, Object> entity) {
        BatchJobItem item = new BatchJobItem();
        item.setEntityType(entityType);
        item.setEntityId(getString(entity, "id"));
        item.setEntityName(getString(entity, "name"));

        // 合并共享参数
        Map<String, Object> params = new HashMap<>();
        if (job.getSharedParams() != null) {
            params.putAll(job.getSharedParams());
        }
        item.setParams(params);

        // 继承 batch 级别的 provider 和 generationType
        item.setProviderId(job.getProviderId());
        item.setGenerationType(job.getGenerationType());
        item.setSkipCondition(TaskConstants.SkipCondition.NONE);
        item.setSkipped(false);
        item.setVariantIndex(0);
        item.setCreditCost(0L);

        return item;
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }
}
