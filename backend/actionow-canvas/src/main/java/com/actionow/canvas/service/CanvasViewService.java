package com.actionow.canvas.service;

import com.actionow.canvas.dto.view.CreateViewRequest;
import com.actionow.canvas.dto.view.UpdateViewRequest;
import com.actionow.canvas.dto.view.ViewResponse;

import java.util.List;

/**
 * 画布视图服务接口
 *
 * @author Actionow
 */
public interface CanvasViewService {

    /**
     * 初始化画布的预设视图（创建画布时调用）
     *
     * @param canvasId    画布ID
     * @param workspaceId 工作空间ID
     */
    void initPresetViews(String canvasId, String workspaceId);

    /**
     * 获取画布的所有视图
     */
    List<ViewResponse> listViews(String canvasId);

    /**
     * 获取单个视图
     */
    ViewResponse getView(String viewId);

    /**
     * 获取画布的默认视图
     */
    ViewResponse getDefaultView(String canvasId);

    /**
     * 根据视图键获取视图
     */
    ViewResponse getViewByKey(String canvasId, String viewKey);

    /**
     * 创建自定义视图
     */
    ViewResponse createCustomView(CreateViewRequest request, String userId);

    /**
     * 更新视图
     */
    ViewResponse updateView(String viewId, UpdateViewRequest request, String userId);

    /**
     * 删除自定义视图（预设视图不可删除）
     */
    void deleteView(String viewId, String userId);

    /**
     * 设置默认视图
     */
    void setDefaultView(String canvasId, String viewId, String userId);
}
