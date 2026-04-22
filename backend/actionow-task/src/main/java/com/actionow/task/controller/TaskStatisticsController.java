package com.actionow.task.controller;

import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireWorkspaceMember;
import com.actionow.task.service.TaskStatisticsService;
import com.actionow.task.service.TaskStatisticsService.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 任务统计控制器
 *
 * @author Actionow
 */
@Tag(name = "任务统计", description = "任务执行统计和分析")
@RestController
@RequestMapping("/tasks/stats")
@RequiredArgsConstructor
public class TaskStatisticsController {

    private final TaskStatisticsService statisticsService;

    /**
     * 获取工作空间任务统计概览
     */
    @Operation(summary = "获取工作空间任务统计概览")
    @GetMapping("/summary")
    @RequireWorkspaceMember
    public Result<TaskStatsSummary> getWorkspaceSummary() {
        String workspaceId = UserContextHolder.getWorkspaceId();
        return Result.success(statisticsService.getWorkspaceSummary(workspaceId));
    }

    /**
     * 获取我的任务统计
     */
    @Operation(summary = "获取我的任务统计")
    @GetMapping("/my-summary")
    @RequireWorkspaceMember
    public Result<TaskStatsSummary> getMySummary() {
        String userId = UserContextHolder.getUserId();
        return Result.success(statisticsService.getUserSummary(userId));
    }

    /**
     * 获取每日统计
     */
    @Operation(summary = "获取每日统计")
    @GetMapping("/daily")
    @RequireWorkspaceMember
    public Result<DailyStats> getDailyStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        LocalDate targetDate = date != null ? date : LocalDate.now();
        return Result.success(statisticsService.getDailyStats(workspaceId, targetDate));
    }

    /**
     * 获取统计趋势
     */
    @Operation(summary = "获取统计趋势")
    @GetMapping("/trend")
    @RequireWorkspaceMember
    public Result<List<DailyStats>> getStatsTrend(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        return Result.success(statisticsService.getStatsTrend(workspaceId, startDate, endDate));
    }

    /**
     * 获取最近7天趋势
     */
    @Operation(summary = "获取最近7天趋势")
    @GetMapping("/trend/week")
    @RequireWorkspaceMember
    public Result<List<DailyStats>> getWeekTrend() {
        String workspaceId = UserContextHolder.getWorkspaceId();
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(6);
        return Result.success(statisticsService.getStatsTrend(workspaceId, startDate, endDate));
    }

    /**
     * 获取最近30天趋势
     */
    @Operation(summary = "获取最近30天趋势")
    @GetMapping("/trend/month")
    @RequireWorkspaceMember
    public Result<List<DailyStats>> getMonthTrend() {
        String workspaceId = UserContextHolder.getWorkspaceId();
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(29);
        return Result.success(statisticsService.getStatsTrend(workspaceId, startDate, endDate));
    }

    /**
     * 获取提供商使用统计
     */
    @Operation(summary = "获取提供商使用统计")
    @GetMapping("/providers")
    @RequireWorkspaceMember
    public Result<List<ProviderUsageStats>> getProviderUsageStats() {
        String workspaceId = UserContextHolder.getWorkspaceId();
        return Result.success(statisticsService.getProviderUsageStats(workspaceId));
    }

    /**
     * 获取任务类型分布
     */
    @Operation(summary = "获取任务类型分布")
    @GetMapping("/type-distribution")
    @RequireWorkspaceMember
    public Result<Map<String, Long>> getTypeDistribution() {
        String workspaceId = UserContextHolder.getWorkspaceId();
        return Result.success(statisticsService.getTaskTypeDistribution(workspaceId));
    }

    /**
     * 获取队列状态
     */
    @Operation(summary = "获取队列状态")
    @GetMapping("/queue")
    @RequireWorkspaceMember
    public Result<QueueStatus> getQueueStatus() {
        return Result.success(statisticsService.getQueueStatus());
    }
}
