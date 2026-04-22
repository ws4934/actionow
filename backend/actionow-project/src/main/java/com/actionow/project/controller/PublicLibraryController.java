package com.actionow.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireLogin;
import com.actionow.common.security.annotation.RequireWorkspaceMember;
import com.actionow.project.dto.*;
import com.actionow.project.dto.asset.AssetResponse;
import com.actionow.project.dto.library.*;
import com.actionow.project.service.PublicLibraryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 公共资源库控制器
 * 浏览接口：所有已登录用户可访问（@RequireLogin）
 * 复制接口：需要工作空间成员身份（@RequireWorkspaceMember）
 *
 * @author Actionow
 */
@RestController
@RequestMapping("/library")
@RequiredArgsConstructor
public class PublicLibraryController {

    private final PublicLibraryService libraryService;

    // ==================== 角色 ====================

    @GetMapping("/characters")
    @RequireLogin
    public Result<Page<LibraryCharacterResponse>> listCharacters(LibraryQueryRequest request) {
        return Result.success(libraryService.listCharacters(request));
    }

    @GetMapping("/characters/{id}")
    @RequireLogin
    public Result<LibraryCharacterResponse> getCharacter(@PathVariable String id) {
        return Result.success(libraryService.getCharacter(id));
    }

    /**
     * 复制公共库角色到当前工作空间
     */
    @PostMapping("/characters/{id}/copy")
    @RequireWorkspaceMember(minRole = RequireWorkspaceMember.WorkspaceRole.MEMBER)
    public Result<CharacterDetailResponse> copyCharacter(@PathVariable String id) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        return Result.success(libraryService.copyCharacterToWorkspace(id, workspaceId, userId));
    }

    // ==================== 场景 ====================

    @GetMapping("/scenes")
    @RequireLogin
    public Result<Page<LibrarySceneResponse>> listScenes(LibraryQueryRequest request) {
        return Result.success(libraryService.listScenes(request));
    }

    @GetMapping("/scenes/{id}")
    @RequireLogin
    public Result<LibrarySceneResponse> getScene(@PathVariable String id) {
        return Result.success(libraryService.getScene(id));
    }

    @PostMapping("/scenes/{id}/copy")
    @RequireWorkspaceMember(minRole = RequireWorkspaceMember.WorkspaceRole.MEMBER)
    public Result<SceneDetailResponse> copyScene(@PathVariable String id) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        return Result.success(libraryService.copySceneToWorkspace(id, workspaceId, userId));
    }

    // ==================== 道具 ====================

    @GetMapping("/props")
    @RequireLogin
    public Result<Page<LibraryPropResponse>> listProps(LibraryQueryRequest request) {
        return Result.success(libraryService.listProps(request));
    }

    @GetMapping("/props/{id}")
    @RequireLogin
    public Result<LibraryPropResponse> getProp(@PathVariable String id) {
        return Result.success(libraryService.getProp(id));
    }

    @PostMapping("/props/{id}/copy")
    @RequireWorkspaceMember(minRole = RequireWorkspaceMember.WorkspaceRole.MEMBER)
    public Result<PropDetailResponse> copyProp(@PathVariable String id) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        return Result.success(libraryService.copyPropToWorkspace(id, workspaceId, userId));
    }

    // ==================== 风格 ====================

    @GetMapping("/styles")
    @RequireLogin
    public Result<Page<LibraryStyleResponse>> listStyles(LibraryQueryRequest request) {
        return Result.success(libraryService.listStyles(request));
    }

    @GetMapping("/styles/{id}")
    @RequireLogin
    public Result<LibraryStyleResponse> getStyle(@PathVariable String id) {
        return Result.success(libraryService.getStyle(id));
    }

    @PostMapping("/styles/{id}/copy")
    @RequireWorkspaceMember(minRole = RequireWorkspaceMember.WorkspaceRole.MEMBER)
    public Result<StyleDetailResponse> copyStyle(@PathVariable String id) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        return Result.success(libraryService.copyStyleToWorkspace(id, workspaceId, userId));
    }

    // ==================== 素材 ====================

    @GetMapping("/assets")
    @RequireLogin
    public Result<Page<LibraryAssetResponse>> listAssets(LibraryQueryRequest request) {
        return Result.success(libraryService.listAssets(request));
    }

    @GetMapping("/assets/{id}")
    @RequireLogin
    public Result<LibraryAssetResponse> getAsset(@PathVariable String id) {
        return Result.success(libraryService.getAsset(id));
    }

    @PostMapping("/assets/{id}/copy")
    @RequireWorkspaceMember(minRole = RequireWorkspaceMember.WorkspaceRole.MEMBER)
    public Result<AssetResponse> copyAsset(@PathVariable String id) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        return Result.success(libraryService.copyAssetToWorkspace(id, workspaceId, userId));
    }
}
