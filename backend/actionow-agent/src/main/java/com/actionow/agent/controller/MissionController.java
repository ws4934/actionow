package com.actionow.agent.controller;

import com.actionow.agent.dto.response.MissionProgressResponse;
import com.actionow.agent.dto.response.MissionResponse;
import com.actionow.agent.dto.response.MissionStepResponse;
import com.actionow.agent.dto.response.MissionTaskResponse;
import com.actionow.agent.dto.response.MissionEventResponse;
import com.actionow.agent.dto.response.MissionTraceResponse;
import com.actionow.agent.service.MissionService;
import com.actionow.agent.service.MissionSseService;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.result.PageResult;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireWorkspaceMember;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * Mission 控制器
 * 提供 Mission 进度查询和管理 API
 *
 * @author Actionow
 */
@Slf4j
@RestController
@RequestMapping("/agent/missions")
@RequiredArgsConstructor
public class MissionController {

    private final MissionService missionService;
    private final MissionSseService missionSseService;

    /**
     * 获取 Mission 详情
     */
    @GetMapping("/{id}")
    @RequireWorkspaceMember
    public Result<MissionResponse> getMission(@PathVariable String id) {
        MissionResponse response = missionService.getById(id);
        return Result.success(response);
    }

    /**
     * 分页列表查询（workspace 维度）
     */
    @GetMapping
    @RequireWorkspaceMember
    public Result<PageResult<MissionResponse>> listMissions(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "20") Long size,
            @RequestParam(required = false) String status) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        PageResult<MissionResponse> result = missionService.listByWorkspace(workspaceId, current, size, status);
        return Result.success(result);
    }

    /**
     * 获取 Mission 进度（轻量级）
     */
    @GetMapping("/{id}/progress")
    @RequireWorkspaceMember
    public Result<MissionProgressResponse> getProgress(@PathVariable String id) {
        MissionProgressResponse response = missionService.getProgress(id);
        return Result.success(response);
    }

    /**
     * 获取 Mission 执行步骤列表
     */
    @GetMapping("/{id}/steps")
    @RequireWorkspaceMember
    public Result<List<MissionStepResponse>> getSteps(@PathVariable String id) {
        List<MissionStepResponse> steps = missionService.getSteps(id);
        return Result.success(steps);
    }

    /**
     * 获取 Mission 任务列表。
     */
    @GetMapping("/{id}/tasks")
    @RequireWorkspaceMember
    public Result<List<MissionTaskResponse>> getTasks(@PathVariable String id) {
        return Result.success(missionService.getTasks(id));
    }

    /**
     * 获取 Mission 事件列表。
     */
    @GetMapping("/{id}/events")
    @RequireWorkspaceMember
    public Result<List<MissionEventResponse>> getEvents(@PathVariable String id) {
        return Result.success(missionService.getEvents(id));
    }

    /**
     * 获取 Mission 轨迹列表。
     */
    @GetMapping("/{id}/traces")
    @RequireWorkspaceMember
    public Result<List<MissionTraceResponse>> getTraces(@PathVariable String id) {
        return Result.success(missionService.getTraces(id));
    }

    /**
     * 取消 Mission
     */
    @PostMapping("/{id}/cancel")
    @RequireWorkspaceMember
    public Result<Void> cancelMission(@PathVariable String id) {
        String userId = UserContextHolder.getUserId();
        missionService.cancel(id, userId);
        return Result.success();
    }

    /**
     * SSE 实时推送 Mission 进度
     */
    @GetMapping(value = "/{id}/progress/stream", produces = "text/event-stream")
    @RequireWorkspaceMember
    public SseEmitter streamProgress(@PathVariable String id, HttpServletResponse response) {
        missionService.getById(id); // 验证存在
        String userId = UserContextHolder.getUserId();
        String workspaceId = UserContextHolder.getWorkspaceId();
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        return missionSseService.createConnection(id, userId, workspaceId);
    }
}
