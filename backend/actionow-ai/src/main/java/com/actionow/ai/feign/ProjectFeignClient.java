package com.actionow.ai.feign;

import com.actionow.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Project 服务 Feign 客户端
 * 用于 AI 服务操作剧本相关实体（Script, Episode, Storyboard, Character, Scene, Prop, Style, Asset）
 *
 * @author Actionow
 */
@FeignClient(name = "actionow-project", contextId = "aiProjectFeignClient",
        path = "/internal/project", fallbackFactory = ProjectFeignClientFallbackFactory.class)
public interface ProjectFeignClient {

    // ==================== Script 剧本 ====================

    /**
     * 创建剧本
     */
    @PostMapping("/scripts")
    Result<Map<String, Object>> createScript(@RequestBody Map<String, Object> request);

    /**
     * 批量创建剧本
     */
    @PostMapping("/scripts/batch")
    Result<List<String>> batchCreateScripts(@RequestBody List<Map<String, Object>> scripts);

    /**
     * 更新剧本
     */
    @PutMapping("/scripts/{scriptId}")
    Result<Void> updateScript(
            @PathVariable("scriptId") String scriptId,
            @RequestBody Map<String, Object> request);

    /**
     * 删除剧本
     */
    @DeleteMapping("/scripts/{scriptId}")
    Result<Void> deleteScript(@PathVariable("scriptId") String scriptId);

    /**
     * 批量删除剧本
     */
    @DeleteMapping("/scripts/batch")
    Result<Void> batchDeleteScripts(@RequestBody List<String> scriptIds);

    /**
     * 获取剧本详情
     */
    @GetMapping("/scripts/{scriptId}")
    Result<Map<String, Object>> getScript(@PathVariable("scriptId") String scriptId);

    /**
     * 获取工作空间下的剧本列表
     */
    @GetMapping("/scripts")
    Result<List<Map<String, Object>>> listScripts(@RequestParam("workspaceId") String workspaceId);

    // ==================== Episode 章节 ====================

    /**
     * 创建章节
     */
    @PostMapping("/episodes")
    Result<Map<String, Object>> createEpisode(@RequestBody Map<String, Object> request);

    /**
     * 批量创建章节
     */
    @PostMapping("/episodes/batch")
    Result<List<String>> batchCreateEpisodes(@RequestBody List<Map<String, Object>> episodes);

    /**
     * 更新章节
     */
    @PutMapping("/episodes/{episodeId}")
    Result<Void> updateEpisode(
            @PathVariable("episodeId") String episodeId,
            @RequestBody Map<String, Object> request);

    /**
     * 删除章节
     */
    @DeleteMapping("/episodes/{episodeId}")
    Result<Void> deleteEpisode(@PathVariable("episodeId") String episodeId);

    /**
     * 批量删除章节
     */
    @DeleteMapping("/episodes/batch")
    Result<Void> batchDeleteEpisodes(@RequestBody List<String> episodeIds);

    /**
     * 获取章节详情
     */
    @GetMapping("/episodes/{episodeId}")
    Result<Map<String, Object>> getEpisode(@PathVariable("episodeId") String episodeId);

    /**
     * 获取剧本下的所有章节
     */
    @GetMapping("/scripts/{scriptId}/episodes")
    Result<List<Map<String, Object>>> getEpisodesByScript(@PathVariable("scriptId") String scriptId);

    // ==================== Storyboard 分镜 ====================

    /**
     * 创建分镜
     */
    @PostMapping("/storyboards")
    Result<Map<String, Object>> createStoryboard(@RequestBody Map<String, Object> request);

    /**
     * 批量创建分镜
     */
    @PostMapping("/storyboards/batch")
    Result<List<String>> batchCreateStoryboards(@RequestBody List<Map<String, Object>> storyboards);

    /**
     * 更新分镜
     */
    @PutMapping("/storyboards/{storyboardId}")
    Result<Void> updateStoryboard(
            @PathVariable("storyboardId") String storyboardId,
            @RequestBody Map<String, Object> request);

    /**
     * 删除分镜
     */
    @DeleteMapping("/storyboards/{storyboardId}")
    Result<Void> deleteStoryboard(@PathVariable("storyboardId") String storyboardId);

    /**
     * 批量删除分镜
     */
    @DeleteMapping("/storyboards/batch")
    Result<Void> batchDeleteStoryboards(@RequestBody List<String> storyboardIds);

    /**
     * 获取分镜详情
     */
    @GetMapping("/storyboards/{storyboardId}")
    Result<Map<String, Object>> getStoryboard(@PathVariable("storyboardId") String storyboardId);

