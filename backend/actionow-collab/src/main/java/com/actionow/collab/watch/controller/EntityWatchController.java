package com.actionow.collab.watch.controller;

import com.actionow.collab.watch.dto.WatchRequest;
import com.actionow.collab.watch.dto.WatchResponse;
import com.actionow.collab.watch.entity.EntityWatch;
import com.actionow.collab.watch.service.EntityWatchService;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireWorkspaceMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/collab/watches")
@RequiredArgsConstructor
public class EntityWatchController {

    private final EntityWatchService entityWatchService;

    @PostMapping
    @RequireWorkspaceMember
    public Result<Void> watch(@RequestBody @Valid WatchRequest request) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        entityWatchService.watch(request.getEntityType(), request.getEntityId(), workspaceId, userId);
        return Result.success();
    }

    @DeleteMapping("/{entityType}/{entityId}")
    @RequireWorkspaceMember
    public Result<Void> unwatch(@PathVariable String entityType, @PathVariable String entityId) {
        String userId = UserContextHolder.getUserId();
        entityWatchService.unwatch(entityType, entityId, userId);
        return Result.success();
    }

    @GetMapping("/{entityType}/{entityId}")
    @RequireWorkspaceMember
    public Result<WatchResponse> getWatchStatus(@PathVariable String entityType, @PathVariable String entityId) {
        String userId = UserContextHolder.getUserId();
        return Result.success(entityWatchService.getWatchStatus(entityType, entityId, userId));
    }

    @GetMapping("/my")
    @RequireWorkspaceMember
    public Result<List<EntityWatch>> getMyWatches() {
        String userId = UserContextHolder.getUserId();
        return Result.success(entityWatchService.getMyWatches(userId));
    }
}
