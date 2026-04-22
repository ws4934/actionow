package com.actionow.project.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.project.dto.CharacterDetailResponse;
import com.actionow.project.dto.SceneDetailResponse;
import com.actionow.project.dto.PropDetailResponse;
import com.actionow.project.dto.StyleDetailResponse;
import com.actionow.project.dto.asset.AssetResponse;
import com.actionow.project.dto.library.*;

/**
 * 公共资源库服务：供所有已登录用户浏览、复制系统公共资源
 *
 * @author Actionow
 */
public interface PublicLibraryService {

    // ==================== 浏览接口 ====================

    Page<LibraryCharacterResponse> listCharacters(LibraryQueryRequest request);

    LibraryCharacterResponse getCharacter(String id);

    Page<LibrarySceneResponse> listScenes(LibraryQueryRequest request);

    LibrarySceneResponse getScene(String id);

    Page<LibraryPropResponse> listProps(LibraryQueryRequest request);

    LibraryPropResponse getProp(String id);

    Page<LibraryStyleResponse> listStyles(LibraryQueryRequest request);

    LibraryStyleResponse getStyle(String id);

    Page<LibraryAssetResponse> listAssets(LibraryQueryRequest request);

    LibraryAssetResponse getAsset(String id);

    // ==================== 复制到工作空间 ====================

    /**
     * 将公共库角色复制到当前工作空间（scope=WORKSPACE）
     */
    CharacterDetailResponse copyCharacterToWorkspace(String characterId, String workspaceId, String userId);

    /**
     * 将公共库场景复制到当前工作空间
     */
    SceneDetailResponse copySceneToWorkspace(String sceneId, String workspaceId, String userId);

    /**
     * 将公共库道具复制到当前工作空间
     */
    PropDetailResponse copyPropToWorkspace(String propId, String workspaceId, String userId);

    /**
     * 将公共库风格复制到当前工作空间
     */
    StyleDetailResponse copyStyleToWorkspace(String styleId, String workspaceId, String userId);

    /**
     * 将公共库素材复制到当前工作空间
     */
    AssetResponse copyAssetToWorkspace(String assetId, String workspaceId, String userId);
}