    /**
     * 获取章节下的所有分镜
     */
    @GetMapping("/episodes/{episodeId}/storyboards")
    Result<List<Map<String, Object>>> getStoryboardsByEpisode(@PathVariable("episodeId") String episodeId);

    // ==================== Character 角色 ====================

    /**
     * 创建角色
     */
    @PostMapping("/characters")
    Result<Map<String, Object>> createCharacter(@RequestBody Map<String, Object> request);

    /**
     * 批量创建角色
     */
    @PostMapping("/characters/batch")
    Result<List<String>> batchCreateCharacters(@RequestBody List<Map<String, Object>> characters);

    /**
     * 更新角色
     */
    @PutMapping("/characters/{characterId}")
    Result<Void> updateCharacter(
            @PathVariable("characterId") String characterId,
            @RequestBody Map<String, Object> request);

    /**
     * 删除角色
     */
    @DeleteMapping("/characters/{characterId}")
    Result<Void> deleteCharacter(@PathVariable("characterId") String characterId);

    /**
     * 批量删除角色
     */
    @DeleteMapping("/characters/batch")
    Result<Void> batchDeleteCharacters(@RequestBody List<String> characterIds);

    /**
     * 获取角色详情
     */
    @GetMapping("/characters/{characterId}")
    Result<Map<String, Object>> getCharacter(@PathVariable("characterId") String characterId);

    /**
     * 获取剧本下的所有角色
     */
    @GetMapping("/scripts/{scriptId}/characters")
    Result<List<Map<String, Object>>> getCharactersByScript(@PathVariable("scriptId") String scriptId);

    // ==================== Scene 场景 ====================

    /**
     * 创建场景
     */
    @PostMapping("/scenes")
    Result<Map<String, Object>> createScene(@RequestBody Map<String, Object> request);

    /**
     * 批量创建场景
     */
    @PostMapping("/scenes/batch")
    Result<List<String>> batchCreateScenes(@RequestBody List<Map<String, Object>> scenes);

    /**
     * 更新场景
     */
    @PutMapping("/scenes/{sceneId}")
    Result<Void> updateScene(
            @PathVariable("sceneId") String sceneId,
            @RequestBody Map<String, Object> request);

    /**
     * 删除场景
     */
    @DeleteMapping("/scenes/{sceneId}")
    Result<Void> deleteScene(@PathVariable("sceneId") String sceneId);

    /**
     * 批量删除场景
     */
    @DeleteMapping("/scenes/batch")
    Result<Void> batchDeleteScenes(@RequestBody List<String> sceneIds);

    /**
     * 获取场景详情
     */
    @GetMapping("/scenes/{sceneId}")
    Result<Map<String, Object>> getScene(@PathVariable("sceneId") String sceneId);

    /**
     * 获取剧本下的所有场景
     */
    @GetMapping("/scripts/{scriptId}/scenes")
    Result<List<Map<String, Object>>> getScenesByScript(@PathVariable("scriptId") String scriptId);

    // ==================== Prop 道具 ====================

    /**
     * 创建道具
     */
    @PostMapping("/props")
    Result<Map<String, Object>> createProp(@RequestBody Map<String, Object> request);

    /**
     * 批量创建道具
     */
    @PostMapping("/props/batch")
    Result<List<String>> batchCreateProps(@RequestBody List<Map<String, Object>> props);

    /**
     * 更新道具
     */
    @PutMapping("/props/{propId}")
    Result<Void> updateProp(
            @PathVariable("propId") String propId,
            @RequestBody Map<String, Object> request);

    /**
     * 删除道具
     */
    @DeleteMapping("/props/{propId}")
    Result<Void> deleteProp(@PathVariable("propId") String propId);

    /**
     * 批量删除道具
     */
    @DeleteMapping("/props/batch")
    Result<Void> batchDeleteProps(@RequestBody List<String> propIds);

    /**
     * 获取道具详情
     */
    @GetMapping("/props/{propId}")
    Result<Map<String, Object>> getProp(@PathVariable("propId") String propId);

    /**
     * 获取剧本下的所有道具
     */
    @GetMapping("/scripts/{scriptId}/props")
    Result<List<Map<String, Object>>> getPropsByScript(@PathVariable("scriptId") String scriptId);

    // ==================== Style 风格 ====================

    /**
     * 创建风格
     */
    @PostMapping("/styles")
    Result<Map<String, Object>> createStyle(@RequestBody Map<String, Object> request);

    /**
     * 批量创建风格
     */
    @PostMapping("/styles/batch")
    Result<List<String>> batchCreateStyles(@RequestBody List<Map<String, Object>> styles);

