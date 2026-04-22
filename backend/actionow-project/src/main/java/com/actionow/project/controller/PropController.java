package com.actionow.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireWorkspaceMember;
import com.actionow.common.security.annotation.RequireWorkspaceMember.WorkspaceRole;
import com.actionow.project.dto.CreatePropRequest;
import com.actionow.project.dto.UpdatePropRequest;
import com.actionow.project.dto.PropQueryRequest;
import com.actionow.project.dto.PropDetailResponse;
import com.actionow.project.dto.PropListResponse;
import com.actionow.project.dto.version.PropVersionDetailResponse;
import com.actionow.project.dto.version.RestoreVersionRequest;
import com.actionow.project.dto.version.VersionInfoResponse;
import com.actionow.project.service.PropService;
import com.actionow.project.service.version.PropVersionServiceImpl;
import com.actionow.project.service.version.VersionDiffResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 道具控制器
 * workspaceId 从请求头获取（通过 UserContextHolder）
 *
 * @author Actionow
 */
@RestController
@RequestMapping("/props")
@RequiredArgsConstructor
public class PropController {

    private final PropService propService;
    private final PropVersionServiceImpl propVersionService;

    /**
     * 创建道具
     */
    @PostMapping
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<PropDetailResponse> createProp(@RequestBody @Valid CreatePropRequest request) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        PropDetailResponse response = propService.create(request, workspaceId, userId);
        return Result.success(response);
    }

    /**
     * 更新道具
     */
    @PutMapping("/{propId}")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<PropDetailResponse> updateProp(
            @PathVariable String propId,
            @RequestBody @Valid UpdatePropRequest request) {
        String userId = UserContextHolder.getUserId();
        PropDetailResponse response = propService.update(propId, request, userId);
        return Result.success(response);
    }

    /**
     * 删除道具
     */
    @DeleteMapping("/{propId}")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Void> deleteProp(@PathVariable String propId) {
        String userId = UserContextHolder.getUserId();
        propService.delete(propId, userId);
        return Result.success();
    }

    /**
     * 获取工作空间级道具列表
     */
    @GetMapping
    @RequireWorkspaceMember
    public Result<List<PropListResponse>> listWorkspaceProps() {
        String workspaceId = UserContextHolder.getWorkspaceId();
        List<PropListResponse> props = propService.listWorkspaceProps(workspaceId);
        return Result.success(props);
    }

    /**
     * 分页查询道具（支持搜索和过滤）
     */
    @GetMapping("/query")
    @RequireWorkspaceMember
    public Result<Page<PropListResponse>> queryProps(PropQueryRequest request) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        Page<PropListResponse> page = propService.queryProps(request, workspaceId);
        return Result.success(page);
    }

    /**
     * 获取剧本级道具列表
     */
    @GetMapping("/script/{scriptId}")
    @RequireWorkspaceMember
    public Result<List<PropListResponse>> listScriptProps(@PathVariable String scriptId) {
        List<PropListResponse> props = propService.listScriptProps(scriptId);
        return Result.success(props);
    }

    /**
     * 获取剧本可用的所有道具
     * 支持模糊搜索和数量限制
     *
     * @param scriptId 剧本ID
     * @param keyword 搜索关键词（匹配名称、描述）
     * @param limit 返回数量限制，默认50
     */
    @GetMapping("/available/{scriptId}")
    @RequireWorkspaceMember
    public Result<List<PropListResponse>> listAvailableProps(
            @PathVariable String scriptId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "50") Integer limit) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        List<PropListResponse> props = propService.listAvailableProps(workspaceId, scriptId, keyword, limit);
        return Result.success(props);
    }

    /**
     * 获取道具详情
     */
    @GetMapping("/{propId}")
    @RequireWorkspaceMember
    public Result<PropDetailResponse> getProp(@PathVariable String propId) {
        PropDetailResponse response = propService.getById(propId);
        return Result.success(response);
    }

    /**
     * 设置道具封面
     */
    @PutMapping("/{propId}/cover")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Void> setCover(
            @PathVariable String propId,
            @RequestParam String assetId) {
        String userId = UserContextHolder.getUserId();
        propService.setCover(propId, assetId, userId);
        return Result.success();
    }

    // ==================== 版本管理接口 ====================

    /**
     * 获取道具的版本列表
     */
    @GetMapping("/{propId}/versions")
    @RequireWorkspaceMember
    public Result<List<VersionInfoResponse>> listVersions(@PathVariable String propId) {
        List<VersionInfoResponse> versions = propVersionService.listVersions(propId);
        return Result.success(versions);
    }

    /**
     * 获取道具的指定版本详情
     */
    @GetMapping("/{propId}/versions/{versionNumber}")
    @RequireWorkspaceMember
    public Result<PropVersionDetailResponse> getVersion(
            @PathVariable String propId,
            @PathVariable Integer versionNumber) {
        PropVersionDetailResponse version = propVersionService.getVersion(propId, versionNumber);
        return Result.success(version);
    }

    /**
     * 获取道具的当前版本
     */
    @GetMapping("/{propId}/versions/current")
    @RequireWorkspaceMember
    public Result<PropVersionDetailResponse> getCurrentVersion(@PathVariable String propId) {
        PropVersionDetailResponse version = propVersionService.getCurrentVersion(propId);
        return Result.success(version);
    }

    /**
     * 恢复道具到指定版本
     */
    @PostMapping("/{propId}/versions/restore")
    @RequireWorkspaceMember(minRole = WorkspaceRole.MEMBER)
    public Result<Integer> restoreVersion(
            @PathVariable String propId,
            @RequestBody @Valid RestoreVersionRequest request) {
        String userId = UserContextHolder.getUserId();
        Integer newVersionNumber = propVersionService.restoreVersion(propId, request, userId);
        return Result.success(newVersionNumber);
    }

    /**
     * 比较两个版本的差异
     */
    @GetMapping("/{propId}/versions/diff")
    @RequireWorkspaceMember
    public Result<VersionDiffResponse> compareVersions(
            @PathVariable String propId,
            @RequestParam Integer version1,
            @RequestParam Integer version2) {
        VersionDiffResponse diff = propVersionService.compareVersions(propId, version1, version2);
        return Result.success(diff);
    }
}
