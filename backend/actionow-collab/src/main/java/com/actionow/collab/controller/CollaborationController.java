package com.actionow.collab.controller;

import com.actionow.collab.dto.EntityCollaboration;
import com.actionow.collab.dto.ScriptCollaboration;
import com.actionow.collab.dto.UserLocation;
import com.actionow.collab.dto.UserPresence;
import com.actionow.collab.manager.EntityCollaborationManager;
import com.actionow.collab.manager.PresenceManager;
import com.actionow.collab.manager.SessionManager;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireWorkspaceMember;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 协作服务 REST API
 * 提供协作状态查询接口
 *
 * @author Actionow
 */
@Slf4j
@RestController
@RequestMapping("/collab")
@RequiredArgsConstructor
@RequireWorkspaceMember
public class CollaborationController {

    private final SessionManager sessionManager;
    private final PresenceManager presenceManager;
    private final EntityCollaborationManager entityCollabManager;

    /**
     * 获取工作空间在线用户
     */
    @GetMapping("/workspace/{workspaceId}/online")
    public Result<List<UserPresence>> getWorkspaceOnlineUsers(@PathVariable String workspaceId) {
        List<UserPresence> users = presenceManager.getWorkspaceOnlineUsers(workspaceId);
        return Result.success(users);
    }

    /**
     * 获取工作空间在线用户数
     */
    @GetMapping("/workspace/{workspaceId}/online/count")
    public Result<Long> getWorkspaceOnlineCount(@PathVariable String workspaceId) {
        long count = presenceManager.getWorkspaceOnlineCount(workspaceId);
        return Result.success(count);
    }

    /**
     * 获取剧本协作状态
     */
    @GetMapping("/script/{scriptId}/collaboration")
    public Result<ScriptCollaboration> getScriptCollaboration(@PathVariable String scriptId) {
        List<UserLocation> users = sessionManager.getScriptUserLocations(scriptId);

        ScriptCollaboration collab = ScriptCollaboration.builder()
                .scriptId(scriptId)
                .totalUsers(users.size())
                .users(users)
                .tabUserCounts(countUsersByTab(users))
                .build();

        return Result.success(collab);
    }

    /**
     * 批量获取剧本协作状态
     */
    @PostMapping("/script/collaboration/batch")
    public Result<Map<String, ScriptCollaboration>> batchGetScriptCollaboration(
            @RequestBody List<String> scriptIds) {
        Map<String, List<UserLocation>> userLocationsMap = sessionManager.batchGetScriptUserLocations(scriptIds);

        Map<String, ScriptCollaboration> result = new HashMap<>();
        for (Map.Entry<String, List<UserLocation>> entry : userLocationsMap.entrySet()) {
            String scriptId = entry.getKey();
            List<UserLocation> users = entry.getValue();
            ScriptCollaboration collab = ScriptCollaboration.builder()
                    .scriptId(scriptId)
                    .totalUsers(users.size())
                    .users(users)
                    .tabUserCounts(countUsersByTab(users))
                    .build();
            result.put(scriptId, collab);
        }

        return Result.success(result);
    }

    /**
     * 获取剧本在线用户数
     */
    @GetMapping("/script/{scriptId}/online/count")
    public Result<Long> getScriptOnlineCount(@PathVariable String scriptId) {
        long count = presenceManager.getScriptOnlineCount(scriptId);
        return Result.success(count);
    }

    /**
     * 获取实体协作状态
     */
    @GetMapping("/entity/{entityType}/{entityId}/collaboration")
    public Result<EntityCollaboration> getEntityCollaboration(
            @PathVariable String entityType,
            @PathVariable String entityId) {
        EntityCollaboration collab = entityCollabManager.getEntityCollaboration(entityType, entityId);
        return Result.success(collab);
    }

    /**
     * 批量获取实体协作状态
     */
    @PostMapping("/entity/{entityType}/collaboration/batch")
    public Result<Map<String, EntityCollaboration>> batchGetEntityCollaboration(
            @PathVariable String entityType,
            @RequestBody List<String> entityIds) {
        Map<String, EntityCollaboration> result = entityCollabManager.batchGetEntityCollaboration(entityType, entityIds);
        return Result.success(result);
    }

    /**
     * 检查实体是否被编辑
     */
    @GetMapping("/entity/{entityType}/{entityId}/editing")
    public Result<Map<String, Object>> checkEntityEditing(
            @PathVariable String entityType,
            @PathVariable String entityId) {
        boolean isEditing = entityCollabManager.isEntityBeingEdited(entityType, entityId);
        var editor = entityCollabManager.getCurrentEditor(entityType, entityId);

        return Result.success(Map.of(
                "isEditing", isEditing,
                "editor", editor != null ? editor : Map.of()
        ));
    }

    /**
     * 检查用户是否在线
     */
    @GetMapping("/workspace/{workspaceId}/user/{userId}/online")
    public Result<Boolean> checkUserOnline(
            @PathVariable String workspaceId,
            @PathVariable String userId) {
        boolean isOnline = presenceManager.isUserOnline(workspaceId, userId);
        return Result.success(isOnline);
    }

    /**
     * 检查用户是否在剧本中
     */
    @GetMapping("/script/{scriptId}/user/{userId}/present")
    public Result<Boolean> checkUserInScript(
            @PathVariable String scriptId,
            @PathVariable String userId) {
        boolean isPresent = presenceManager.isUserInScript(scriptId, userId);
        return Result.success(isPresent);
    }

    private Map<String, Integer> countUsersByTab(List<UserLocation> users) {
        return users.stream()
                .filter(u -> u.getTab() != null)
                .collect(Collectors.groupingBy(UserLocation::getTab, Collectors.summingInt(u -> 1)));
    }
}
