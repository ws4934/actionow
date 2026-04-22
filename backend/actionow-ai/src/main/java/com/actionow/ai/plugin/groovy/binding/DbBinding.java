package com.actionow.ai.plugin.groovy.binding;

import com.actionow.ai.feign.ProjectFeignClient;
import com.actionow.common.core.result.Result;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 数据库操作绑定（受限白名单控制）
 * 通过 Feign 客户端调用 Project 微服务来执行数据库操作
 * 支持：Script, Episode, Storyboard, Character, Scene, Prop, Style, Asset
 *
 * @author Actionow
 */
@Slf4j
public class DbBinding {

    /** 结果集硬上限 */
    private static final int MAX_RESULT_SIZE = 10_000;

    private final ProjectFeignClient projectFeignClient;

    /**
     * 工作空间ID（必须）
     */
    private String workspaceId;

    /**
     * 用户ID（创建者）
     */
    private String userId;

    /**
     * 租户Schema
     */
    private String tenantSchema;

    public DbBinding(ProjectFeignClient projectFeignClient) {
        this.projectFeignClient = projectFeignClient;
    }

    /**
     * 设置上下文信息
     */
    public void setContext(String workspaceId, String userId, String tenantSchema) {
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.tenantSchema = tenantSchema;
    }

    // ==================== Script 剧本操作 ====================

    /**
     * 创建剧本
     */
    public Map<String, Object> createScript(Map<String, Object> data) {
        log.info("[DbBinding] Creating script: {}", data.get("title"));
        validateContext();

        Map<String, Object> request = new HashMap<>(data);
        enrichRequest(request);

        try {
            Result<Map<String, Object>> result = projectFeignClient.createScript(request);
            return buildResult(result);
        } catch (Exception e) {
            log.error("[DbBinding] Failed to create script", e);
            return buildErrorResult(e);
        }
    }

    /**
     * 批量创建剧本
     */
    public Map<String, Object> batchCreateScripts(List<Map<String, Object>> scripts) {
        log.info("[DbBinding] Batch creating {} scripts", scripts.size());
        validateContext();

        List<Map<String, Object>> requests = enrichRequestList(scripts);

        try {
            Result<List<String>> result = projectFeignClient.batchCreateScripts(requests);
            return buildListResult(result);
        } catch (Exception e) {
            log.error("[DbBinding] Failed to batch create scripts", e);
            return buildErrorResult(e);
        }
    }

    /**
     * 更新剧本
     */
    public Map<String, Object> updateScript(String scriptId, Map<String, Object> data) {
        log.info("[DbBinding] Updating script: {}", scriptId);
        validateContext();

        try {
            Result<Void> result = projectFeignClient.updateScript(scriptId, data);
            return buildVoidResult(result);
        } catch (Exception e) {
            log.error("[DbBinding] Failed to update script", e);
            return buildErrorResult(e);
        }
    }

    /**
     * 删除剧本
     */
    public Map<String, Object> deleteScript(String scriptId) {
        log.info("[DbBinding] Deleting script: {}", scriptId);
        validateContext();

        try {
            Result<Void> result = projectFeignClient.deleteScript(scriptId);
            return buildVoidResult(result);
        } catch (Exception e) {
            log.error("[DbBinding] Failed to delete script", e);
            return buildErrorResult(e);
        }
    }

    /**
     * 批量删除剧本
     */
    public Map<String, Object> batchDeleteScripts(List<String> scriptIds) {
        log.info("[DbBinding] Batch deleting {} scripts", scriptIds.size());
        validateContext();

        try {
            Result<Void> result = projectFeignClient.batchDeleteScripts(scriptIds);
            return buildVoidResult(result);
        } catch (Exception e) {
            log.error("[DbBinding] Failed to batch delete scripts", e);
            return buildErrorResult(e);
        }
    }

    /**
     * 获取剧本详情
     */
    public Map<String, Object> getScript(String scriptId) {
        log.info("[DbBinding] Getting script: {}", scriptId);
        validateContext();

        try {
            Result<Map<String, Object>> result = projectFeignClient.getScript(scriptId);
            if (result.isSuccess()) {
                return result.getData() != null ? result.getData() : Collections.emptyMap();
            }
            return Collections.emptyMap();
        } catch (Exception e) {
            log.error("[DbBinding] Failed to get script", e);
            return Collections.emptyMap();
        }
    }

