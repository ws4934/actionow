package com.actionow.collab.activity.controller;

import com.actionow.collab.activity.dto.ActivityFeedItem;
import com.actionow.collab.activity.service.ActivityFeedService;
import com.actionow.common.core.result.PageResult;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireWorkspaceMember;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/collab/activities")
@RequiredArgsConstructor
public class ActivityFeedController {

    private final ActivityFeedService activityFeedService;

    @GetMapping("/{entityType}/{entityId}")
    @RequireWorkspaceMember
    public Result<PageResult<ActivityFeedItem>> getEntityActivities(
            @PathVariable String entityType,
            @PathVariable String entityId,
            @RequestParam(defaultValue = "1") Long pageNum,
            @RequestParam(defaultValue = "20") Long pageSize) {
        return Result.success(activityFeedService.getEntityActivities(entityType, entityId, pageNum, pageSize));
    }

    @GetMapping("/script/{scriptId}")
    @RequireWorkspaceMember
    public Result<PageResult<ActivityFeedItem>> getScriptActivities(
            @PathVariable String scriptId,
            @RequestParam(defaultValue = "1") Long pageNum,
            @RequestParam(defaultValue = "20") Long pageSize) {
        return Result.success(activityFeedService.getScriptActivities(scriptId, pageNum, pageSize));
    }
}
