package com.actionow.project.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.project.dto.CreateStyleRequest;
import com.actionow.project.dto.UpdateStyleRequest;
import com.actionow.project.dto.StyleQueryRequest;
import com.actionow.project.dto.StyleDetailResponse;
import com.actionow.project.dto.StyleListResponse;
import com.actionow.project.entity.Style;

import java.util.List;
import java.util.Optional;

/**
 * 风格服务接口
 *
 * @author Actionow
 */
public interface StyleService {

    /**
     * 创建风格
     */
    StyleDetailResponse create(CreateStyleRequest request, String workspaceId, String userId);

    /**
     * 创建风格（可控制是否跳过 Canvas 同步）
     */
    StyleDetailResponse create(CreateStyleRequest request, String workspaceId, String userId, boolean skipCanvasSync);

    /**
     * 批量创建风格
     */
    List<StyleDetailResponse> batchCreate(List<CreateStyleRequest> requests, String workspaceId, String userId);

    /**
     * 更新风格
     */
    StyleDetailResponse update(String styleId, UpdateStyleRequest request, String userId);

    /**
     * 删除风格
     */
    void delete(String styleId, String userId);

    /**
     * 获取风格详情
     */
    StyleDetailResponse getById(String styleId);

    /**
     * 根据ID查找风格实体（内部使用）
     * @return Optional，不存在或已删除返回 empty
     */
    Optional<Style> findById(String styleId);

    /**
     * 获取工作空间级风格
     */
    List<StyleListResponse> listWorkspaceStyles(String workspaceId);

    /**
     * 获取剧本级风格
     */
    List<StyleListResponse> listScriptStyles(String scriptId);

    /**
     * 获取剧本可用的所有风格
     */
    List<StyleListResponse> listAvailableStyles(String workspaceId, String scriptId);

    /**
     * 获取剧本可用的所有风格（支持模糊搜索）
     *
     * @param workspaceId 工作空间ID
     * @param scriptId 剧本ID
     * @param keyword 搜索关键词（匹配名称、描述）
     * @param limit 返回数量限制
     */
    List<StyleListResponse> listAvailableStyles(String workspaceId, String scriptId, String keyword, Integer limit);

    /**
     * 分页查询风格
     */
    Page<StyleListResponse> queryStyles(StyleQueryRequest request, String workspaceId);

    /**
     * 设置风格封面
     */
    void setCover(String styleId, String assetId, String userId);
}
