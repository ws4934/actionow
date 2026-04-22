package com.actionow.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireWorkspaceMember;
import com.actionow.common.security.annotation.RequireWorkspaceMember.WorkspaceRole;
import com.actionow.project.dto.asset.*;
import com.actionow.project.dto.version.AssetVersionDetailResponse;
import com.actionow.project.dto.version.RestoreVersionRequest;
import com.actionow.project.dto.version.VersionInfoResponse;
import com.actionow.project.service.AssetService;
import com.actionow.project.service.version.AssetVersionServiceImpl;
import com.actionow.project.service.version.VersionDiffResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 素材控制器
 * 提供素材实体的 CRUD 操作
 *
 * @author Actionow
 */
@RestController
@RequestMapping("/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;
    private final AssetVersionServiceImpl assetVersionService;

    /**
     * 创建素材（元数据）
     */
    @PostMapping
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<AssetResponse> create(@RequestBody @Valid CreateAssetRequest request) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        AssetResponse response = assetService.create(request, workspaceId, userId);
        return Result.success(response);
    }

    // ==================== 上传接口（推荐使用） ====================

    /**
     * 初始化素材上传
     * 一次请求完成：创建素材记录 + 获取预签名上传 URL
     *
     * 使用流程：
     * 1. 调用此接口，获取 assetId 和预签名 uploadUrl
     * 2. 前端使用 uploadUrl 直接上传文件到 OSS
     * 3. 上传完成后调用 POST /assets/{assetId}/upload/confirm 确认
     */
    @PostMapping("/upload/init")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<AssetUploadInitResponse> initUpload(@RequestBody @Valid AssetUploadInitRequest request) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        AssetUploadInitResponse response = assetService.initUpload(request, workspaceId, userId);
        return Result.success(response);
    }

    /**
     * 确认素材上传完成
     * 验证文件已上传到 OSS，更新素材状态，生成缩略图
     */
    @PostMapping("/{assetId}/upload/confirm")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<AssetResponse> confirmUpload(
            @PathVariable String assetId,
            @RequestBody @Valid AssetUploadConfirmRequest request) {
        String userId = UserContextHolder.getUserId();
        AssetResponse response = assetService.confirmUpload(assetId, request, userId);
        return Result.success(response);
    }

    /**
     * 更新素材信息
     */
    @PutMapping("/{assetId}")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<AssetResponse> update(
            @PathVariable String assetId,
            @RequestBody @Valid UpdateAssetRequest request) {
        String userId = UserContextHolder.getUserId();
        AssetResponse response = assetService.update(assetId, request, userId);
        return Result.success(response);
    }

    /**
     * 删除素材
     */
    @DeleteMapping("/{assetId}")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Void> delete(@PathVariable String assetId) {
        String userId = UserContextHolder.getUserId();
        assetService.delete(assetId, userId);
        return Result.success();
    }

    /**
     * 获取素材详情
     */
    @GetMapping("/{assetId}")
    @RequireWorkspaceMember
    public Result<AssetResponse> getById(@PathVariable String assetId) {
        AssetResponse response = assetService.getById(assetId);
        return Result.success(response);
    }

    /**
     * 获取工作空间的素材列表
     */
    @GetMapping
    @RequireWorkspaceMember
    public Result<List<AssetResponse>> listByWorkspace(
            @RequestParam(required = false) String assetType) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        List<AssetResponse> assets;
        if (assetType != null && !assetType.isEmpty()) {
            assets = assetService.listByType(workspaceId, assetType);
        } else {
            assets = assetService.listByWorkspace(workspaceId);
        }
        return Result.success(assets);
    }

    /**
     * 根据剧本ID获取素材列表
     */
    @GetMapping("/script/{scriptId}")
    @RequireWorkspaceMember
    public Result<List<AssetResponse>> listByScript(@PathVariable String scriptId) {
        List<AssetResponse> assets = assetService.listByScript(scriptId);
        return Result.success(assets);
    }

    /**
     * 查询剧本下的游离素材（未挂载到任何实体的素材）
     */
    @GetMapping("/script/{scriptId}/unattached")
    @RequireWorkspaceMember
    public Result<Page<AssetResponse>> listUnattachedByScript(
            @PathVariable String scriptId,
            @RequestParam(required = false) String assetType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        Page<AssetResponse> result = assetService.listUnattachedByScript(scriptId, workspaceId, assetType, page, size);
        return Result.success(result);
    }

    /**
     * 分页查询素材
     */
    @GetMapping("/query")
    @RequireWorkspaceMember
    public Result<Page<AssetResponse>> queryAssets(AssetQueryRequest request) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        Page<AssetResponse> page = assetService.queryAssets(request, workspaceId);
        return Result.success(page);
    }

    /**
     * 批量获取素材
     */
    @PostMapping("/batch")
    @RequireWorkspaceMember
    public Result<List<AssetResponse>> batchGet(@RequestBody List<String> assetIds) {
        List<AssetResponse> assets = assetService.batchGet(assetIds);
        return Result.success(assets);
    }

    /**
     * 复制素材
     * 基于源素材创建新记录（共享同一文件），可选同时挂载到目标实体
     */
    @PostMapping("/{assetId}/copy")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<AssetResponse> copyAsset(
            @PathVariable String assetId,
            @RequestBody @Valid CopyAssetRequest request) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        AssetResponse response = assetService.copyAsset(assetId, request, workspaceId, userId);
        return Result.success(response);
    }

    // ==================== 版本管理接口 ====================

    /**
     * 获取素材的版本列表
     */
    @GetMapping("/{assetId}/versions")
    @RequireWorkspaceMember
    public Result<List<VersionInfoResponse>> listVersions(@PathVariable String assetId) {
        List<VersionInfoResponse> versions = assetVersionService.listVersions(assetId);
        return Result.success(versions);
    }

    /**
     * 获取素材的指定版本详情
     */
    @GetMapping("/{assetId}/versions/{versionNumber}")
    @RequireWorkspaceMember
    public Result<AssetVersionDetailResponse> getVersion(
            @PathVariable String assetId,
            @PathVariable Integer versionNumber) {
        AssetVersionDetailResponse version = assetVersionService.getVersion(assetId, versionNumber);
        return Result.success(version);
    }

    /**
     * 获取素材的当前版本
     */
    @GetMapping("/{assetId}/versions/current")
    @RequireWorkspaceMember
    public Result<AssetVersionDetailResponse> getCurrentVersion(@PathVariable String assetId) {
        AssetVersionDetailResponse version = assetVersionService.getCurrentVersion(assetId);
        return Result.success(version);
    }

    /**
     * 恢复素材到指定版本
     */
    @PostMapping("/{assetId}/versions/restore")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Integer> restoreVersion(
            @PathVariable String assetId,
            @RequestBody @Valid RestoreVersionRequest request) {
        String userId = UserContextHolder.getUserId();
        Integer newVersionNumber = assetVersionService.restoreVersion(assetId, request, userId);
        return Result.success(newVersionNumber);
    }

    /**
     * 比较两个版本的差异
     */
    @GetMapping("/{assetId}/versions/diff")
    @RequireWorkspaceMember
    public Result<VersionDiffResponse> compareVersions(
            @PathVariable String assetId,
            @RequestParam Integer version1,
            @RequestParam Integer version2) {
        VersionDiffResponse diff = assetVersionService.compareVersions(assetId, version1, version2);
        return Result.success(diff);
    }

    // ==================== 回收站接口 ====================

    /**
     * 获取回收站素材列表
     */
    @GetMapping("/trash")
    @RequireWorkspaceMember
    public Result<Page<AssetResponse>> listTrash(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        Page<AssetResponse> trashPage = assetService.listTrash(workspaceId, page, size);
        return Result.success(trashPage);
    }

    /**
     * 从回收站恢复素材
     */
    @PostMapping("/{assetId}/restore")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<AssetResponse> restoreFromTrash(@PathVariable String assetId) {
        String userId = UserContextHolder.getUserId();
        AssetResponse response = assetService.restoreFromTrash(assetId, userId);
        return Result.success(response);
    }

    /**
     * 永久删除素材（从回收站彻底删除）
     */
    @DeleteMapping("/{assetId}/permanent")
    @RequireWorkspaceMember(minRole = WorkspaceRole.ADMIN)
    public Result<Void> permanentDelete(@PathVariable String assetId) {
        String userId = UserContextHolder.getUserId();
        assetService.permanentDelete(assetId, userId);
        return Result.success();
    }

    /**
     * 清空回收站
     */
    @DeleteMapping("/trash")
    @RequireWorkspaceMember(minRole = WorkspaceRole.ADMIN)
    public Result<Integer> emptyTrash() {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        int count = assetService.emptyTrash(workspaceId, userId);
        return Result.success(count);
    }
}
