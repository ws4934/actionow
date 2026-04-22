package com.actionow.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.common.core.constant.CommonConstants;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.id.UuidGenerator;
import com.actionow.common.core.result.ResultCode;
import com.actionow.project.constant.ProjectConstants;
import com.actionow.project.dto.CreateScriptRequest;
import com.actionow.project.dto.UpdateScriptRequest;
import com.actionow.project.dto.ScriptQueryRequest;
import com.actionow.project.dto.ScriptDetailResponse;
import com.actionow.project.dto.ScriptListResponse;
import com.actionow.project.entity.Script;
import com.actionow.project.mapper.EpisodeMapper;
import com.actionow.project.mapper.ScriptMapper;
import com.actionow.project.mapper.WorkspaceSchemaMapper;
import com.actionow.project.service.AssetService;
import com.actionow.project.service.ScriptPermissionService;
import com.actionow.project.service.ScriptService;
import com.actionow.project.service.UserInfoHelper;
import com.actionow.project.service.version.VersionService;
import com.actionow.project.dto.version.ScriptVersionDetailResponse;
import com.actionow.project.feign.UserBasicInfo;
import com.actionow.project.feign.CanvasFeignClient;
import com.actionow.project.publisher.EntityChangeEventPublisher;
import com.actionow.common.mq.message.CollabEntityChangeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 剧本服务实现
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptServiceImpl implements ScriptService {

    private final ScriptMapper scriptMapper;
    private final EpisodeMapper episodeMapper;
    private final UserInfoHelper userInfoHelper;
    private final CanvasFeignClient canvasFeignClient;
    private final VersionService<Script, ScriptVersionDetailResponse> scriptVersionService;
    private final EntityChangeEventPublisher entityChangeEventPublisher;
    private final AssetService assetService;
    private final ScriptPermissionService scriptPermissionService;
    private final WorkspaceSchemaMapper workspaceSchemaMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScriptDetailResponse create(CreateScriptRequest request, String workspaceId, String userId) {
        return create(request, workspaceId, userId, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScriptDetailResponse create(CreateScriptRequest request, String workspaceId, String userId, boolean skipCanvasSync) {
        // 权限检查：GUEST 始终拒绝；MEMBER 检查租户开关；CREATOR/ADMIN 直接允许
        checkScriptCreationPermission(workspaceId);

        Script script = new Script();
        script.setId(UuidGenerator.generateUuidV7());
        script.setWorkspaceId(workspaceId);
        script.setTitle(request.getTitle());
        script.setSynopsis(request.getSynopsis());
        script.setCoverAssetId(request.getCoverAssetId());
        script.setStatus(ProjectConstants.ScriptStatus.DRAFT);
        script.setVersion(1);
        script.setCreatedBy(userId);

        scriptMapper.insert(script);

        // 为创建者自动授予剧本 ADMIN 权限
        scriptPermissionService.createOwnerPermission(script.getId(), workspaceId, userId);

        // 创建初始版本快照 (V1)
        scriptVersionService.createVersionSnapshot(script, "创建剧本", userId);

        log.info("剧本创建成功: scriptId={}, title={}, workspaceId={}, skipCanvasSync={}",
                script.getId(), script.getTitle(), workspaceId, skipCanvasSync);

        // 画布初始化必须在事务提交后执行，否则 Canvas 服务的 FK 检查看不到未提交的 script 行
        if (!skipCanvasSync) {
            final String sid = script.getId();
            final String wid = workspaceId;
            final String sname = script.getTitle();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    initCanvasAsync(sid, wid, sname);
                }
            });
        }

        // 发布实体创建事件到协作服务
        entityChangeEventPublisher.publishEntityCreated(
                CollabEntityChangeEvent.EntityType.SCRIPT,
                script.getId(),
                script.getId(),  // 剧本本身就是 scriptId
                ScriptDetailResponse.fromEntity(script)
        );

        ScriptDetailResponse response = ScriptDetailResponse.fromEntity(script);
        response.setEpisodeCount(0);
        return response;
    }

    /**
     * 异步初始化画布
     * Canvas 初始化失败不影响剧本创建
     */
    private void initCanvasAsync(String scriptId, String workspaceId, String scriptName) {
        try {
            var result = canvasFeignClient.initScriptCanvas(scriptId, workspaceId, scriptName);
            if (result != null && result.isSuccess()) {
                log.info("剧本画布初始化成功: scriptId={}", scriptId);
            } else {
                log.warn("剧本画布初始化返回失败: scriptId={}, result={}", scriptId, result);
            }
        } catch (Exception e) {
            // Canvas 初始化失败不影响剧本创建
            log.warn("剧本画布初始化异常（不影响剧本创建）: scriptId={}, error={}", scriptId, e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScriptDetailResponse update(String scriptId, UpdateScriptRequest request, String userId) {
        Script script = getScriptOrThrow(scriptId);

        // 权限检查：workspace ADMIN+ 直接允许；其他需有 script EDIT 或 ADMIN 权限
        checkScriptWritePermission(scriptId, userId);

        // 获取保存模式，默认为 NEW_VERSION
        String saveMode = request.getSaveMode();
        if (saveMode == null) {
            saveMode = ProjectConstants.SaveMode.NEW_VERSION;
        }

        // NEW_ENTITY 模式：另存为新实体
        if (ProjectConstants.SaveMode.NEW_ENTITY.equals(saveMode)) {
            CreateScriptRequest newRequest = new CreateScriptRequest();
            newRequest.setTitle(request.getTitle() != null ? request.getTitle() : script.getTitle());
            newRequest.setSynopsis(request.getSynopsis() != null ? request.getSynopsis() : script.getSynopsis());
            newRequest.setContent(request.getContent() != null ? request.getContent() : script.getContent());
            newRequest.setCoverAssetId(request.getCoverAssetId() != null ? request.getCoverAssetId() : script.getCoverAssetId());
            newRequest.setDocAssetId(request.getDocAssetId() != null ? request.getDocAssetId() : script.getDocAssetId());
            newRequest.setExtraInfo(request.getExtraInfo() != null ? request.getExtraInfo() : script.getExtraInfo());
            return create(newRequest, script.getWorkspaceId(), userId);
        }

        // 检测实际变更，生成变更摘要
        StringBuilder changes = new StringBuilder();
        if (request.getTitle() != null && !request.getTitle().equals(script.getTitle())) {
            changes.append("标题");
        }
        if (request.getSynopsis() != null && !request.getSynopsis().equals(script.getSynopsis())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("简介");
        }
        if (request.getContent() != null && !request.getContent().equals(script.getContent())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("正文");
        }
        if (request.getCoverAssetId() != null && !request.getCoverAssetId().equals(script.getCoverAssetId())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("封面");
        }
        if (request.getDocAssetId() != null && !request.getDocAssetId().equals(script.getDocAssetId())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("文档");
        }
        if (request.getExtraInfo() != null && !request.getExtraInfo().equals(script.getExtraInfo())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("扩展信息");
        }
        if (request.getStatus() != null && !request.getStatus().equals(script.getStatus())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("状态");
        }

        // 1. 先更新数据
        if (request.getTitle() != null) {
            script.setTitle(request.getTitle());
        }
        if (request.getSynopsis() != null) {
            script.setSynopsis(request.getSynopsis());
        }
        if (request.getContent() != null) {
            script.setContent(request.getContent());
        }
        if (request.getCoverAssetId() != null) {
            script.setCoverAssetId(request.getCoverAssetId());
        }
        if (request.getDocAssetId() != null) {
            script.setDocAssetId(request.getDocAssetId());
        }
        if (request.getExtraInfo() != null) {
            script.setExtraInfo(request.getExtraInfo());
        }
        if (request.getStatus() != null) {
            script.setStatus(request.getStatus());
        }
        script.setUpdatedBy(userId);

        // 检查乐观锁更新结果
        int rows = scriptMapper.updateById(script);
        if (rows == 0) {
            log.warn("剧本更新失败（并发冲突）: scriptId={}, version={}", scriptId, script.getVersion());
            throw new BusinessException(ResultCode.CONCURRENT_OPERATION);
        }

        // 2. NEW_VERSION 模式：仅在有实际变更时创建版本快照（保存更新后的数据）
        // OVERWRITE 模式：跳过版本快照创建
        if (ProjectConstants.SaveMode.NEW_VERSION.equals(saveMode) && !changes.isEmpty()) {
            String changeSummary = "更新" + changes;
            script = getScriptOrThrow(scriptId);
            scriptVersionService.createVersionSnapshot(script, changeSummary, userId);
        }

        log.info("剧本更新成功: scriptId={}, versionNumber={}, saveMode={}", scriptId, script.getVersionNumber(), saveMode);

        // 发布实体更新事件到协作服务
        entityChangeEventPublisher.publishEntityUpdated(
                CollabEntityChangeEvent.EntityType.SCRIPT,
                script.getId(),
                script.getId(),
                changes.isEmpty() ? null : List.of(changes.toString().split("、")),
                ScriptDetailResponse.fromEntity(script)
        );

        ScriptDetailResponse response = ScriptDetailResponse.fromEntity(script);
        response.setEpisodeCount(episodeMapper.countByScriptId(scriptId));
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String scriptId, String userId) {
        // 先验证剧本存在
        getScriptOrThrow(scriptId);

        // 权限检查：workspace ADMIN+ 直接允许；其他需有 script ADMIN 权限
        checkScriptAdminPermission(scriptId, userId);
        // 使用 MyBatis-Plus 的 deleteById，会自动转换为逻辑删除（UPDATE SET deleted=1）
        scriptMapper.deleteById(scriptId);

        // 发布实体删除事件到协作服务
        entityChangeEventPublisher.publishEntityDeleted(
                CollabEntityChangeEvent.EntityType.SCRIPT,
                scriptId,
                scriptId
        );

        // Canvas 删除也需在事务提交后执行，保证数据一致性
        final String sid = scriptId;
        final String uid = userId;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteCanvasAsync(sid, uid);
            }
        });

        log.info("剧本删除成功: scriptId={}", scriptId);
    }

    /**
     * 异步删除画布
     * Canvas 删除失败不影响剧本删除
     */
    private void deleteCanvasAsync(String scriptId, String userId) {
        try {
            var result = canvasFeignClient.deleteByScriptId(scriptId, userId);
            if (result != null && result.isSuccess()) {
                log.info("剧本画布删除成功: scriptId={}", scriptId);
            } else {
                log.warn("剧本画布删除返回失败: scriptId={}, result={}", scriptId, result);
            }
        } catch (Exception e) {
            // Canvas 删除失败不影响剧本删除
            log.warn("剧本画布删除异常（不影响剧本删除）: scriptId={}, error={}", scriptId, e.getMessage());
        }
    }

    @Override
    public ScriptDetailResponse getById(String scriptId) {
        Script script = getScriptOrThrow(scriptId);

        // 权限检查：workspace ADMIN+ 直接允许；其他需有任意 script 权限
        checkScriptViewPermission(scriptId, UserContextHolder.getUserId());

        ScriptDetailResponse response = ScriptDetailResponse.fromEntity(script);
        response.setEpisodeCount(episodeMapper.countByScriptId(scriptId));
        // 填充创建者信息
        if (script.getCreatedBy() != null) {
            UserBasicInfo userInfo = userInfoHelper.getUserInfo(script.getCreatedBy());
            if (userInfo != null) {
                response.setCreatedByUsername(userInfo.getUsername());
                response.setCreatedByNickname(userInfo.getNickname());
            }
        }
        // 填充封面URL
        if (script.getCoverAssetId() != null) {
            try {
                var asset = assetService.getById(script.getCoverAssetId());
                response.setCoverUrl(asset.getThumbnailUrl() != null ? asset.getThumbnailUrl() : asset.getFileUrl());
            } catch (Exception e) {
                log.warn("获取剧本封面素材失败: scriptId={}, coverAssetId={}", scriptId, script.getCoverAssetId());
            }
        }
        return response;
    }

    @Override
    public Optional<Script> findById(String scriptId) {
        Script script = scriptMapper.selectById(scriptId);
        if (script == null || script.getDeleted() == CommonConstants.DELETED) {
            return Optional.empty();
        }
        return Optional.of(script);
    }

    @Override
    public List<ScriptListResponse> listByWorkspace(String workspaceId) {
        List<Script> scripts = getAccessibleScripts(workspaceId);
        return convertToListResponses(scripts);
    }

    @Override
    public List<ScriptListResponse> listByStatus(String workspaceId, String status) {
        List<Script> scripts = getAccessibleScripts(workspaceId);
        // 在内存中按状态过滤（已经是权限过滤后的结果）
        if (status != null && !status.isEmpty()) {
            scripts = scripts.stream()
                    .filter(s -> status.equals(s.getStatus()))
                    .collect(java.util.stream.Collectors.toList());
        }
        return convertToListResponses(scripts);
    }

    /**
     * 获取当前用户可访问的剧本列表（含权限过滤）
     */
    private List<Script> getAccessibleScripts(String workspaceId) {
        String userId = UserContextHolder.getUserId();
        String role = UserContextHolder.getWorkspaceRole();
        if (isWorkspaceAdmin(role)) {
            return scriptMapper.selectByWorkspaceId(workspaceId);
        }
        return scriptMapper.selectAccessibleByUser(workspaceId, userId, role != null ? role : "");
    }

    @Override
    public Page<ScriptListResponse> queryScripts(ScriptQueryRequest request, String workspaceId) {
        Page<Script> page = new Page<>(request.getPageNum(), request.getPageSize());

        String currentUserId = UserContextHolder.getUserId();
        String currentRole = UserContextHolder.getWorkspaceRole();

        LambdaQueryWrapper<Script> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Script::getWorkspaceId, workspaceId)
                .eq(Script::getDeleted, CommonConstants.NOT_DELETED);

        // 非 ADMIN+ 用户只能看到有权限的剧本
        if (!isWorkspaceAdmin(currentRole)) {
            final String uid = currentUserId != null ? currentUserId : "";
            wrapper.and(w -> w
                    .eq(Script::getCreatedBy, uid)
                    .or()
                    .apply("EXISTS (SELECT 1 FROM t_script_permission sp " +
                            "WHERE sp.script_id = t_script.id AND sp.user_id = {0} AND sp.deleted = 0 " +
                            "AND (sp.expires_at IS NULL OR sp.expires_at > NOW()))", uid)
            );
        }

        // 状态过滤
        if (StringUtils.hasText(request.getStatus())) {
            wrapper.eq(Script::getStatus, request.getStatus());
        }
        // 关键词搜索（标题、简介、正文、附加信息）
        if (StringUtils.hasText(request.getKeyword())) {
            String kw = "%" + request.getKeyword() + "%";
            wrapper.and(w -> w.like(Script::getTitle, request.getKeyword())
                    .or()
                    .like(Script::getSynopsis, request.getKeyword())
                    .or()
                    .like(Script::getContent, request.getKeyword())
                    .or()
                    .apply("extra_info::text LIKE {0}", kw));
        }
        // 创建者过滤
        if (StringUtils.hasText(request.getCreatedBy())) {
            wrapper.eq(Script::getCreatedBy, request.getCreatedBy());
        }

        // 排序
        String orderBy = request.getOrderBy();
        boolean isAsc = "asc".equalsIgnoreCase(request.getOrderDir());
        switch (orderBy) {
            case "title" -> wrapper.orderBy(true, isAsc, Script::getTitle);
            case "updated_at" -> wrapper.orderBy(true, isAsc, Script::getUpdatedAt);
            default -> wrapper.orderBy(true, isAsc, Script::getCreatedAt);
        }

        Page<Script> resultPage = scriptMapper.selectPage(page, wrapper);

        // 转换为响应对象
        Page<ScriptListResponse> responsePage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        responsePage.setRecords(convertToListResponses(resultPage.getRecords()));

        return responsePage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(String scriptId, String status, String userId) {
        Script script = getScriptOrThrow(scriptId);

        // 仅在状态实际变更时处理
        if (!status.equals(script.getStatus())) {
            String changeSummary = String.format("状态变更: %s → %s", script.getStatus(), status);

            // 1. 先更新数据
            script.setStatus(status);
            script.setUpdatedBy(userId);

            int rows = scriptMapper.updateById(script);
            if (rows == 0) {
                throw new BusinessException(ResultCode.CONCURRENT_OPERATION);
            }

            // 2. 再创建版本快照（保存更新后的数据）
            script = getScriptOrThrow(scriptId);
            scriptVersionService.createVersionSnapshot(script, changeSummary, userId);

            log.info("剧本状态更新: scriptId={}, status={}, versionNumber={}", scriptId, status, script.getVersionNumber());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateContent(String scriptId, String content, String userId) {
        Script script = getScriptOrThrow(scriptId);

        // 1. 先更新数据
        script.setContent(content);
        script.setUpdatedBy(userId);

        int rows = scriptMapper.updateById(script);
        if (rows == 0) {
            throw new BusinessException(ResultCode.CONCURRENT_OPERATION);
        }

        // 2. 再创建版本快照（保存更新后的数据）
        script = getScriptOrThrow(scriptId);
        scriptVersionService.createVersionSnapshot(script, "更新正文", userId);

        log.info("剧本内容更新: scriptId={}, versionNumber={}", scriptId, script.getVersionNumber());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archive(String scriptId, String userId) {
        updateStatus(scriptId, ProjectConstants.ScriptStatus.ARCHIVED, userId);
    }

    @Override
    public void setCover(String scriptId, String assetId, String userId) {
        Script script = getScriptOrThrow(scriptId);
        script.setCoverAssetId(assetId);
        scriptMapper.updateById(script);

        log.info("剧本封面设置成功: scriptId={}, assetId={}", scriptId, assetId);
    }

    private Script getScriptOrThrow(String scriptId) {
        return findById(scriptId)
                .orElseThrow(() -> new BusinessException(ResultCode.SCRIPT_NOT_FOUND));
    }

    // ==================== 权限辅助方法 ====================

    /**
     * 检查剧本创建权限
     * Creator/Admin: 直接允许
     * Member: 检查租户 memberCanCreateScript 开关（默认 true）
     * Guest: 始终拒绝
     */
    private void checkScriptCreationPermission(String workspaceId) {
        String role = UserContextHolder.getWorkspaceRole();
        if ("GUEST".equals(role)) {
            throw new BusinessException(ResultCode.SCRIPT_CREATE_NOT_ALLOWED);
        }
        if ("MEMBER".equals(role)) {
            Boolean allowed = workspaceSchemaMapper.selectMemberCanCreateScript(workspaceId);
            // null 表示配置不存在，默认允许
            if (Boolean.FALSE.equals(allowed)) {
                throw new BusinessException(ResultCode.SCRIPT_CREATE_NOT_ALLOWED);
            }
        }
        // CREATOR / ADMIN: 直接允许
    }

    /**
     * 检查剧本查看权限（VIEW ∨ EDIT ∨ ADMIN）
     * workspace ADMIN+ 直接通过
     */
    private void checkScriptViewPermission(String scriptId, String userId) {
        if (userId == null) return;
        String role = UserContextHolder.getWorkspaceRole();
        if (isWorkspaceAdmin(role)) return;
        if (!scriptPermissionService.hasViewPermission(scriptId, userId)) {
            throw new BusinessException(ResultCode.SCRIPT_NO_PERMISSION);
        }
    }

    /**
     * 检查剧本写入权限（EDIT ∨ ADMIN）
     * workspace ADMIN+ 直接通过
     */
    private void checkScriptWritePermission(String scriptId, String userId) {
        String role = UserContextHolder.getWorkspaceRole();
        if (isWorkspaceAdmin(role)) return;
        if (!scriptPermissionService.hasEditPermission(scriptId, userId)) {
            throw new BusinessException(ResultCode.SCRIPT_NO_PERMISSION);
        }
    }

    /**
     * 检查剧本 ADMIN 权限（用于删除操作）
     * workspace ADMIN+ 直接通过
     */
    private void checkScriptAdminPermission(String scriptId, String userId) {
        String role = UserContextHolder.getWorkspaceRole();
        if (isWorkspaceAdmin(role)) return;
        if (!scriptPermissionService.hasAdminPermission(scriptId, userId)) {
            throw new BusinessException(ResultCode.SCRIPT_NO_PERMISSION);
        }
    }

    private boolean isWorkspaceAdmin(String role) {
        return "ADMIN".equals(role) || "CREATOR".equals(role);
    }

    /**
     * 转换为列表响应并批量填充关联数据
     */
    private List<ScriptListResponse> convertToListResponses(List<Script> scripts) {
        if (scripts == null || scripts.isEmpty()) {
            return List.of();
        }

        // 批量获取所有 scriptId 的 episodeCount，避免 N+1 问题
        List<String> scriptIds = scripts.stream().map(Script::getId).toList();
        Map<String, Integer> episodeCountMap = episodeMapper.batchCountByScriptIds(scriptIds);

        List<ScriptListResponse> responses = scripts.stream()
                .map(script -> {
                    ScriptListResponse response = ScriptListResponse.fromEntity(script);
                    response.setEpisodeCount(episodeCountMap.getOrDefault(script.getId(), 0));
                    return response;
                })
                .collect(Collectors.toList());

        // 批量填充用户信息
        populateUserInfo(responses);
        // 批量填充封面URL
        populateCoverUrl(scripts, responses);
        return responses;
    }

    private void populateUserInfo(List<ScriptListResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return;
        }
        Set<String> userIds = responses.stream()
                .map(ScriptListResponse::getCreatedBy)
                .filter(id -> id != null && !id.isEmpty())
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return;
        }
        Map<String, UserBasicInfo> userInfoMap = userInfoHelper.batchGetUserInfo(userIds);
        responses.forEach(response -> {
            UserBasicInfo userInfo = userInfoMap.get(response.getCreatedBy());
            if (userInfo != null) {
                response.setCreatedByUsername(userInfo.getUsername());
                response.setCreatedByNickname(userInfo.getNickname());
            }
        });
    }

    private void populateCoverUrl(List<Script> scripts, List<ScriptListResponse> responses) {
        Set<String> coverAssetIds = scripts.stream()
                .map(Script::getCoverAssetId)
                .filter(id -> id != null && !id.isEmpty())
                .collect(Collectors.toSet());
        if (coverAssetIds.isEmpty()) {
            return;
        }
        try {
            var assets = assetService.batchGet(new java.util.ArrayList<>(coverAssetIds));
            Map<String, String> assetUrlMap = assets.stream()
                    .collect(Collectors.toMap(
                            a -> a.getId(),
                            a -> a.getThumbnailUrl() != null ? a.getThumbnailUrl() : a.getFileUrl(),
                            (a, b) -> a
                    ));
            for (int i = 0; i < scripts.size(); i++) {
                String coverAssetId = scripts.get(i).getCoverAssetId();
                if (coverAssetId != null && assetUrlMap.containsKey(coverAssetId)) {
                    responses.get(i).setCoverUrl(assetUrlMap.get(coverAssetId));
                }
            }
        } catch (Exception e) {
            log.warn("批量获取剧本封面素材失败", e);
        }
    }
}
