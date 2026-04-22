package com.actionow.collab.controller;

import com.actionow.collab.dto.EntityCollaboration;
import com.actionow.collab.dto.ScriptCollaboration;
import com.actionow.collab.dto.UserLocation;
import com.actionow.collab.manager.EntityCollaborationManager;
import com.actionow.collab.manager.PresenceManager;
import com.actionow.collab.manager.SessionManager;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.IgnoreAuth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 协作服务内部 API
 * 供其他微服务调用
 *
 * @author Actionow
 */
@Slf4j
@RestController
@RequestMapping("/internal/collab")
@RequiredArgsConstructor
@IgnoreAuth
public class CollaborationInternalController {

    private final SessionManager sessionManager;
    private final PresenceManager presenceManager;
    private final EntityCollaborationManager entityCollabManager;

    /**
     * 获取剧本在线用户列表
     */
    @GetMapping("/script/{scriptId}/users")
    public Result<List<UserLocation>> getScriptUsers(@PathVariable String scriptId) {
        List<UserLocation> users = sessionManager.getScriptUserLocations(scriptId);
        return Result.success(users);
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
     * 批量获取实体协作状态
     */
    @PostMapping("/entity/collaboration/batch")
    public Result<Map<String, EntityCollaboration>> batchGetEntityCollaboration(
            @RequestParam String entityType,
            @RequestBody List<String> entityIds) {
        Map<String, EntityCollaboration> result = entityCollabManager.batchGetEntityCollaboration(entityType, entityIds);
        return Result.success(result);
    }

    /**
     * 检查实体是否被编辑
     */
    @GetMapping("/entity/{entityType}/{entityId}/editing")
    public Result<Boolean> isEntityBeingEdited(
            @PathVariable String entityType,
            @PathVariable String entityId) {
        boolean isEditing = entityCollabManager.isEntityBeingEdited(entityType, entityId);
        return Result.success(isEditing);
    }

    /**
     * 获取实体当前编辑者
     */
    @GetMapping("/entity/{entityType}/{entityId}/editor")
    public Result<Object> getEntityEditor(
            @PathVariable String entityType,
            @PathVariable String entityId) {
        var editor = entityCollabManager.getCurrentEditor(entityType, entityId);
        return Result.success(editor);
    }

    private Map<String, Integer> countUsersByTab(List<UserLocation> users) {
        return users.stream()
                .filter(u -> u.getTab() != null)
                .collect(Collectors.groupingBy(UserLocation::getTab, Collectors.summingInt(u -> 1)));
    }
}
