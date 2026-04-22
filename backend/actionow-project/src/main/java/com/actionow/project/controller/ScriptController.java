package com.actionow.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireWorkspaceMember;
import com.actionow.common.security.annotation.RequireWorkspaceMember.WorkspaceRole;
import com.actionow.project.dto.*;
import com.actionow.project.dto.version.RestoreVersionRequest;
import com.actionow.project.dto.version.ScriptVersionDetailResponse;
import com.actionow.project.dto.version.VersionInfoResponse;
import com.actionow.project.service.ScriptService;
import com.actionow.project.service.version.ScriptVersionServiceImpl;
import com.actionow.project.service.version.VersionDiffResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 剧本控制器
 *
 * @author Actionow
 */
@RestController
@RequestMapping("/scripts")
@RequiredArgsConstructor
public class ScriptController {

    private final ScriptService scriptService;
    private final ScriptVersionServiceImpl scriptVersionService;

    /**
     * 创建剧本
     * 注：创建权限检查已移至 ScriptServiceImpl（支持租户级 memberCanCreateScript 开关）
     */
    @PostMapping
    @RequireWorkspaceMember
    public Result<ScriptDetailResponse> createScript(@RequestBody @Valid CreateScriptRequest request) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        ScriptDetailResponse response = scriptService.create(request, workspaceId, userId);
        return Result.success(response);
    }

    /**
     * 更新剧本
     */
    @PutMapping("/{scriptId}")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<ScriptDetailResponse> updateScript(
            @PathVariable String scriptId,
            @RequestBody @Valid UpdateScriptRequest request) {
        String userId = UserContextHolder.getUserId();
        ScriptDetailResponse response = scriptService.update(scriptId, request, userId);
        return Result.success(response);
    }

    /**
     * 删除剧本
     */
    @DeleteMapping("/{scriptId}")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Void> deleteScript(@PathVariable String scriptId) {
        String userId = UserContextHolder.getUserId();
        scriptService.delete(scriptId, userId);
        return Result.success();
    }

    /**
     * 获取工作空间的剧本列表
     */
    @GetMapping
    @RequireWorkspaceMember
    public Result<List<ScriptListResponse>> listScripts(
            @RequestParam(required = false) String status) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        List<ScriptListResponse> scripts;
        if (status != null && !status.isEmpty()) {
            scripts = scriptService.listByStatus(workspaceId, status);
        } else {
            scripts = scriptService.listByWorkspace(workspaceId);
        }
        return Result.success(scripts);
    }

    /**
     * 分页查询剧本（支持搜索和过滤）
     */
    @GetMapping("/query")
    @RequireWorkspaceMember
    public Result<Page<ScriptListResponse>> queryScripts(ScriptQueryRequest request) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        Page<ScriptListResponse> page = scriptService.queryScripts(request, workspaceId);
        return Result.success(page);
    }

    /**
     * 获取剧本详情
     */
    @GetMapping("/{scriptId}")
    @RequireWorkspaceMember
    public Result<ScriptDetailResponse> getScript(@PathVariable String scriptId) {
        ScriptDetailResponse response = scriptService.getById(scriptId);
        return Result.success(response);
    }

    /**
     * 更新剧本状态
     */
    @PatchMapping("/{scriptId}/status")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Void> updateScriptStatus(
            @PathVariable String scriptId,
            @RequestParam String status) {
        String userId = UserContextHolder.getUserId();
        scriptService.updateStatus(scriptId, status, userId);
        return Result.success();
    }

    /**
     * 归档剧本
     */
    @PostMapping("/{scriptId}/archive")
    @RequireWorkspaceMember(minRole = WorkspaceRole.ADMIN)
    public Result<Void> archiveScript(@PathVariable String scriptId) {
        String userId = UserContextHolder.getUserId();
        scriptService.archive(scriptId, userId);
        return Result.success();
    }

    /**
     * 设置剧本封面
     */
    @PutMapping("/{scriptId}/cover")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Void> setCover(
            @PathVariable String scriptId,
            @RequestParam String assetId) {
        String userId = UserContextHolder.getUserId();
        scriptService.setCover(scriptId, assetId, userId);
        return Result.success();
    }

    // ==================== 版本管理接口 ====================

    /**
     * 获取剧本的版本列表
     */
    @GetMapping("/{scriptId}/versions")
    @RequireWorkspaceMember
    public Result<List<VersionInfoResponse>> listVersions(@PathVariable String scriptId) {
        List<VersionInfoResponse> versions = scriptVersionService.listVersions(scriptId);
        return Result.success(versions);
    }

    /**
     * 获取剧本的指定版本详情
     */
    @GetMapping("/{scriptId}/versions/{versionNumber}")
    @RequireWorkspaceMember
    public Result<ScriptVersionDetailResponse> getVersion(
            @PathVariable String scriptId,
            @PathVariable Integer versionNumber) {
        ScriptVersionDetailResponse version = scriptVersionService.getVersion(scriptId, versionNumber);
        return Result.success(version);
    }

    /**
     * 获取剧本的当前版本
     */
    @GetMapping("/{scriptId}/versions/current")
    @RequireWorkspaceMember
    public Result<ScriptVersionDetailResponse> getCurrentVersion(@PathVariable String scriptId) {
        ScriptVersionDetailResponse version = scriptVersionService.getCurrentVersion(scriptId);
        return Result.success(version);
    }

    /**
     * 恢复剧本到指定版本
     */
    @PostMapping("/{scriptId}/versions/restore")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Integer> restoreVersion(
            @PathVariable String scriptId,
            @RequestBody @Valid RestoreVersionRequest request) {
        String userId = UserContextHolder.getUserId();
        Integer newVersionNumber = scriptVersionService.restoreVersion(scriptId, request, userId);
        return Result.success(newVersionNumber);
    }

    /**
     * 比较两个版本的差异
     */
    @GetMapping("/{scriptId}/versions/diff")
    @RequireWorkspaceMember
    public Result<VersionDiffResponse> compareVersions(
            @PathVariable String scriptId,
            @RequestParam Integer version1,
            @RequestParam Integer version2) {
        VersionDiffResponse diff = scriptVersionService.compareVersions(scriptId, version1, version2);
        return Result.success(diff);
    }
}
