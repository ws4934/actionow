package com.actionow.project.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.project.dto.CreatePropRequest;
import com.actionow.project.dto.UpdatePropRequest;
import com.actionow.project.dto.PropQueryRequest;
import com.actionow.project.dto.PropDetailResponse;
import com.actionow.project.dto.PropListResponse;
import com.actionow.project.entity.Prop;

import java.util.List;
import java.util.Optional;

/**
 * 道具服务接口
 *
 * @author Actionow
 */
public interface PropService {

    /**
     * 创建道具
     */
    PropDetailResponse create(CreatePropRequest request, String workspaceId, String userId);

    /**
     * 创建道具（可控制是否跳过 Canvas 同步）
     */
    PropDetailResponse create(CreatePropRequest request, String workspaceId, String userId, boolean skipCanvasSync);

    /**
     * 批量创建道具
     */
    List<PropDetailResponse> batchCreate(List<CreatePropRequest> requests, String workspaceId, String userId);

    /**
     * 更新道具
     */
    PropDetailResponse update(String propId, UpdatePropRequest request, String userId);

    /**
     * 删除道具
     */
    void delete(String propId, String userId);

    /**
     * 获取道具详情
     */
    PropDetailResponse getById(String propId);

    /**
     * 根据ID查找道具实体（内部使用）
     * @return Optional，不存在或已删除返回 empty
     */
    Optional<Prop> findById(String propId);

    /**
     * 获取工作空间级道具
     */
    List<PropListResponse> listWorkspaceProps(String workspaceId);

    /**
     * 获取剧本级道具
     */
    List<PropListResponse> listScriptProps(String scriptId);

    /**
     * 获取剧本可用的所有道具
     */
    List<PropListResponse> listAvailableProps(String workspaceId, String scriptId);

    /**
     * 获取剧本可用的所有道具（支持模糊搜索）
     *
     * @param workspaceId 工作空间ID
     * @param scriptId 剧本ID
     * @param keyword 搜索关键词（匹配名称、描述）
     * @param limit 返回数量限制
     */
    List<PropListResponse> listAvailableProps(String workspaceId, String scriptId, String keyword, Integer limit);

    /**
     * 分页查询道具
     */
    Page<PropListResponse> queryProps(PropQueryRequest request, String workspaceId);

    /**
     * 设置道具封面
     */
    void setCover(String propId, String assetId, String userId);
}
