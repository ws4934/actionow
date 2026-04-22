package com.actionow.ai.feign;

import com.actionow.common.core.result.Result;
import com.actionow.common.core.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Project 服务 Feign 客户端降级工厂
 *
 * @author Actionow
 */
@Slf4j
@Component
public class ProjectFeignClientFallbackFactory implements FallbackFactory<ProjectFeignClient> {

    @Override
    public ProjectFeignClient create(Throwable cause) {
        log.error("[ProjectFeignClient] Fallback triggered, cause: {}", cause.getMessage());
        return new ProjectFeignClientFallback(cause);
    }

    /**
     * 降级实现
     */
    @Slf4j
    static class ProjectFeignClientFallback implements ProjectFeignClient {

        private final Throwable cause;

        ProjectFeignClientFallback(Throwable cause) {
            this.cause = cause;
        }

        private <T> Result<T> fallbackResult(String operation) {
            log.error("[ProjectFeignClient] {} failed: {}", operation, cause.getMessage());
            return Result.fail(ResultCode.SERVICE_UNAVAILABLE.getCode(), "Project服务暂时不可用: " + cause.getMessage());
        }

        private <T> Result<T> fallbackResultWithDefault(String operation, T defaultValue) {
            log.warn("[ProjectFeignClient] {} fallback with default value: {}", operation, cause.getMessage());
            return Result.success(defaultValue);
        }

        // ==================== Script ====================

        @Override
        public Result<Map<String, Object>> createScript(Map<String, Object> request) {
            return fallbackResult("createScript");
        }

        @Override
        public Result<List<String>> batchCreateScripts(List<Map<String, Object>> scripts) {
            return fallbackResult("batchCreateScripts");
        }

        @Override
        public Result<Void> updateScript(String scriptId, Map<String, Object> request) {
            return fallbackResult("updateScript");
        }

        @Override
        public Result<Void> deleteScript(String scriptId) {
            return fallbackResult("deleteScript");
        }

        @Override
        public Result<Void> batchDeleteScripts(List<String> scriptIds) {
            return fallbackResult("batchDeleteScripts");
        }

        @Override
        public Result<Map<String, Object>> getScript(String scriptId) {
            return fallbackResultWithDefault("getScript", Collections.emptyMap());
        }

        @Override
        public Result<List<Map<String, Object>>> listScripts(String workspaceId) {
            return fallbackResultWithDefault("listScripts", Collections.emptyList());
        }

        // ==================== Episode ====================

        @Override
        public Result<Map<String, Object>> createEpisode(Map<String, Object> request) {
            return fallbackResult("createEpisode");
        }

        @Override
        public Result<List<String>> batchCreateEpisodes(List<Map<String, Object>> episodes) {
            return fallbackResult("batchCreateEpisodes");
        }

        @Override
        public Result<Void> updateEpisode(String episodeId, Map<String, Object> request) {
            return fallbackResult("updateEpisode");
        }

        @Override
        public Result<Void> deleteEpisode(String episodeId) {
            return fallbackResult("deleteEpisode");
        }

        @Override
        public Result<Void> batchDeleteEpisodes(List<String> episodeIds) {
            return fallbackResult("batchDeleteEpisodes");
        }

        @Override
        public Result<Map<String, Object>> getEpisode(String episodeId) {
            return fallbackResultWithDefault("getEpisode", Collections.emptyMap());
        }

        @Override
        public Result<List<Map<String, Object>>> getEpisodesByScript(String scriptId) {
            return fallbackResultWithDefault("getEpisodesByScript", Collections.emptyList());
        }

        // ==================== Storyboard ====================

        @Override
        public Result<Map<String, Object>> createStoryboard(Map<String, Object> request) {
            return fallbackResult("createStoryboard");
        }

        @Override
        public Result<List<String>> batchCreateStoryboards(List<Map<String, Object>> storyboards) {
            return fallbackResult("batchCreateStoryboards");
        }

        @Override
        public Result<Void> updateStoryboard(String storyboardId, Map<String, Object> request) {
            return fallbackResult("updateStoryboard");
        }

        @Override
        public Result<Void> deleteStoryboard(String storyboardId) {
            return fallbackResult("deleteStoryboard");
        }

        @Override
        public Result<Void> batchDeleteStoryboards(List<String> storyboardIds) {
            return fallbackResult("batchDeleteStoryboards");
        }

        @Override
        public Result<Map<String, Object>> getStoryboard(String storyboardId) {
            return fallbackResultWithDefault("getStoryboard", Collections.emptyMap());
        }

        @Override
        public Result<List<Map<String, Object>>> getStoryboardsByEpisode(String episodeId) {
            return fallbackResultWithDefault("getStoryboardsByEpisode", Collections.emptyList());
        }

        // ==================== Character ====================

        @Override
        public Result<Map<String, Object>> createCharacter(Map<String, Object> request) {
            return fallbackResult("createCharacter");
        }

        @Override
        public Result<List<String>> batchCreateCharacters(List<Map<String, Object>> characters) {
            return fallbackResult("batchCreateCharacters");
        }

        @Override
        public Result<Void> updateCharacter(String characterId, Map<String, Object> request) {
            return fallbackResult("updateCharacter");
        }

        @Override
        public Result<Void> deleteCharacter(String characterId) {
            return fallbackResult("deleteCharacter");
        }

        @Override
        public Result<Void> batchDeleteCharacters(List<String> characterIds) {
            return fallbackResult("batchDeleteCharacters");
        }

        @Override
        public Result<Map<String, Object>> getCharacter(String characterId) {
            return fallbackResultWithDefault("getCharacter", Collections.emptyMap());
        }

