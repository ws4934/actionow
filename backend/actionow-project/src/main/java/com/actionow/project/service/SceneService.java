package com.actionow.project.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.project.dto.CreateSceneRequest;
import com.actionow.project.dto.UpdateSceneRequest;
import com.actionow.project.dto.SceneQueryRequest;
import com.actionow.project.dto.SceneDetailResponse;
import com.actionow.project.dto.SceneListResponse;
import com.actionow.project.entity.Scene;

import java.util.List;
import java.util.Optional;

/**
 * 场景服务接口
 *
 * @author Actionow
 */
public interface SceneService {

    /**
     * 创建场景
     */
    SceneDetailResponse create(CreateSceneRequest request, String workspaceId, String userId);

    /**
     * 创建场景（可控制是否跳过 Canvas 同步）
     */
    SceneDetailResponse create(CreateSceneRequest request, String workspaceId, String userId, boolean skipCanvasSync);

    /**
     * 批量创建场景
     */
    List<SceneDetailResponse> batchCreate(List<CreateSceneRequest> requests, String workspaceId, String userId);

    /**
     * 更新场景
     */
    SceneDetailResponse update(String sceneId, UpdateSceneRequest request, String userId);

    /**
     * 删除场景
     */
    void delete(String sceneId, String userId);

    /**
     * 获取场景详情
     */
    SceneDetailResponse getById(String sceneId);

    /**
     * 根据ID查找场景实体（内部使用）
     * @return Optional，不存在或已删除返回 empty
     */
    Optional<Scene> findById(String sceneId);

    /**
     * 获取工作空间级场景
     */
    List<SceneListResponse> listWorkspaceScenes(String workspaceId);

    /**
     * 获取剧本级场景
     */
    List<SceneListResponse> listScriptScenes(String scriptId);

    /**
     * 获取剧本可用的所有场景
     */
    List<SceneListResponse> listAvailableScenes(String workspaceId, String scriptId);

    /**
     * 获取剧本可用的所有场景（支持模糊搜索）
     *
     * @param workspaceId 工作空间ID
     * @param scriptId 剧本ID
     * @param keyword 搜索关键词（匹配名称、描述）
     * @param limit 返回数量限制
     */
    List<SceneListResponse> listAvailableScenes(String workspaceId, String scriptId, String keyword, Integer limit);

    /**
     * 分页查询场景
     */
    Page<SceneListResponse> queryScenes(SceneQueryRequest request, String workspaceId);

    /**
     * 设置场景封面
     */
    void setCover(String sceneId, String assetId, String userId);
}
