package com.actionow.project.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.project.dto.CreateEpisodeRequest;
import com.actionow.project.dto.UpdateEpisodeRequest;
import com.actionow.project.dto.EpisodeQueryRequest;
import com.actionow.project.dto.EpisodeDetailResponse;
import com.actionow.project.dto.EpisodeListResponse;
import com.actionow.project.entity.Episode;

import java.util.List;
import java.util.Optional;

/**
 * 剧集服务接口
 *
 * @author Actionow
 */
public interface EpisodeService {

    /**
     * 创建剧集
     */
    EpisodeDetailResponse create(String scriptId, CreateEpisodeRequest request, String userId);

    /**
     * 创建剧集（可控制是否跳过 Canvas 同步）
     */
    EpisodeDetailResponse create(String scriptId, CreateEpisodeRequest request, String userId, boolean skipCanvasSync);

    /**
     * 批量创建剧集
     */
    List<EpisodeDetailResponse> batchCreate(String scriptId, List<CreateEpisodeRequest> requests, String userId);

    /**
     * 更新剧集
     */
    EpisodeDetailResponse update(String episodeId, UpdateEpisodeRequest request, String userId);

    /**
     * 删除剧集
     */
    void delete(String episodeId, String userId);

    /**
     * 获取剧集详情
     */
    EpisodeDetailResponse getById(String episodeId);

    /**
     * 获取剧本的所有剧集
     */
    List<EpisodeListResponse> listByScript(String scriptId);

    /**
     * 获取剧本的所有剧集（支持模糊搜索）
     *
     * @param scriptId 剧本ID
     * @param keyword 搜索关键词（匹配标题、简介）
     * @param limit 返回数量限制
     */
    List<EpisodeListResponse> listByScript(String scriptId, String keyword, Integer limit);

    /**
     * 分页查询剧集
     */
    Page<EpisodeListResponse> queryEpisodes(EpisodeQueryRequest request, String workspaceId);

    /**
     * 调整剧集顺序
     */
    void reorder(String scriptId, List<String> episodeIds, String userId);

    /**
     * 更新剧集状态
     */
    void updateStatus(String episodeId, String status, String userId);

    /**
     * 根据ID查找剧集
     */
    Optional<Episode> findById(String episodeId);

    /**
     * 设置剧集封面
     */
    void setCover(String episodeId, String assetId, String userId);
}