        @Override
        public Result<List<Map<String, Object>>> getCharactersByScript(String scriptId) {
            return fallbackResultWithDefault("getCharactersByScript", Collections.emptyList());
        }

        // ==================== Scene ====================

        @Override
        public Result<Map<String, Object>> createScene(Map<String, Object> request) {
            return fallbackResult("createScene");
        }

        @Override
        public Result<List<String>> batchCreateScenes(List<Map<String, Object>> scenes) {
            return fallbackResult("batchCreateScenes");
        }

        @Override
        public Result<Void> updateScene(String sceneId, Map<String, Object> request) {
            return fallbackResult("updateScene");
        }

        @Override
        public Result<Void> deleteScene(String sceneId) {
            return fallbackResult("deleteScene");
        }

        @Override
        public Result<Void> batchDeleteScenes(List<String> sceneIds) {
            return fallbackResult("batchDeleteScenes");
        }

        @Override
        public Result<Map<String, Object>> getScene(String sceneId) {
            return fallbackResultWithDefault("getScene", Collections.emptyMap());
        }

        @Override
        public Result<List<Map<String, Object>>> getScenesByScript(String scriptId) {
            return fallbackResultWithDefault("getScenesByScript", Collections.emptyList());
        }

        // ==================== Prop ====================

        @Override
        public Result<Map<String, Object>> createProp(Map<String, Object> request) {
            return fallbackResult("createProp");
        }

        @Override
        public Result<List<String>> batchCreateProps(List<Map<String, Object>> props) {
            return fallbackResult("batchCreateProps");
        }

        @Override
        public Result<Void> updateProp(String propId, Map<String, Object> request) {
            return fallbackResult("updateProp");
        }

        @Override
        public Result<Void> deleteProp(String propId) {
            return fallbackResult("deleteProp");
        }

        @Override
        public Result<Void> batchDeleteProps(List<String> propIds) {
            return fallbackResult("batchDeleteProps");
        }

        @Override
        public Result<Map<String, Object>> getProp(String propId) {
            return fallbackResultWithDefault("getProp", Collections.emptyMap());
        }

        @Override
        public Result<List<Map<String, Object>>> getPropsByScript(String scriptId) {
            return fallbackResultWithDefault("getPropsByScript", Collections.emptyList());
        }

        // ==================== Style ====================

        @Override
        public Result<Map<String, Object>> createStyle(Map<String, Object> request) {
            return fallbackResult("createStyle");
        }

        @Override
        public Result<List<String>> batchCreateStyles(List<Map<String, Object>> styles) {
            return fallbackResult("batchCreateStyles");
        }

        @Override
        public Result<Void> updateStyle(String styleId, Map<String, Object> request) {
            return fallbackResult("updateStyle");
        }

        @Override
        public Result<Void> deleteStyle(String styleId) {
            return fallbackResult("deleteStyle");
        }

        @Override
        public Result<Void> batchDeleteStyles(List<String> styleIds) {
            return fallbackResult("batchDeleteStyles");
        }

        @Override
        public Result<Map<String, Object>> getStyle(String styleId) {
            return fallbackResultWithDefault("getStyle", Collections.emptyMap());
        }

        @Override
        public Result<List<Map<String, Object>>> getStylesByScript(String scriptId) {
            return fallbackResultWithDefault("getStylesByScript", Collections.emptyList());
        }

        // ==================== Asset ====================

        @Override
        public Result<Map<String, Object>> createAsset(Map<String, Object> request) {
            return fallbackResult("createAsset");
        }

        @Override
        public Result<List<String>> batchCreateAssets(List<Map<String, Object>> assets) {
            return fallbackResult("batchCreateAssets");
        }

        @Override
        public Result<Void> updateAsset(String assetId, Map<String, Object> request) {
            return fallbackResult("updateAsset");
        }

        @Override
        public Result<Void> deleteAsset(String assetId) {
            return fallbackResult("deleteAsset");
        }

        @Override
        public Result<Void> batchDeleteAssets(List<String> assetIds) {
            return fallbackResult("batchDeleteAssets");
        }

        @Override
        public Result<Map<String, Object>> getAsset(String assetId) {
            return fallbackResultWithDefault("getAsset", Collections.emptyMap());
        }

        @Override
        public Result<List<Map<String, Object>>> getAssetsByScript(String scriptId) {
            return fallbackResultWithDefault("getAssetsByScript", Collections.emptyList());
        }

        @Override
        public Result<List<Map<String, Object>>> listAssets(String workspaceId, String assetType) {
            return fallbackResultWithDefault("listAssets", Collections.emptyList());
        }

        @Override
        public Result<Void> updateAssetGenerationStatus(String assetId, String status, String errorMessage) {
            return fallbackResult("updateAssetGenerationStatus");
        }

        @Override
        public Result<Void> updateAssetFileInfo(String assetId, Map<String, Object> fileInfo) {
            return fallbackResult("updateAssetFileInfo");
        }

        @Override
        public Result<Map<String, List<Map<String, Object>>>> batchQueryEntities(Map<String, List<String>> request) {
            return fallbackResultWithDefault("batchQueryEntities", Collections.emptyMap());
        }

        @Override
        public Result<List<Map<String, Object>>> batchGetAssets(List<String> assetIds) {
            return fallbackResult("batchGetAssets");
        }

        @Override
        public Result<String> getAssetDownloadUrl(String assetId, int expireSeconds) {
            return fallbackResult("getAssetDownloadUrl");
        }
    }
}
