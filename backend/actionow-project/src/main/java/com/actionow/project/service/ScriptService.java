package com.actionow.project.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.project.dto.CreateScriptRequest;
import com.actionow.project.dto.UpdateScriptRequest;
import com.actionow.project.dto.ScriptQueryRequest;
import com.actionow.project.dto.ScriptDetailResponse;
import com.actionow.project.dto.ScriptListResponse;
import com.actionow.project.entity.Script;

import java.util.List;
import java.util.Optional;

/**
 * 剧本服务接口
 *
 * @author Actionow
 */
public interface ScriptService {

    /**
     * 创建剧本
     */
    ScriptDetailResponse create(CreateScriptRequest request, String workspaceId, String userId);

    /**
     * 创建剧本（可控制是否跳过 Canvas 同步）
     */
    ScriptDetailResponse create(CreateScriptRequest request, String workspaceId, String userId, boolean skipCanvasSync);

    /**
     * 更新剧本
     */
    ScriptDetailResponse update(String scriptId, UpdateScriptRequest request, String userId);

    /**
     * 删除剧本
     */
    void delete(String scriptId, String userId);

    /**
     * 获取剧本详情
     */
    ScriptDetailResponse getById(String scriptId);

    /**
     * 根据ID获取实体
     */
    Optional<Script> findById(String scriptId);

    /**
     * 获取工作空间的剧本列表
     */
    List<ScriptListResponse> listByWorkspace(String workspaceId);

    /**
     * 按状态筛选剧本
     */
    List<ScriptListResponse> listByStatus(String workspaceId, String status);

    /**
     * 分页查询剧本
     */
    Page<ScriptListResponse> queryScripts(ScriptQueryRequest request, String workspaceId);

    /**
     * 更新剧本状态
     */
    void updateStatus(String scriptId, String status, String userId);

    /**
     * 更新剧本内容
     */
    void updateContent(String scriptId, String content, String userId);

    /**
     * 归档剧本
     */
    void archive(String scriptId, String userId);

    /**
     * 设置剧本封面
     */
    void setCover(String scriptId, String assetId, String userId);
}
