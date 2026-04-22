package com.actionow.canvas.service.impl;

import com.actionow.canvas.constant.CanvasConstants;
import com.actionow.canvas.dto.view.CreateViewRequest;
import com.actionow.canvas.dto.view.UpdateViewRequest;
import com.actionow.canvas.dto.view.ViewResponse;
import com.actionow.canvas.entity.CanvasView;
import com.actionow.canvas.mapper.CanvasViewMapper;
import com.actionow.canvas.service.CanvasViewService;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.id.UuidGenerator;
import com.actionow.common.core.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 画布视图服务实现
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CanvasViewServiceImpl implements CanvasViewService {

    private final CanvasViewMapper viewMapper;

    /**
     * 预设视图配置
     */
    private static final List<PresetViewConfig> PRESET_VIEWS = List.of(
            new PresetViewConfig(CanvasConstants.ViewKey.SCRIPT, "剧本视图", "script", CanvasConstants.EntityType.SCRIPT,
                    CanvasConstants.VisibleEntityTypes.SCRIPT_VIEW, 1, true),
            new PresetViewConfig(CanvasConstants.ViewKey.EPISODE, "剧集视图", "episode", CanvasConstants.EntityType.EPISODE,
                    CanvasConstants.VisibleEntityTypes.EPISODE_VIEW, 2, false),
            new PresetViewConfig(CanvasConstants.ViewKey.STORYBOARD, "分镜视图", "storyboard", CanvasConstants.EntityType.STORYBOARD,
                    CanvasConstants.VisibleEntityTypes.STORYBOARD_VIEW, 3, false),
            new PresetViewConfig(CanvasConstants.ViewKey.CHARACTER, "角色视图", "character", CanvasConstants.EntityType.CHARACTER,
                    CanvasConstants.VisibleEntityTypes.CHARACTER_VIEW, 4, false),
            new PresetViewConfig(CanvasConstants.ViewKey.SCENE, "场景视图", "scene", CanvasConstants.EntityType.SCENE,
                    CanvasConstants.VisibleEntityTypes.SCENE_VIEW, 5, false),
            new PresetViewConfig(CanvasConstants.ViewKey.PROP, "道具视图", "prop", CanvasConstants.EntityType.PROP,
                    CanvasConstants.VisibleEntityTypes.PROP_VIEW, 6, false),
            new PresetViewConfig(CanvasConstants.ViewKey.ASSET, "素材视图", "asset", CanvasConstants.EntityType.ASSET,
                    CanvasConstants.VisibleEntityTypes.ASSET_VIEW, 7, false)
    );

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void initPresetViews(String canvasId, String workspaceId) {
        for (PresetViewConfig config : PRESET_VIEWS) {
            CanvasView view = new CanvasView();
            view.setId(UuidGenerator.generateUuidV7());
            view.setWorkspaceId(workspaceId);
            view.setCanvasId(canvasId);
            view.setViewKey(config.viewKey);
            view.setName(config.name);
            view.setIcon(config.icon);
            view.setViewType(CanvasConstants.ViewType.PRESET);
            view.setRootEntityType(config.rootEntityType);
            view.setVisibleEntityTypes(new ArrayList<>(config.visibleEntityTypes));
            view.setVisibleLayers(new ArrayList<>(config.visibleEntityTypes));
            view.setSequence(config.sequence);
            view.setIsDefault(config.isDefault);

            viewMapper.insert(view);
        }

        log.info("初始化预设视图: canvasId={}, count={}", canvasId, PRESET_VIEWS.size());
    }

    @Override
    public List<ViewResponse> listViews(String canvasId) {
        List<CanvasView> views = viewMapper.selectByCanvasId(canvasId);
        return views.stream()
                .map(ViewResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public ViewResponse getView(String viewId) {
        CanvasView view = viewMapper.selectById(viewId);
        if (view == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "视图不存在");
        }
        return ViewResponse.fromEntity(view);
    }

    @Override
    public ViewResponse getDefaultView(String canvasId) {
        CanvasView view = viewMapper.selectDefaultView(canvasId);
        if (view == null) {
            // 如果没有默认视图，返回 SCRIPT 视图
            view = viewMapper.selectByCanvasIdAndViewKey(canvasId, CanvasConstants.ViewKey.SCRIPT);
        }
        if (view == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "视图不存在");
        }
        return ViewResponse.fromEntity(view);
    }

    @Override
    public ViewResponse getViewByKey(String canvasId, String viewKey) {
        CanvasView view = viewMapper.selectByCanvasIdAndViewKey(canvasId, viewKey);
        if (view == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "视图不存在");
        }
        return ViewResponse.fromEntity(view);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ViewResponse createCustomView(CreateViewRequest request, String userId) {
        // 检查视图键是否已存在
        if (viewMapper.existsByCanvasIdAndViewKey(request.getCanvasId(), request.getViewKey())) {
            throw new BusinessException(ResultCode.ALREADY_EXISTS, "视图键已存在");
        }

        CanvasView view = new CanvasView();
        view.setId(UuidGenerator.generateUuidV7());
        view.setCanvasId(request.getCanvasId());
        view.setViewKey(request.getViewKey());
        view.setName(request.getName());
        view.setIcon(request.getIcon());
        view.setViewType(CanvasConstants.ViewType.CUSTOM);
        view.setRootEntityType(request.getRootEntityType());

        if (request.getVisibleEntityTypes() != null) {
            view.setVisibleEntityTypes(new ArrayList<>(request.getVisibleEntityTypes()));
        }
        if (request.getVisibleLayers() != null) {
            view.setVisibleLayers(new ArrayList<>(request.getVisibleLayers()));
        }
        if (request.getFilterConfig() != null) {
            view.setFilterConfig(request.getFilterConfig());
        }

        view.setLayoutStrategy(request.getLayoutStrategy());
        view.setSequence(request.getSequence() != null ? request.getSequence() : 100);
        view.setIsDefault(false);

        viewMapper.insert(view);

        log.info("创建自定义视图: viewId={}, canvasId={}, viewKey={}",
                view.getId(), request.getCanvasId(), request.getViewKey());

        return ViewResponse.fromEntity(view);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ViewResponse updateView(String viewId, UpdateViewRequest request, String userId) {
        CanvasView view = viewMapper.selectById(viewId);
        if (view == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "视图不存在");
        }

        if (StringUtils.hasText(request.getName())) {
            view.setName(request.getName());
        }
        if (request.getIcon() != null) {
            view.setIcon(request.getIcon());
        }
        if (request.getRootEntityType() != null) {
            view.setRootEntityType(request.getRootEntityType());
        }
        if (request.getVisibleEntityTypes() != null) {
            view.setVisibleEntityTypes(new ArrayList<>(request.getVisibleEntityTypes()));
        }
        if (request.getVisibleLayers() != null) {
            view.setVisibleLayers(new ArrayList<>(request.getVisibleLayers()));
        }
        if (request.getFilterConfig() != null) {
            view.setFilterConfig(request.getFilterConfig());
        }
        if (request.getViewport() != null) {
            view.setViewport(request.getViewport());
        }
        if (request.getLayoutStrategy() != null) {
            view.setLayoutStrategy(request.getLayoutStrategy());
        }
        if (request.getSequence() != null) {
            view.setSequence(request.getSequence());
        }

        viewMapper.updateById(view);

        // 处理设置为默认视图
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            setDefaultView(view.getCanvasId(), viewId, userId);
        }

        log.info("更新视图: viewId={}", viewId);

        return ViewResponse.fromEntity(view);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteView(String viewId, String userId) {
        CanvasView view = viewMapper.selectById(viewId);
        if (view == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "视图不存在");
        }

        // 预设视图不可删除
        if (CanvasConstants.ViewType.PRESET.equals(view.getViewType())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "预设视图不可删除");
        }

        viewMapper.deleteById(viewId);

        log.info("删除视图: viewId={}", viewId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefaultView(String canvasId, String viewId, String userId) {
        // 先取消当前默认视图
        CanvasView currentDefault = viewMapper.selectDefaultView(canvasId);
        if (currentDefault != null && !currentDefault.getId().equals(viewId)) {
            currentDefault.setIsDefault(false);
            viewMapper.updateById(currentDefault);
        }

        // 设置新的默认视图
        CanvasView newDefault = viewMapper.selectById(viewId);
        if (newDefault == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "视图不存在");
        }
        newDefault.setIsDefault(true);
        viewMapper.updateById(newDefault);

        log.info("设置默认视图: canvasId={}, viewId={}", canvasId, viewId);
    }

    /**
     * 预设视图配置内部类
     */
    private record PresetViewConfig(
            String viewKey,
            String name,
            String icon,
            String rootEntityType,
            Set<String> visibleEntityTypes,
            int sequence,
            boolean isDefault
    ) {}
}