    /**
     * 更新风格
     */
    @PutMapping("/styles/{styleId}")
    Result<Void> updateStyle(
            @PathVariable("styleId") String styleId,
            @RequestBody Map<String, Object> request);

    /**
     * 删除风格
     */
    @DeleteMapping("/styles/{styleId}")
    Result<Void> deleteStyle(@PathVariable("styleId") String styleId);

    /**
     * 批量删除风格
     */
    @DeleteMapping("/styles/batch")
    Result<Void> batchDeleteStyles(@RequestBody List<String> styleIds);

    /**
     * 获取风格详情
     */
    @GetMapping("/styles/{styleId}")
    Result<Map<String, Object>> getStyle(@PathVariable("styleId") String styleId);

    /**
     * 获取剧本下的所有风格
     */
    @GetMapping("/scripts/{scriptId}/styles")
    Result<List<Map<String, Object>>> getStylesByScript(@PathVariable("scriptId") String scriptId);

    // ==================== Asset 素材 ====================

    /**
     * 创建素材
     */
    @PostMapping("/assets")
    Result<Map<String, Object>> createAsset(@RequestBody Map<String, Object> request);

    /**
     * 批量创建素材
     */
    @PostMapping("/assets/batch")
    Result<List<String>> batchCreateAssets(@RequestBody List<Map<String, Object>> assets);

    /**
     * 更新素材
     */
    @PutMapping("/assets/{assetId}")
    Result<Void> updateAsset(
            @PathVariable("assetId") String assetId,
            @RequestBody Map<String, Object> request);

    /**
     * 删除素材
     */
    @DeleteMapping("/assets/{assetId}")
    Result<Void> deleteAsset(@PathVariable("assetId") String assetId);

    /**
     * 批量删除素材
     */
    @DeleteMapping("/assets/batch")
    Result<Void> batchDeleteAssets(@RequestBody List<String> assetIds);

    /**
     * 获取素材详情
     */
    @GetMapping("/assets/{assetId}")
    Result<Map<String, Object>> getAsset(@PathVariable("assetId") String assetId);

    /**
     * 获取剧本下的所有素材
     */
    @GetMapping("/scripts/{scriptId}/assets")
    Result<List<Map<String, Object>>> getAssetsByScript(@PathVariable("scriptId") String scriptId);

    /**
     * 获取工作空间下的素材列表
     */
    @GetMapping("/assets")
    Result<List<Map<String, Object>>> listAssets(
            @RequestParam("workspaceId") String workspaceId,
            @RequestParam(value = "assetType", required = false) String assetType);

    /**
     * 更新素材生成状态
     */
    @PutMapping("/assets/{assetId}/generation-status")
    Result<Void> updateAssetGenerationStatus(
            @PathVariable("assetId") String assetId,
            @RequestParam("status") String status,
            @RequestParam(value = "errorMessage", required = false) String errorMessage);

    /**
     * 更新素材文件信息（AI生成完成后）
     */
    @PutMapping("/assets/{assetId}/file-info")
    Result<Void> updateAssetFileInfo(
            @PathVariable("assetId") String assetId,
            @RequestBody Map<String, Object> fileInfo);

    // ==================== 实体批量查询（AI模块专用） ====================

    /**
     * 统一批量查询多类型实体
     * 用于输入解析器根据 inputSchema 批量获取角色、场景、道具、风格等实体数据
     *
     * @param request 按类型分组的实体ID列表，如 {characterIds: [...], sceneIds: [...]}
     * @return 按类型分组的实体数据
     */
    @PostMapping("/entities/batch-query")
    Result<Map<String, List<Map<String, Object>>>> batchQueryEntities(@RequestBody Map<String, List<String>> request);

    // ==================== 素材批量查询（AI模块专用） ====================

    /**
     * 批量获取素材信息
     * 用于 AI 模块解析输入中的素材ID
     *
     * @param assetIds 素材ID列表
     * @return 素材信息列表
     */
    @PostMapping("/assets/batch-get")
    Result<List<Map<String, Object>>> batchGetAssets(@RequestBody List<String> assetIds);

    /**
     * 获取素材的下载URL（带过期时间）
     *
     * @param assetId       素材ID
     * @param expireSeconds 过期时间（秒）
     * @return 预签名下载URL
     */
    @GetMapping("/assets/{assetId}/download-url")
    Result<String> getAssetDownloadUrl(
            @PathVariable("assetId") String assetId,
            @RequestParam(value = "expireSeconds", defaultValue = "3600") int expireSeconds);
}
