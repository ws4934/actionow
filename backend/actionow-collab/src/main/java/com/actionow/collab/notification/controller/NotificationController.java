package com.actionow.collab.notification.controller;

import com.actionow.collab.notification.dto.*;
import com.actionow.collab.notification.entity.NotificationPreference;
import com.actionow.collab.notification.service.NotificationService;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.result.PageResult;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireLogin;
import com.actionow.common.security.annotation.RequireWorkspaceMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/collab/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @RequireLogin
    public Result<PageResult<NotificationResponse>> list(
            @RequestParam(required = false) String workspaceId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean isRead,
            @RequestParam(defaultValue = "1") Long pageNum,
            @RequestParam(defaultValue = "20") Long pageSize) {
        String userId = UserContextHolder.getUserId();
        return Result.success(notificationService.listByUser(userId, workspaceId, type, isRead, pageNum, pageSize));
    }

    @GetMapping("/unread/count")
    @RequireLogin
    public Result<UnreadCountResponse> unreadCount() {
        String userId = UserContextHolder.getUserId();
        return Result.success(notificationService.getUnreadCount(userId));
    }

    @PutMapping("/{id}/read")
    @RequireLogin
    public Result<Void> markAsRead(@PathVariable String id) {
        String userId = UserContextHolder.getUserId();
        notificationService.markAsRead(id, userId);
        return Result.success();
    }

    @PutMapping("/read/batch")
    @RequireLogin
    public Result<Void> batchMarkAsRead(@RequestBody @Valid BatchMarkReadRequest request) {
        String userId = UserContextHolder.getUserId();
        notificationService.batchMarkAsRead(request.getIds(), userId);
        return Result.success();
    }

    @PutMapping("/read/all")
    @RequireLogin
    public Result<Void> markAllAsRead() {
        String userId = UserContextHolder.getUserId();
        notificationService.markAllAsRead(userId);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @RequireLogin
    public Result<Void> delete(@PathVariable String id) {
        String userId = UserContextHolder.getUserId();
        notificationService.delete(id, userId);
        return Result.success();
    }

    @GetMapping("/preferences")
    @RequireWorkspaceMember
    public Result<NotificationPreference> getPreferences() {
        String userId = UserContextHolder.getUserId();
        String workspaceId = UserContextHolder.getWorkspaceId();
        return Result.success(notificationService.getPreference(userId, workspaceId));
    }

    @PutMapping("/preferences")
    @RequireWorkspaceMember
    public Result<Void> updatePreferences(@RequestBody NotificationPreferenceRequest request) {
        String userId = UserContextHolder.getUserId();
        String workspaceId = UserContextHolder.getWorkspaceId();
        notificationService.updatePreference(userId, workspaceId, request);
        return Result.success();
    }
}
