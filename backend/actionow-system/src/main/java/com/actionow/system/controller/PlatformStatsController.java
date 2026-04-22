package com.actionow.system.controller;

import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireSystemTenant;
import com.actionow.system.dto.PlatformOverviewResponse;
import com.actionow.system.dto.PlatformStatsResponse;
import com.actionow.system.service.PlatformStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 平台统计控制器
 *
 * @author Actionow
 */
@RestController
@RequestMapping("/system/stats")
@RequiredArgsConstructor
@RequireSystemTenant(minRole = "ADMIN")
public class PlatformStatsController {

    private final PlatformStatsService statsService;

    /**
     * 获取平台概览
     */
    @GetMapping("/overview")
    public Result<PlatformOverviewResponse> getOverview() {
        return Result.success(statsService.getOverview());
    }

    /**
     * 获取工作空间概览
     */
    @GetMapping("/overview/workspace/{workspaceId}")
    public Result<PlatformOverviewResponse> getWorkspaceOverview(@PathVariable String workspaceId) {
        return Result.success(statsService.getWorkspaceOverview(workspaceId));
    }

    /**
     * 获取指定日期范围的统计数据
     */
    @GetMapping("/range")
    public Result<List<PlatformStatsResponse>> getStatsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "DAILY") String period,
            @RequestParam String metricType,
            @RequestParam(required = false) String workspaceId) {
        return Result.success(statsService.getStatsByDateRange(startDate, endDate, period, metricType, workspaceId));
    }

    /**
     * 获取最近N天的统计数据
     */
    @GetMapping("/recent")
    public Result<List<PlatformStatsResponse>> getRecentStats(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam String metricType) {
        return Result.success(statsService.getRecentStats(days, metricType));
    }
}