    /**
     * 获取工作空间下的剧本列表
     */
    public List<Map<String, Object>> listScripts() {
        log.info("[DbBinding] Listing scripts for workspace: {}", workspaceId);
        validateContext();

        try {
            Result<List<Map<String, Object>>> result = projectFeignClient.listScripts(workspaceId);
            if (result.isSuccess()) {
                return limitResults(result.getData());
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("[DbBinding] Failed to list scripts", e);
            return Collections.emptyList();
        }
    }

    // ==================== Episode 章节操作 ====================

    /**
     * 创建章节
     */
    public Map<String, Object> createEpisode(Map<String, Object> data) {
        log.info("[DbBinding] Creating episode: {}", data.get("title"));
        validateContext();

        Map<String, Object> request = new HashMap<>(data);
        enrichRequest(request);

        try {
            Result<Map<String, Object>> result = projectFeignClient.createEpisode(request);
            return buildResult(result);
        } catch (Exception e) {
            log.error("[DbBinding] Failed to create episode", e);
            return buildErrorResult(e);
        }
    }

    /**
     * 批量创建章节
     */
    public Map<String, Object> batchCreateEpisodes(List<Map<String, Object>> episodes) {
        log.info("[DbBinding] Batch creating {} episodes", episodes.size());
        validateContext();

        List<Map<String, Object>> requests = enrichRequestList(episodes);

        try {
            Result<List<String>> result = projectFeignClient.batchCreateEpisodes(requests);
            return buildListResult(result);
        } catch (Exception e) {
            log.error("[DbBinding] Failed to batch create episodes", e);
            return buildErrorResult(e);
        }
    }

    /**
     * 更新章节
     */
    public Map<String, Object> updateEpisode(String episodeId, Map<String, Object> data) {
        log.info("[DbBinding] Updating episode: {}", episodeId);
        validateContext();

        try {
            Result<Void> result = projectFeignClient.updateEpisode(episodeId, data);
            return buildVoidResult(result);
        } catch (Exception e) {
            log.error("[DbBinding] Failed to update episode", e);
            return buildErrorResult(e);
        }
    }

    /**
     * 删除章节
     */
    public Map<String, Object> deleteEpisode(String episodeId) {
        log.info("[DbBinding] Deleting episode: {}", episodeId);
        validateContext();

        try {
            Result<Void> result = projectFeignClient.deleteEpisode(episodeId);
            return buildVoidResult(result);
        } catch (Exception e) {
            log.error("[DbBinding] Failed to delete episode", e);
            return buildErrorResult(e);
        }
    }

    /**
     * 批量删除章节
     */
    public Map<String, Object> batchDeleteEpisodes(List<String> episodeIds) {
        log.info("[DbBinding] Batch deleting {} episodes", episodeIds.size());
        validateContext();

        try {
            Result<Void> result = projectFeignClient.batchDeleteEpisodes(episodeIds);
            return buildVoidResult(result);
        } catch (Exception e) {
            log.error("[DbBinding] Failed to batch delete episodes", e);
            return buildErrorResult(e);
        }
    }

    /**
     * 获取章节详情
     */
    public Map<String, Object> getEpisode(String episodeId) {
        log.info("[DbBinding] Getting episode: {}", episodeId);
        validateContext();

        try {
            Result<Map<String, Object>> result = projectFeignClient.getEpisode(episodeId);
            if (result.isSuccess()) {
                return result.getData() != null ? result.getData() : Collections.emptyMap();
            }
            return Collections.emptyMap();
        } catch (Exception e) {
            log.error("[DbBinding] Failed to get episode", e);
            return Collections.emptyMap();
        }
    }

    /**
     * 获取剧本下的所有章节
     */
    public List<Map<String, Object>> getEpisodesByScript(String scriptId) {
        log.info("[DbBinding] Getting episodes for script: {}", scriptId);
        validateContext();

        try {
            Result<List<Map<String, Object>>> result = projectFeignClient.getEpisodesByScript(scriptId);
            if (result.isSuccess()) {
                return limitResults(result.getData());
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("[DbBinding] Failed to get episodes", e);
            return Collections.emptyList();
        }
    }

    // ==================== Storyboard 分镜操作 ====================

    /**
     * 创建分镜
     */
    public Map<String, Object> createStoryboard(Map<String, Object> data) {
        log.info("[DbBinding] Creating storyboard for episode: {}", data.get("episodeId"));
        validateContext();

        Map<String, Object> request = new HashMap<>(data);
        enrichRequest(request);

        try {
            Result<Map<String, Object>> result = projectFeignClient.createStoryboard(request);
            return buildResult(result);
        } catch (Exception e) {
            log.error("[DbBinding] Failed to create storyboard", e);
            return buildErrorResult(e);
        }
    }

    /**
     * 批量创建分镜
     */
    public Map<String, Object> batchCreateStoryboards(List<Map<String, Object>> storyboards) {
        log.info("[DbBinding] Batch creating {} storyboards", storyboards.size());
        validateContext();

        List<Map<String, Object>> requests = enrichRequestList(storyboards);

        try {
            Result<List<String>> result = projectFeignClient.batchCreateStoryboards(requests);
            return buildListResult(result);
        } catch (Exception e) {
            log.error("[DbBinding] Failed to batch create storyboards", e);
            return buildErrorResult(e);
        }
    }

    /**
     * 更新分镜
     */
    public Map<String, Object> updateStoryboard(String storyboardId, Map<String, Object> data) {
        log.info("[DbBinding] Updating storyboard: {}", storyboardId);
        validateContext();

        try {
            Result<Void> result = projectFeignClient.updateStoryboard(storyboardId, data);
            return buildVoidResult(result);
        } catch (Exception e) {
            log.error("[DbBinding] Failed to update storyboard", e);
            return buildErrorResult(e);
        }
    }

    /**
     * 删除分镜
     */
    public Map<String, Object> deleteStoryboard(String storyboardId) {
        log.info("[DbBinding] Deleting storyboard: {}", storyboardId);
        validateContext();

        try {
            Result<Void> result = projectFeignClient.deleteStoryboard(storyboardId);
            return buildVoidResult(result);
        } catch (Exception e) {
            log.error("[DbBinding] Failed to delete storyboard", e);
            return buildErrorResult(e);
        }
    }

    /**
     * 批量删除分镜
     */
    public Map<String, Object> batchDeleteStoryboards(List<String> storyboardIds) {
        log.info("[DbBinding] Batch deleting {} storyboards", storyboardIds.size());
        validateContext();

        try {
            Result<Void> result = projectFeignClient.batchDeleteStoryboards(storyboardIds);
            return buildVoidResult(result);
        } catch (Exception e) {
            log.error("[DbBinding] Failed to batch delete storyboards", e);
            return buildErrorResult(e);
        }
    }

    /**
     * 获取分镜详情
     */
    public Map<String, Object> getStoryboard(String storyboardId) {
        log.info("[DbBinding] Getting storyboard: {}", storyboardId);
        validateContext();

        try {
            Result<Map<String, Object>> result = projectFeignClient.getStoryboard(storyboardId);
            if (result.isSuccess()) {
                return result.getData() != null ? result.getData() : Collections.emptyMap();
            }
            return Collections.emptyMap();
        } catch (Exception e) {
            log.error("[DbBinding] Failed to get storyboard", e);
            return Collections.emptyMap();
        }
    }

    /**
     * 获取章节下的所有分镜
     */
    public List<Map<String, Object>> getStoryboardsByEpisode(String episodeId) {
        log.info("[DbBinding] Getting storyboards for episode: {}", episodeId);
        validateContext();

        try {
            Result<List<Map<String, Object>>> result = projectFeignClient.getStoryboardsByEpisode(episodeId);
            if (result.isSuccess()) {
                return limitResults(result.getData());
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("[DbBinding] Failed to get storyboards", e);
            return Collections.emptyList();
        }
    }

    // ==================== Character 角色操作 ====================

    /**
     * 创建角色
     */
    public Map<String, Object> createCharacter(Map<String, Object> data) {
        log.info("[DbBinding] Creating character: {}", data.get("name"));
        validateContext();

        Map<String, Object> request = new HashMap<>(data);
        enrichRequest(request);

        try {
            Result<Map<String, Object>> result = projectFeignClient.createCharacter(request);
            return buildResult(result);
        } catch (Exception e) {
            log.error("[DbBinding] Failed to create character", e);
            return buildErrorResult(e);
        }
    }

    /**
     * 批量创建角色
     */
    public Map<String, Object> batchCreateCharacters(List<Map<String, Object>> characters) {
        log.info("[DbBinding] Batch creating {} characters", characters.size());
        validateContext();

        List<Map<String, Object>> requests = enrichRequestList(characters);

        try {
            Result<List<String>> result = projectFeignClient.batchCreateCharacters(requests);
            return buildListResult(result);
        } catch (Exception e) {
            log.error("[DbBinding] Failed to batch create characters", e);
            return buildErrorResult(e);
        }
    }

    /**
     * 更新角色
     */
    public Map<String, Object> updateCharacter(String characterId, Map<String, Object> data) {
        log.info("[DbBinding] Updating character: {}", characterId);
        validateContext();

        try {
            Result<Void> result = projectFeignClient.updateCharacter(characterId, data);
            return buildVoidResult(result);
        } catch (Exception e) {
            log.error("[DbBinding] Failed to update character", e);
            return buildErrorResult(e);
        }
    }

    /**
     * 删除角色
     */
    public Map<String, Object> deleteCharacter(String characterId) {
        log.info("[DbBinding] Deleting character: {}", characterId);
        validateContext();

        try {
            Result<Void> result = projectFeignClient.deleteCharacter(characterId);
            return buildVoidResult(result);
        } catch (Exception e) {
            log.error("[DbBinding] Failed to delete character", e);
            return buildErrorResult(e);
        }
    }

    /**
     * 批量删除角色
     */
    public Map<String, Object> batchDeleteCharacters(List<String> characterIds) {
        log.info("[DbBinding] Batch deleting {} characters", characterIds.size());
        validateContext();

        try {
            Result<Void> result = projectFeignClient.batchDeleteCharacters(characterIds);
            return buildVoidResult(result);
        } catch (Exception e) {
            log.error("[DbBinding] Failed to batch delete characters", e);
            return buildErrorResult(e);
        }
    }

    /**
     * 获取角色详情
     */
    public Map<String, Object> getCharacter(String characterId) {
        log.info("[DbBinding] Getting character: {}", characterId);
        validateContext();

        try {
            Result<Map<String, Object>> result = projectFeignClient.getCharacter(characterId);
            if (result.isSuccess()) {
                return result.getData() != null ? result.getData() : Collections.emptyMap();
            }
            return Collections.emptyMap();
        } catch (Exception e) {
            log.error("[DbBinding] Failed to get character", e);
            return Collections.emptyMap();
        }
    }

    /**
     * 获取剧本下的所有角色
     */
    public List<Map<String, Object>> getCharactersByScript(String scriptId) {
        log.info("[DbBinding] Getting characters for script: {}", scriptId);
        validateContext();

        try {
            Result<List<Map<String, Object>>> result = projectFeignClient.getCharactersByScript(scriptId);
            if (result.isSuccess()) {
                return limitResults(result.getData());
            }
            log.warn("[DbBinding] Failed to get characters: {}", result.getMessage());
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("[DbBinding] Failed to get characters", e);
            return Collections.emptyList();
        }
    }

    // ==================== Scene 场景操作 ====================

    /**
     * 创建场景
     */
    public Map<String, Object> createScene(Map<String, Object> data) {
        log.info("[DbBinding] Creating scene: {}", data.get("name"));
        validateContext();

        Map<String, Object> request = new HashMap<>(data);
        enrichRequest(request);

        try {
            Result<Map<String, Object>> result = projectFeignClient.createScene(request);
            return buildResult(result);
        } catch (Exception e) {
            log.error("[DbBinding] Failed to create scene", e);
            return buildErrorResult(e);
        }
    }

    /**
     * 批量创建场景
     */
    public Map<String, Object> batchCreateScenes(List<Map<String, Object>> scenes) {
        log.info("[DbBinding] Batch creating {} scenes", scenes.size());
        validateContext();

        List<Map<String, Object>> requests = enrichRequestList(scenes);

        try {
            Result<List<String>> result = projectFeignClient.batchCreateScenes(requests);
            return buildListResult(result);
        } catch (Exception e) {
            log.error("[DbBinding] Failed to batch create scenes", e);
            return buildErrorResult(e);
        }
    }

    /**
     * 更新场景
     */
    public Map<String, Object> updateScene(String sceneId, Map<String, Object> data) {
        log.info("[DbBinding] Updating scene: {}", sceneId);
        validateContext();

        try {
            Result<Void> result = projectFeignClient.updateScene(sceneId, data);
            return buildVoidResult(result);
        } catch (Exception e) {
            log.error("[DbBinding] Failed to update scene", e);
            return buildErrorResult(e);
        }
    }

    /**
     * 删除场景
     */
    public Map<String, Object> deleteScene(String sceneId) {
        log.info("[DbBinding] Deleting scene: {}", sceneId);
        validateContext();

        try {
            Result<Void> result = projectFeignClient.deleteScene(sceneId);
            return buildVoidResult(result);
        } catch (Exception e) {
            log.error("[DbBinding] Failed to delete scene", e);
            return buildErrorResult(e);
        }
    }

    /**
     * 批量删除场景
     */
    public Map<String, Object> batchDeleteScenes(List<String> sceneIds) {
        log.info("[DbBinding] Batch deleting {} scenes", sceneIds.size());
        validateContext();

        try {
            Result<Void> result = projectFeignClient.batchDeleteScenes(sceneIds);
            return buildVoidResult(result);
        } catch (Exception e) {
            log.error("[DbBinding] Failed to batch delete scenes", e);
            return buildErrorResult(e);
        }
    }

    /**
     * 获取场景详情
     */
    public Map<String, Object> getScene(String sceneId) {
        log.info("[DbBinding] Getting scene: {}", sceneId);
        validateContext();

        try {
            Result<Map<String, Object>> result = projectFeignClient.getScene(sceneId);
            if (result.isSuccess()) {
                return result.getData() != null ? result.getData() : Collections.emptyMap();
            }
            return Collections.emptyMap();
        } catch (Exception e) {
            log.error("[DbBinding] Failed to get scene", e);
            return Collections.emptyMap();
        }
    }

    /**
     * 获取剧本下的所有场景
     */
    public List<Map<String, Object>> getScenesByScript(String scriptId) {
        log.info("[DbBinding] Getting scenes for script: {}", scriptId);
        validateContext();

        try {
            Result<List<Map<String, Object>>> result = projectFeignClient.getScenesByScript(scriptId);
            if (result.isSuccess()) {
                return limitResults(result.getData());
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("[DbBinding] Failed to get scenes", e);
            return Collections.emptyList();
        }
    }

    // ==================== Prop 道具操作 ====================

    /**
     * 创建道具
     */
    public Map<String, Object> createProp(Map<String, Object> data) {
        log.info("[DbBinding] Creating prop: {}", data.get("name"));
        validateContext();

        Map<String, Object> request = new HashMap<>(data);
        enrichRequest(request);

        try {
            Result<Map<String, Object>> result = projectFeignClient.createProp(request);
            return buildResult(result);
        } catch (Exception e) {
            log.error("[DbBinding] Failed to create prop", e);
            return buildErrorResult(e);
        }
    }

    /**
     * 批量创建道具
     */
    public Map<String, Object> batchCreateProps(List<Map<String, Object>> props) {
        log.info("[DbBinding] Batch creating {} props", props.size());
        validateContext();

        List<Map<String, Object>> requests = enrichRequestList(props);

        try {
            Result<List<String>> result = projectFeignClient.batchCreateProps(requests);
            return buildListResult(result);
        } catch (Exception e) {
            log.error("[DbBinding] Failed to batch create props", e);
            return buildErrorResult(e);
        }
    }

    /**
     * 更新道具
     */
    public Map<String, Object> updateProp(String propId, Map<String, Object> data) {
        log.info("[DbBinding] Updating prop: {}", propId);
        validateContext();

        try {
            Result<Void> result = projectFeignClient.updateProp(propId, data);
            return buildVoidResult(result);
        } catch (Exception e) {
            log.error("[DbBinding] Failed to update prop", e);
            return buildErrorResult(e);
        }
    }

    /**
     * 删除道具
     */
    public Map<String, Object> deleteProp(String propId) {
        log.info("[DbBinding] Deleting prop: {}", propId);
        validateContext();

        try {
            Result<Void> result = projectFeignClient.deleteProp(propId);
            return buildVoidResult(result);
        } catch (Exception e) {
            log.error("[DbBinding] Failed to delete prop", e);
            return buildErrorResult(e);
        }
    }

    /**
     * 批量删除道具
     */
    public Map<String, Object> batchDeleteProps(List<String> propIds) {
        log.info("[DbBinding] Batch deleting {} props", propIds.size());
        validateContext();

        try {
            Result<Void> result = projectFeignClient.batchDeleteProps(propIds);
            return buildVoidResult(result);
        } catch (Exception e) {
            log.error("[DbBinding] Failed to batch delete props", e);
            return buildErrorResult(e);
        }
    }

    /**
     * 获取道具详情
     */
    public Map<String, Object> getProp(String propId) {
        log.info("[DbBinding] Getting prop: {}", propId);
        validateContext();

        try {
            Result<Map<String, Object>> result = projectFeignClient.getProp(propId);
            if (result.isSuccess()) {
                return result.getData() != null ? result.getData() : Collections.emptyMap();
            }
            return Collections.emptyMap();
        } catch (Exception e) {
            log.error("[DbBinding] Failed to get prop", e);
            return Collections.emptyMap();
        }
    }

    /**
     * 获取剧本下的所有道具
     */
    public List<Map<String, Object>> getPropsByScript(String scriptId) {
        log.info("[DbBinding] Getting props for script: {}", scriptId);
        validateContext();

        try {
            Result<List<Map<String, Object>>> result = projectFeignClient.getPropsByScript(scriptId);
            if (result.isSuccess()) {
                return limitResults(result.getData());
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("[DbBinding] Failed to get props", e);
            return Collections.emptyList();
        }
    }

    // ==================== Style 风格操作 ====================

    /**
     * 创建风格
     */
    public Map<String, Object> createStyle(Map<String, Object> data) {
        log.info("[DbBinding] Creating style: {}", data.get("name"));
        validateContext();

        Map<String, Object> request = new HashMap<>(data);
        enrichRequest(request);

        try {
            Result<Map<String, Object>> result = projectFeignClient.createStyle(request);
            return buildResult(result);
        } catch (Exception e) {
            log.error("[DbBinding] Failed to create style", e);
            return buildErrorResult(e);
        }
    }

    /**
     * 批量创建风格
     */
    public Map<String, Object> batchCreateStyles(List<Map<String, Object>> styles) {
        log.info("[DbBinding] Batch creating {} styles", styles.size());
        validateContext();

        List<Map<String, Object>> requests = enrichRequestList(styles);

        try {
            Result<List<String>> result = projectFeignClient.batchCreateStyles(requests);
            return buildListResult(result);
        } catch (Exception e) {
            log.error("[DbBinding] Failed to batch create styles", e);
            return buildErrorResult(e);
        }
    }

    /**
     * 更新风格
     */
    public Map<String, Object> updateStyle(String styleId, Map<String, Object> data) {
        log.info("[DbBinding] Updating style: {}", styleId);
        validateContext();

        try {
            Result<Void> result = projectFeignClient.updateStyle(styleId, data);
            return buildVoidResult(result);
        } catch (Exception e) {
            log.error("[DbBinding] Failed to update style", e);
            return buildErrorResult(e);
        }
    }

    /**
     * 删除风格
     */
    public Map<String, Object> deleteStyle(String styleId) {
        log.info("[DbBinding] Deleting style: {}", styleId);
        validateContext();

        try {
            Result<Void> result = projectFeignClient.deleteStyle(styleId);
            return buildVoidResult(result);
        } catch (Exception e) {
            log.error("[DbBinding] Failed to delete style", e);
            return buildErrorResult(e);
        }
    }

    /**
     * 批量删除风格
     */
    public Map<String, Object> batchDeleteStyles(List<String> styleIds) {
        log.info("[DbBinding] Batch deleting {} styles", styleIds.size());
        validateContext();

        try {
            Result<Void> result = projectFeignClient.batchDeleteStyles(styleIds);
            return buildVoidResult(result);
        } catch (Exception e) {
            log.error("[DbBinding] Failed to batch delete styles", e);
            return buildErrorResult(e);
        }
    }

    /**
     * 获取风格详情
     */
    public Map<String, Object> getStyle(String styleId) {
        log.info("[DbBinding] Getting style: {}", styleId);
        validateContext();

        try {
            Result<Map<String, Object>> result = projectFeignClient.getStyle(styleId);
            if (result.isSuccess()) {
                return result.getData() != null ? result.getData() : Collections.emptyMap();
            }
            return Collections.emptyMap();
        } catch (Exception e) {
            log.error("[DbBinding] Failed to get style", e);
            return Collections.emptyMap();
        }
    }

    /**
     * 获取剧本下的所有风格
     */
    public List<Map<String, Object>> getStylesByScript(String scriptId) {
        log.info("[DbBinding] Getting styles for script: {}", scriptId);
        validateContext();

        try {
            Result<List<Map<String, Object>>> result = projectFeignClient.getStylesByScript(scriptId);
            if (result.isSuccess()) {
                return limitResults(result.getData());
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("[DbBinding] Failed to get styles", e);
            return Collections.emptyList();
        }
    }

    // ==================== Asset 素材操作 ====================

    /**
     * 创建素材
     */
    public Map<String, Object> createAsset(Map<String, Object> data) {
        log.info("[DbBinding] Creating asset: {}", data.get("name"));
        validateContext();

        Map<String, Object> request = new HashMap<>(data);
        enrichRequest(request);

        try {
            Result<Map<String, Object>> result = projectFeignClient.createAsset(request);
            return buildResult(result);
        } catch (Exception e) {
            log.error("[DbBinding] Failed to create asset", e);
            return buildErrorResult(e);
        }
    }

    /**
     * 批量创建素材
     */
    public Map<String, Object> batchCreateAssets(List<Map<String, Object>> assets) {
        log.info("[DbBinding] Batch creating {} assets", assets.size());
        validateContext();

        List<Map<String, Object>> requests = enrichRequestList(assets);

        try {
            Result<List<String>> result = projectFeignClient.batchCreateAssets(requests);
            return buildListResult(result);
        } catch (Exception e) {
            log.error("[DbBinding] Failed to batch create assets", e);
            return buildErrorResult(e);
        }
    }

    /**
     * 更新素材
     */
    public Map<String, Object> updateAsset(String assetId, Map<String, Object> data) {
        log.info("[DbBinding] Updating asset: {}", assetId);
        validateContext();

        try {
            Result<Void> result = projectFeignClient.updateAsset(assetId, data);
            return buildVoidResult(result);
        } catch (Exception e) {
            log.error("[DbBinding] Failed to update asset", e);
            return buildErrorResult(e);
        }
    }

    /**
     * 删除素材
     */
    public Map<String, Object> deleteAsset(String assetId) {
        log.info("[DbBinding] Deleting asset: {}", assetId);
        validateContext();

        try {
            Result<Void> result = projectFeignClient.deleteAsset(assetId);
            return buildVoidResult(result);
        } catch (Exception e) {
            log.error("[DbBinding] Failed to delete asset", e);
            return buildErrorResult(e);
        }
    }

    /**
     * 批量删除素材
     */
    public Map<String, Object> batchDeleteAssets(List<String> assetIds) {
        log.info("[DbBinding] Batch deleting {} assets", assetIds.size());
        validateContext();

        try {
            Result<Void> result = projectFeignClient.batchDeleteAssets(assetIds);
            return buildVoidResult(result);
        } catch (Exception e) {
            log.error("[DbBinding] Failed to batch delete assets", e);
            return buildErrorResult(e);
        }
    }

    /**
     * 获取素材详情
     */
    public Map<String, Object> getAsset(String assetId) {
        log.info("[DbBinding] Getting asset: {}", assetId);
        validateContext();

        try {
            Result<Map<String, Object>> result = projectFeignClient.getAsset(assetId);
            if (result.isSuccess()) {
                return result.getData() != null ? result.getData() : Collections.emptyMap();
            }
            return Collections.emptyMap();
        } catch (Exception e) {
            log.error("[DbBinding] Failed to get asset", e);
            return Collections.emptyMap();
        }
    }

    /**
     * 获取剧本下的所有素材
     */
    public List<Map<String, Object>> getAssetsByScript(String scriptId) {
        log.info("[DbBinding] Getting assets for script: {}", scriptId);
        validateContext();

        try {
            Result<List<Map<String, Object>>> result = projectFeignClient.getAssetsByScript(scriptId);
            if (result.isSuccess()) {
                return limitResults(result.getData());
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("[DbBinding] Failed to get assets", e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取工作空间下的素材列表
     */
    public List<Map<String, Object>> listAssets() {
        return listAssets(null);
    }

    /**
     * 获取工作空间下的素材列表（按类型过滤）
     */
    public List<Map<String, Object>> listAssets(String assetType) {
        log.info("[DbBinding] Listing assets for workspace: {}, type: {}", workspaceId, assetType);
        validateContext();

        try {
            Result<List<Map<String, Object>>> result = projectFeignClient.listAssets(workspaceId, assetType);
            if (result.isSuccess()) {
                return limitResults(result.getData());
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("[DbBinding] Failed to list assets", e);
            return Collections.emptyList();
        }
    }

    /**
     * 更新素材生成状态
     */
    public Map<String, Object> updateAssetGenerationStatus(String assetId, String status) {
        return updateAssetGenerationStatus(assetId, status, null);
    }

    /**
     * 更新素材生成状态（带错误信息）
     */
    public Map<String, Object> updateAssetGenerationStatus(String assetId, String status, String errorMessage) {
        log.info("[DbBinding] Updating asset generation status: {} -> {}", assetId, status);
        validateContext();

        try {
            Result<Void> result = projectFeignClient.updateAssetGenerationStatus(assetId, status, errorMessage);
            return buildVoidResult(result);
        } catch (Exception e) {
            log.error("[DbBinding] Failed to update asset generation status", e);
            return buildErrorResult(e);
        }
    }

    /**
     * 更新素材文件信息（AI生成完成后）
     */
    public Map<String, Object> updateAssetFileInfo(String assetId, Map<String, Object> fileInfo) {
        log.info("[DbBinding] Updating asset file info: {}", assetId);
        validateContext();

        try {
            Result<Void> result = projectFeignClient.updateAssetFileInfo(assetId, fileInfo);
            return buildVoidResult(result);
        } catch (Exception e) {
            log.error("[DbBinding] Failed to update asset file info", e);
            return buildErrorResult(e);
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 验证上下文（workspaceId + userId 必须设置）
     */
    private void validateContext() {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalStateException("DbBinding: workspaceId is required");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalStateException("DbBinding: userId is required");
        }
    }

    /**
     * 丰富请求数据（添加上下文信息）
     */
    private void enrichRequest(Map<String, Object> request) {
        if (workspaceId != null) {
            request.putIfAbsent("workspaceId", workspaceId);
        }
        if (userId != null) {
            request.putIfAbsent("creatorId", userId);
            request.putIfAbsent("createdBy", userId);
        }
        if (tenantSchema != null) {
            request.putIfAbsent("tenantSchema", tenantSchema);
        }
    }

    /**
     * 批量丰富请求列表
     */
    private List<Map<String, Object>> enrichRequestList(List<Map<String, Object>> dataList) {
        return dataList.stream()
                .map(data -> {
                    Map<String, Object> request = new HashMap<>(data);
                    enrichRequest(request);
                    return request;
                })
                .toList();
    }

    /**
     * 构建返回结果
     */
    private Map<String, Object> buildResult(Result<Map<String, Object>> result) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", result.isSuccess());
        if (result.isSuccess() && result.getData() != null) {
            response.put("id", result.getData().get("id"));
            response.put("data", result.getData());
        } else {
            response.put("error", result.getMessage());
        }
        return response;
    }

    /**
     * 构建列表返回结果
     */
    private Map<String, Object> buildListResult(Result<List<String>> result) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", result.isSuccess());
        if (result.isSuccess() && result.getData() != null) {
            response.put("ids", result.getData());
            response.put("count", result.getData().size());
        } else {
            response.put("error", result.getMessage());
        }
        return response;
    }

    /**
     * 构建Void返回结果
     */
    private Map<String, Object> buildVoidResult(Result<Void> result) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", result.isSuccess());
        if (!result.isSuccess()) {
            response.put("error", result.getMessage());
        }
        return response;
    }

    /**
     * 构建错误返回结果
     */
    private Map<String, Object> buildErrorResult(Exception e) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", e.getMessage());
        return response;
    }

    /**
     * 限制列表结果集大小，防止内存溢出
     */
    private <T> List<T> limitResults(List<T> list) {
        if (list == null) {
            return Collections.emptyList();
        }
        if (list.size() > MAX_RESULT_SIZE) {
            log.warn("[DbBinding] Result set truncated from {} to {}", list.size(), MAX_RESULT_SIZE);
            return list.subList(0, MAX_RESULT_SIZE);
        }
        return list;
    }
}
