package com.actionow.project.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.project.dto.CreateStoryboardRequest;
import com.actionow.project.dto.UpdateStoryboardRequest;
import com.actionow.project.dto.StoryboardQueryRequest;
import com.actionow.project.dto.StoryboardDetailResponse;
import com.actionow.project.dto.StoryboardListResponse;
import com.actionow.project.entity.Storyboard;

import java.util.List;
import java.util.Optional;

/**
 * 分镜服务接口
 *
 * @author Actionow
 */
public interface StoryboardService {

    /**
     * 创建分镜
     */
    StoryboardDetailResponse create(String episodeId, CreateStoryboardRequest request, String userId);

    /**
     * 创建分镜（可控制是否跳过 Canvas 同步）
     */
    StoryboardDetailResponse create(String episodeId, CreateStoryboardRequest request, String userId, boolean skipCanvasSync);

    /**
     * 批量创建分镜
     */
    List<StoryboardDetailResponse> batchCreate(String episodeId, List<CreateStoryboardRequest> requests, String userId);

    /**
     * 更新分镜
     */
    StoryboardDetailResponse update(String storyboardId, UpdateStoryboardRequest request, String userId);

    /**
     * 删除分镜
     */
    void delete(String storyboardId, String userId);

    /**
     * 获取分镜详情
     */
    StoryboardDetailResponse getById(String storyboardId);

    /**
     * 根据ID查找分镜实体（内部使用）
     * @return Optional，不存在或已删除返回 empty
     */
    Optional<Storyboard> findById(String storyboardId);

    /**
     * 获取剧集的所有分镜
     */
    List<StoryboardListResponse> listByEpisode(String episodeId);

    /**
     * 获取剧集的所有分镜（支持模糊搜索）
     *
     * @param episodeId 剧集ID
     * @param keyword 搜索关键词（匹配标题、描述）
     * @param limit 返回数量限制
     */
    List<StoryboardListResponse> listByEpisode(String episodeId, String keyword, Integer limit);

    /**
     * 获取剧本的所有分镜
     */
    List<StoryboardListResponse> listByScript(String scriptId);

    /**
     * 分页查询分镜
     */
    Page<StoryboardListResponse> queryStoryboards(StoryboardQueryRequest request, String workspaceId);

    /**
     * 调整分镜顺序
     */
    void reorder(String episodeId, List<String> storyboardIds, String userId);

    /**
     * 更新分镜状态
     */
    void updateStatus(String storyboardId, String status, String userId);

    /**
     * 设置分镜封面
     */
    void setCover(String storyboardId, String assetId, String userId);
}
