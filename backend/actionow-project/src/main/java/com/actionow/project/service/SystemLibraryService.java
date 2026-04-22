package com.actionow.project.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.project.dto.library.*;

/**
 * 系统管理员资源库服务：管理公共资源的发布/下架
 *
 * @author Actionow
 */
public interface SystemLibraryService {

    // ==================== 角色管理 ====================

    Page<SystemCharacterResponse> listSystemCharacters(SystemLibraryQueryRequest request);

    void publishCharacter(String id, PublishResourceRequest request, String operatorId);

    void unpublishCharacter(String id, String operatorId);

    // ==================== 场景管理 ====================

    Page<SystemSceneResponse> listSystemScenes(SystemLibraryQueryRequest request);

    void publishScene(String id, PublishResourceRequest request, String operatorId);

    void unpublishScene(String id, String operatorId);

    // ==================== 道具管理 ====================

    Page<SystemPropResponse> listSystemProps(SystemLibraryQueryRequest request);

    void publishProp(String id, PublishResourceRequest request, String operatorId);

    void unpublishProp(String id, String operatorId);

    // ==================== 风格管理 ====================

    Page<SystemStyleResponse> listSystemStyles(SystemLibraryQueryRequest request);

    void publishStyle(String id, PublishResourceRequest request, String operatorId);

    void unpublishStyle(String id, String operatorId);

    // ==================== 素材管理 ====================

    Page<SystemAssetResponse> listSystemAssets(SystemLibraryQueryRequest request);

    void publishAsset(String id, PublishResourceRequest request, String operatorId);

    void unpublishAsset(String id, String operatorId);
}
