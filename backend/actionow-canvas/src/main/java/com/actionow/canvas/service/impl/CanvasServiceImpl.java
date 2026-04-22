package com.actionow.canvas.service.impl;

import com.actionow.canvas.constant.CanvasConstants;
import com.actionow.canvas.dto.EntityInfo;
import com.actionow.canvas.dto.canvas.*;
import com.actionow.canvas.dto.edge.CanvasEdgeResponse;
import com.actionow.canvas.dto.node.CanvasNodeResponse;
import com.actionow.canvas.dto.view.ViewDataRequest;
import com.actionow.canvas.dto.view.ViewDataResponse;
import com.actionow.canvas.entity.Canvas;
import com.actionow.canvas.entity.CanvasEdge;
import com.actionow.canvas.entity.CanvasNode;
import com.actionow.canvas.entity.CanvasView;
import com.actionow.canvas.event.CanvasEventPublisher;
import com.actionow.canvas.event.CanvasLayoutChangedEvent;
import com.actionow.canvas.event.CanvasUpdatedEvent;
import com.actionow.canvas.feign.ProjectFeignClient;
import com.actionow.canvas.layout.LayoutConfig;
import com.actionow.canvas.layout.LayoutEngine;
import com.actionow.canvas.layout.LayoutEngineFactory;
import com.actionow.canvas.mapper.CanvasEdgeMapper;
import com.actionow.canvas.mapper.CanvasMapper;
import com.actionow.canvas.mapper.CanvasNodeMapper;
import com.actionow.canvas.mapper.CanvasViewMapper;
import com.actionow.canvas.service.CanvasService;
import com.actionow.canvas.service.CanvasViewService;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.id.UuidGenerator;
import com.actionow.common.core.result.Result;
import com.actionow.common.core.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 画布服务实现
 * 统一主画布模型：1 Script = 1 Canvas
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CanvasServiceImpl implements CanvasService {

    private final CanvasMapper canvasMapper;
    private final CanvasNodeMapper nodeMapper;
    private final CanvasEdgeMapper edgeMapper;
    private final CanvasViewMapper viewMapper;
    private final ProjectFeignClient projectFeignClient;
    private final LayoutEngineFactory layoutEngineFactory;
    private final CanvasEventPublisher eventPublisher;
    private final CanvasViewService viewService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CanvasResponse createCanvas(CreateCanvasRequest request, String workspaceId, String userId) {
        // 检查剧本是否已有画布
        if (canvasMapper.existsByScriptId(request.getScriptId())) {
            throw new BusinessException(ResultCode.ALREADY_EXISTS, "该剧本已存在画布");
        }

        Canvas canvas = new Canvas();
        canvas.setId(UuidGenerator.generateUuidV7());
        canvas.setWorkspaceId(workspaceId);
        canvas.setScriptId(request.getScriptId());
        canvas.setName(StringUtils.hasText(request.getName()) ? request.getName() : "剧本画布");
        canvas.setDescription(request.getDescription());
        canvas.setLayoutStrategy(StringUtils.hasText(request.getLayoutStrategy()) ?
                request.getLayoutStrategy() : CanvasConstants.LayoutStrategy.GRID);
        canvas.setLocked(false);
        canvas.setViewport(Map.of("x", 0, "y", 0, "zoom", 1));
        canvas.setSettings(request.getSettings() != null ? request.getSettings() : new HashMap<>());

        canvasMapper.insert(canvas);

        // 初始化预设视图
        viewService.initPresetViews(canvas.getId(), workspaceId);

        log.info("创建画布: canvasId={}, scriptId={}, workspaceId={}",
                canvas.getId(), canvas.getScriptId(), workspaceId);

        return toResponse(canvas);
    }

    @Override
    public CanvasResponse getCanvas(String canvasId) {
        Canvas canvas = getCanvasEntity(canvasId);
        return toResponse(canvas);
    }

    @Override
    public CanvasResponse getCanvasByScriptId(String scriptId) {
        Canvas canvas = canvasMapper.selectByScriptId(scriptId);
        if (canvas == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "画布不存在");
        }
        return toResponse(canvas);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CanvasResponse getOrCreateByScriptId(String scriptId, String workspaceId, String userId) {
        Canvas canvas = canvasMapper.selectByScriptId(scriptId);
        if (canvas != null) {
            return toResponse(canvas);
        }

        CreateCanvasRequest request = new CreateCanvasRequest();
        request.setScriptId(scriptId);

        return createCanvas(request, workspaceId, userId);
    }

    @Override
    public CanvasFullResponse getCanvasFull(String canvasId) {
        Canvas canvas = getCanvasEntity(canvasId);

        List<CanvasNode> nodes = nodeMapper.selectByCanvasId(canvasId);
        List<CanvasEdge> edges = edgeMapper.selectByCanvasId(canvasId);

        // 转换节点响应
        List<CanvasNodeResponse> nodeResponses = nodes.stream()
                .map(CanvasNodeResponse::fromEntity)
                .collect(Collectors.toList());

        // 批量获取实体详情并填充
        enrichNodeResponses(nodeResponses);

        // 构建实体到节点ID的映射 (entityType:entityId -> nodeId)
        Map<String, String> entityToNodeIdMap = nodes.stream()
                .collect(Collectors.toMap(
                        n -> n.getEntityType() + ":" + n.getEntityId(),
                        CanvasNode::getId,
                        (existing, replacement) -> existing
                ));

        CanvasFullResponse response = new CanvasFullResponse();
        copyToFullResponse(canvas, response);
        response.setNodeCount(nodes.size());
        response.setEdgeCount(edges.size());
        response.setNodes(nodeResponses);
        response.setEdges(edges.stream()
                .map(edge -> toEdgeResponse(edge, entityToNodeIdMap))
                .collect(Collectors.toList()));

        return response;
    }

    @Override
    public ViewDataResponse getViewData(ViewDataRequest request) {
        Canvas canvas = getCanvasEntity(request.getCanvasId());
        String viewKey = request.getViewKey();
        String canvasId = request.getCanvasId();

        // 检查是否为聚焦模式
        boolean isFocusMode = StringUtils.hasText(request.getFocusEntityType())
                && StringUtils.hasText(request.getFocusEntityId());

        // 获取可见实体类型
        Set<String> visibleTypes;
        String viewName;
        if (StringUtils.hasText(viewKey)) {
            visibleTypes = CanvasConstants.VisibleEntityTypes.getByViewKey(viewKey);
            CanvasView view = viewMapper.selectByCanvasIdAndViewKey(canvasId, viewKey);
            viewName = view != null ? view.getName() : viewKey;
        } else {
            // 不指定视图则返回所有
            visibleTypes = CanvasConstants.VisibleEntityTypes.SCRIPT_VIEW;
            viewName = "全部";
        }

        List<CanvasNode> nodes;
        List<CanvasEdge> visibleEdges;
        String focusEntityName = null;

        if (isFocusMode) {
            // === 聚焦模式：只显示特定实体及其关联节点 ===
            String focusEntityType = request.getFocusEntityType();
            String focusEntityId = request.getFocusEntityId();
            int depth = request.getDepth() != null ? request.getDepth() : 1;

            // 收集需要显示的实体键集合
            Set<String> visibleEntityKeys = new HashSet<>();
            visibleEntityKeys.add(focusEntityType + ":" + focusEntityId);

            // 收集相关边
            Set<CanvasEdge> allRelatedEdges = new HashSet<>();

            // 逐层收集关联实体
            Set<String> currentLevelKeys = new HashSet<>(visibleEntityKeys);
            for (int d = 0; d < depth; d++) {
                List<CanvasEdge> levelEdges = edgeMapper.selectByCanvasAndEntityKeys(
                        canvasId, new ArrayList<>(currentLevelKeys));
                allRelatedEdges.addAll(levelEdges);

                // 收集下一层的实体键
                Set<String> nextLevelKeys = new HashSet<>();
                for (CanvasEdge edge : levelEdges) {
                    String sourceKey = edge.getSourceType() + ":" + edge.getSourceId();
                    String targetKey = edge.getTargetType() + ":" + edge.getTargetId();
                    if (!visibleEntityKeys.contains(sourceKey)) {
                        nextLevelKeys.add(sourceKey);
                    }
                    if (!visibleEntityKeys.contains(targetKey)) {
                        nextLevelKeys.add(targetKey);
                    }
                }
                visibleEntityKeys.addAll(nextLevelKeys);
                currentLevelKeys = nextLevelKeys;

                if (nextLevelKeys.isEmpty()) {
                    break; // 没有更多关联实体
                }
            }

            // 查询所有可见节点，然后过滤
            List<CanvasNode> allNodes = nodeMapper.selectByCanvasId(canvasId);
            nodes = allNodes.stream()
                    .filter(n -> visibleEntityKeys.contains(n.getEntityType() + ":" + n.getEntityId()))
                    .filter(n -> !Boolean.TRUE.equals(n.getHidden()))
                    .collect(Collectors.toList());

            // 获取聚焦实体的名称
            CanvasNode focusNode = nodes.stream()
                    .filter(n -> focusEntityType.equals(n.getEntityType())
                            && focusEntityId.equals(n.getEntityId()))
                    .findFirst()
                    .orElse(null);
            if (focusNode != null) {
                focusEntityName = focusNode.getCachedName();
            }

            // 过滤边：只保留两端都在可见节点中的边
            Set<String> visibleNodeEntityKeys = nodes.stream()
                    .map(n -> n.getEntityType() + ":" + n.getEntityId())
                    .collect(Collectors.toSet());
            visibleEdges = allRelatedEdges.stream()
                    .filter(edge -> {
                        String sourceKey = edge.getSourceType() + ":" + edge.getSourceId();
                        String targetKey = edge.getTargetType() + ":" + edge.getTargetId();
                        return visibleNodeEntityKeys.contains(sourceKey)
                                && visibleNodeEntityKeys.contains(targetKey);
                    })
                    .collect(Collectors.toList());

        } else {
            // === 普通模式：按视图类型过滤 ===
            List<CanvasEdge> allEdges = edgeMapper.selectByCanvasId(canvasId);

            // SCRIPT 视图显示所有节点（全量视图）
            if (CanvasConstants.ViewKey.SCRIPT.equals(viewKey)) {
                nodes = nodeMapper.selectVisibleByCanvasIdAndEntityTypes(
                        canvasId, new ArrayList<>(visibleTypes));
            } else {
                // 非 SCRIPT 视图：ASSET 节点只显示与其他节点有边连接的
                // 1. 获取非 ASSET 类型的可见节点
                List<String> nonAssetTypes = visibleTypes.stream()
                        .filter(t -> !CanvasConstants.EntityType.ASSET.equals(t))
                        .collect(Collectors.toList());

                List<CanvasNode> nonAssetNodes = nonAssetTypes.isEmpty()
                        ? List.of()
                        : nodeMapper.selectVisibleByCanvasIdAndEntityTypes(canvasId, nonAssetTypes);

                // 2. 收集非 ASSET 节点的实体键
                Set<String> nonAssetEntityKeys = nonAssetNodes.stream()
                        .map(n -> n.getEntityType() + ":" + n.getEntityId())
                        .collect(Collectors.toSet());

                // 3. 找出与非 ASSET 节点有边连接的 ASSET 实体键
                Set<String> connectedAssetEntityKeys = new HashSet<>();
                for (CanvasEdge edge : allEdges) {
                    String sourceType = CanvasConstants.EntityType.normalize(edge.getSourceType());
                    String targetType = CanvasConstants.EntityType.normalize(edge.getTargetType());
                    String sourceKey = edge.getSourceType() + ":" + edge.getSourceId();
                    String targetKey = edge.getTargetType() + ":" + edge.getTargetId();

                    // 源是非 ASSET，目标是 ASSET
                    if (nonAssetEntityKeys.contains(sourceKey)
                            && CanvasConstants.EntityType.isAssetType(targetType)) {
                        connectedAssetEntityKeys.add(targetKey);
                    }
                    // 源是 ASSET，目标是非 ASSET
                    if (CanvasConstants.EntityType.isAssetType(sourceType)
                            && nonAssetEntityKeys.contains(targetKey)) {
                        connectedAssetEntityKeys.add(sourceKey);
                    }
                    // ASSET 视图：ASSET 与 ASSET 之间的连接
                    if (CanvasConstants.ViewKey.ASSET.equals(viewKey)
                            && CanvasConstants.EntityType.isAssetType(sourceType)
                            && CanvasConstants.EntityType.isAssetType(targetType)) {
                        connectedAssetEntityKeys.add(sourceKey);
                        connectedAssetEntityKeys.add(targetKey);
                    }
                }

                // 4. 查询有边连接的 ASSET 节点
                List<CanvasNode> connectedAssetNodes = List.of();
                if (!connectedAssetEntityKeys.isEmpty()) {
                    List<CanvasNode> allAssetNodes = nodeMapper.selectVisibleByCanvasIdAndEntityTypes(
                            canvasId, List.of(CanvasConstants.EntityType.ASSET));
                    connectedAssetNodes = allAssetNodes.stream()
                            .filter(n -> connectedAssetEntityKeys.contains(n.getEntityType() + ":" + n.getEntityId()))
                            .collect(Collectors.toList());
                }

                // 5. 合并节点列表
                nodes = new ArrayList<>(nonAssetNodes);
                nodes.addAll(connectedAssetNodes);
            }

            // 查询相关的边（两端节点都在可见类型中）
            visibleEdges = allEdges.stream()
                    .filter(edge -> {
                        String normalizedSource = CanvasConstants.EntityType.normalize(edge.getSourceType());
                        String normalizedTarget = CanvasConstants.EntityType.normalize(edge.getTargetType());
                        return visibleTypes.contains(normalizedSource)
                                && visibleTypes.contains(normalizedTarget);
                    })
                    .collect(Collectors.toList());
        }

        // 转换节点响应
        List<CanvasNodeResponse> nodeResponses = nodes.stream()
                .map(CanvasNodeResponse::fromEntity)
                .collect(Collectors.toList());

        // 填充实体详情（如果需要）
        if (Boolean.TRUE.equals(request.getIncludeEntityDetail())) {
            enrichNodeResponses(nodeResponses);
            // 如果聚焦模式下还没有获取到名称，尝试从详情中获取
            if (isFocusMode && focusEntityName == null) {
                String focusEntityType = request.getFocusEntityType();
                String focusEntityId = request.getFocusEntityId();
                for (CanvasNodeResponse nodeResp : nodeResponses) {
                    if (focusEntityType.equals(nodeResp.getEntityType())
                            && focusEntityId.equals(nodeResp.getEntityId())) {
                        if (nodeResp.getEntityDetail() != null) {
                            Object name = nodeResp.getEntityDetail().get("name");
                            if (name != null) {
                                focusEntityName = name.toString();
                            }
                        }
                        break;
                    }
                }
            }
        }

        // 构建响应
        Map<String, String> entityToNodeIdMap = nodes.stream()
                .collect(Collectors.toMap(
                        n -> n.getEntityType() + ":" + n.getEntityId(),
                        CanvasNode::getId,
                        (existing, replacement) -> existing
                ));

        ViewDataResponse response = new ViewDataResponse();
        response.setCanvasId(canvasId);
        response.setViewKey(viewKey);
        response.setViewName(viewName);
        response.setFocusMode(isFocusMode);
        if (isFocusMode) {
            response.setFocusEntityType(request.getFocusEntityType());
            response.setFocusEntityId(request.getFocusEntityId());
            response.setFocusEntityName(focusEntityName);
        }
        response.setNodes(nodeResponses);
        response.setEdges(visibleEdges.stream()
                .map(edge -> toEdgeResponse(edge, entityToNodeIdMap))
                .collect(Collectors.toList()));
        response.setTotalNodes(nodeResponses.size());
        response.setTotalEdges(visibleEdges.size());

        // 统计各类型节点数量
        Map<String, Integer> nodeCountByType = nodeResponses.stream()
                .collect(Collectors.groupingBy(
                        CanvasNodeResponse::getEntityType,
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
                ));
        response.setNodeCountByType(nodeCountByType);

        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CanvasResponse updateCanvas(String canvasId, UpdateCanvasRequest request, String userId) {
        Canvas canvas = getCanvasEntity(canvasId);

        Canvas previousState = copyCanvasState(canvas);

        if (StringUtils.hasText(request.getName())) {
            canvas.setName(request.getName());
        }
        if (request.getDescription() != null) {
            canvas.setDescription(request.getDescription());
        }
        if (StringUtils.hasText(request.getLayoutStrategy())) {
            canvas.setLayoutStrategy(request.getLayoutStrategy());
        }
        if (request.getLocked() != null) {
            canvas.setLocked(request.getLocked());
        }
        if (request.getViewport() != null) {
            canvas.setViewport(request.getViewport());
        }
        if (request.getSettings() != null) {
            canvas.setSettings(request.getSettings());
        }

        canvasMapper.updateById(canvas);

        log.info("更新画布: canvasId={}", canvasId);

        eventPublisher.publishAsync(new CanvasUpdatedEvent(canvas, previousState, userId, canvas.getWorkspaceId()));

        return toResponse(canvas);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateViewport(String canvasId, Map<String, Object> viewport, String userId) {
        Canvas canvas = getCanvasEntity(canvasId);

        // 保存之前的状态用于事件
        Canvas previousState = copyCanvasState(canvas);

        canvas.setViewport(viewport);
        canvasMapper.updateById(canvas);

        log.debug("更新画布视口: canvasId={}, viewport={}", canvasId, viewport);

        // 发布画布更新事件，广播给其他用户（不会发给操作者自己）
        // 这是解决 Viewport 跳回问题的关键
        eventPublisher.publishAsync(new CanvasUpdatedEvent(canvas, previousState, userId, canvas.getWorkspaceId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCanvas(String canvasId, String userId) {
        Canvas canvas = getCanvasEntity(canvasId);

        // 删除所有视图
        viewMapper.deleteByCanvasId(canvasId);
        // 删除所有节点和边
        nodeMapper.deleteByCanvasId(canvasId);
        edgeMapper.deleteByCanvasId(canvasId);
        // 删除画布
        canvasMapper.deleteById(canvasId);

        log.info("删除画布: canvasId={}", canvasId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByScriptId(String scriptId, String userId) {
        Canvas canvas = canvasMapper.selectByScriptId(scriptId);
        if (canvas != null) {
            deleteCanvas(canvas.getId(), userId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CanvasFullResponse autoLayout(String canvasId, String strategy, String viewKey, String userId) {
        Canvas canvas = getCanvasEntity(canvasId);

        // 根据视图筛选节点
        List<CanvasNode> nodes;
        if (StringUtils.hasText(viewKey)) {
            Set<String> visibleTypes = CanvasConstants.VisibleEntityTypes.getByViewKey(viewKey);
            nodes = nodeMapper.selectVisibleByCanvasIdAndEntityTypes(canvasId, new ArrayList<>(visibleTypes));
        } else {
            nodes = nodeMapper.selectByCanvasId(canvasId);
        }

        if (nodes.isEmpty()) {
            return getCanvasFull(canvasId);
        }

        List<CanvasEdge> edges = edgeMapper.selectByCanvasId(canvasId);
        String layoutStrategy = StringUtils.hasText(strategy) ? strategy : canvas.getLayoutStrategy();

        LayoutEngine engine = layoutEngineFactory.getEngine(layoutStrategy);
        LayoutConfig config = LayoutConfig.builder()
                .centerX(500.0)
                .centerY(400.0)
                .build();

        engine.applyLayout(nodes, edges, config);

        for (CanvasNode node : nodes) {
            nodeMapper.updateById(node);
        }

        log.info("自动布局画布: canvasId={}, strategy={}, viewKey={}, nodeCount={}",
                canvasId, layoutStrategy, viewKey, nodes.size());

        eventPublisher.publishAsync(new CanvasLayoutChangedEvent(canvasId, layoutStrategy, nodes.size(), userId, canvas.getWorkspaceId()));

        return getCanvasFull(canvasId);
    }

    @Override
    public Canvas getCanvasEntity(String canvasId) {
        Canvas canvas = canvasMapper.selectById(canvasId);
        if (canvas == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "画布不存在");
        }
        return canvas;
    }

    @Override
    public boolean existsByScriptId(String scriptId) {
        return canvasMapper.existsByScriptId(scriptId);
    }

    /**
     * 批量获取实体详情并填充到节点响应
     */
    private void enrichNodeResponses(List<CanvasNodeResponse> nodeResponses) {
        if (nodeResponses == null || nodeResponses.isEmpty()) {
            return;
        }

        Map<String, List<String>> idsByType = new HashMap<>();
        for (CanvasNodeResponse node : nodeResponses) {
            idsByType.computeIfAbsent(node.getEntityType(), k -> new ArrayList<>())
                    .add(node.getEntityId());
        }

        Map<String, EntityInfo> entityInfoMap = new java.util.concurrent.ConcurrentHashMap<>();
        idsByType.entrySet().parallelStream().forEach(entry -> {
            String entityType = entry.getKey();
            List<String> ids = entry.getValue();

            try {
                List<EntityInfo> infos = fetchEntitiesByType(entityType, ids);
                if (infos != null) {
                    for (EntityInfo info : infos) {
                        entityInfoMap.put(entityType + ":" + info.getId(), info);
                    }
                }
            } catch (Exception e) {
                log.warn("获取实体信息失败: entityType={}, error={}", entityType, e.getMessage());
            }
        });

        for (CanvasNodeResponse node : nodeResponses) {
            String key = node.getEntityType() + ":" + node.getEntityId();
            EntityInfo info = entityInfoMap.get(key);
            if (info != null) {
                node.setEntityDetail(info.toEntityDetailMap());
            }
        }
    }

    private List<EntityInfo> fetchEntitiesByType(String entityType, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }

        String normalizedType = CanvasConstants.EntityType.normalize(entityType);

        Result<List<EntityInfo>> result = switch (normalizedType) {
            case CanvasConstants.EntityType.SCRIPT -> projectFeignClient.batchGetScripts(ids);
            case CanvasConstants.EntityType.EPISODE -> projectFeignClient.batchGetEpisodes(ids);
            case CanvasConstants.EntityType.STORYBOARD -> projectFeignClient.batchGetStoryboards(ids);
            case CanvasConstants.EntityType.CHARACTER -> projectFeignClient.batchGetCharacters(ids);
            case CanvasConstants.EntityType.SCENE -> projectFeignClient.batchGetScenes(ids);
            case CanvasConstants.EntityType.PROP -> projectFeignClient.batchGetProps(ids);
            case CanvasConstants.EntityType.ASSET -> projectFeignClient.batchGetAssets(ids);
            default -> null;
        };

        if (result != null && result.isSuccess() && result.getData() != null) {
            return result.getData();
        }
        return Collections.emptyList();
    }

    private CanvasResponse toResponse(Canvas canvas) {
        CanvasResponse response = new CanvasResponse();
        response.setId(canvas.getId());
        response.setScriptId(canvas.getScriptId());
        response.setName(canvas.getName());
        response.setDescription(canvas.getDescription());
        response.setViewport(canvas.getViewport());
        response.setLayoutStrategy(canvas.getLayoutStrategy());
        response.setLocked(canvas.getLocked());
        response.setSettings(canvas.getSettings());
        response.setCreatedAt(canvas.getCreatedAt());
        response.setUpdatedAt(canvas.getUpdatedAt());

        // 获取节点和边数量
        response.setNodeCount((int) nodeMapper.countByCanvasId(canvas.getId()));
        response.setEdgeCount((int) edgeMapper.countByCanvasId(canvas.getId()));

        // 获取视图列表
        List<CanvasView> views = viewMapper.selectByCanvasId(canvas.getId());
        response.setViews(views.stream()
                .map(this::toViewResponse)
                .collect(Collectors.toList()));

        return response;
    }

    private void copyToFullResponse(Canvas canvas, CanvasFullResponse response) {
        response.setId(canvas.getId());
        response.setScriptId(canvas.getScriptId());
        response.setName(canvas.getName());
        response.setDescription(canvas.getDescription());
        response.setViewport(canvas.getViewport());
        response.setLayoutStrategy(canvas.getLayoutStrategy());
        response.setLocked(canvas.getLocked());
        response.setSettings(canvas.getSettings());
        response.setCreatedAt(canvas.getCreatedAt());
        response.setUpdatedAt(canvas.getUpdatedAt());

        List<CanvasView> views = viewMapper.selectByCanvasId(canvas.getId());
        response.setViews(views.stream()
                .map(this::toViewResponse)
                .collect(Collectors.toList()));
    }

    private CanvasResponse.CanvasViewResponse toViewResponse(CanvasView view) {
        CanvasResponse.CanvasViewResponse response = new CanvasResponse.CanvasViewResponse();
        response.setId(view.getId());
        response.setViewKey(view.getViewKey());
        response.setName(view.getName());
        response.setIcon(view.getIcon());
        response.setViewType(view.getViewType());
        response.setRootEntityType(view.getRootEntityType());
        response.setVisibleEntityTypes(view.getVisibleEntityTypes());
        response.setSequence(view.getSequence());
        response.setIsDefault(view.getIsDefault());
        return response;
    }

    private CanvasEdgeResponse toEdgeResponse(CanvasEdge edge, Map<String, String> entityToNodeIdMap) {
        CanvasEdgeResponse response = new CanvasEdgeResponse();
        response.setId(edge.getId());
        response.setCanvasId(edge.getCanvasId());
        response.setSourceType(edge.getSourceType());
        response.setSourceId(edge.getSourceId());
        response.setSourceVersionId(edge.getSourceVersionId());
        response.setSourceHandle(edge.getSourceHandle());
        response.setTargetType(edge.getTargetType());
        response.setTargetId(edge.getTargetId());
        response.setTargetVersionId(edge.getTargetVersionId());
        response.setTargetHandle(edge.getTargetHandle());
        response.setRelationType(edge.getRelationType());
        response.setRelationLabel(edge.getRelationLabel());
        response.setDescription(edge.getDescription());
        response.setLineStyle(edge.getLineStyle());
        response.setPathType(edge.getPathType());
        response.setSequence(edge.getSequence());
        response.setExtraInfo(edge.getExtraInfo());
        response.setCreatedAt(edge.getCreatedAt());
        response.setUpdatedAt(edge.getUpdatedAt());

        if (entityToNodeIdMap != null) {
            String sourceKey = edge.getSourceType() + ":" + edge.getSourceId();
            String targetKey = edge.getTargetType() + ":" + edge.getTargetId();
            response.setSourceNodeId(entityToNodeIdMap.get(sourceKey));
            response.setTargetNodeId(entityToNodeIdMap.get(targetKey));
        }

        return response;
    }

    private Canvas copyCanvasState(Canvas source) {
        Canvas copy = new Canvas();
        copy.setId(source.getId());
        copy.setWorkspaceId(source.getWorkspaceId());
        copy.setScriptId(source.getScriptId());
        copy.setName(source.getName());
        copy.setDescription(source.getDescription());
        copy.setLayoutStrategy(source.getLayoutStrategy());
        copy.setLocked(source.getLocked());
        copy.setViewport(source.getViewport() != null ? new HashMap<>(source.getViewport()) : null);
        copy.setSettings(source.getSettings() != null ? new HashMap<>(source.getSettings()) : null);
        return copy;
    }
}
