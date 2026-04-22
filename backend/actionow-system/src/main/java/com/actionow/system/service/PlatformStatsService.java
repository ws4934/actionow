package com.actionow.system.service;

import com.actionow.system.dto.PlatformOverviewResponse;
import com.actionow.system.dto.PlatformStatsResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * 平台统计服务接口
 *
 * @author Actionow
 */
public interface PlatformStatsService {

    /**
     * 获取平台概览
     */
    PlatformOverviewResponse getOverview();

    /**
     * 获取工作空间概览
     */
    PlatformOverviewResponse getWorkspaceOverview(String workspaceId);

    /**
     * 获取指定日期范围的统计数据
     */
    List<PlatformStatsResponse> getStatsByDateRange(LocalDate startDate, LocalDate endDate,
                                                     String period, String metricType,
                                                     String workspaceId);

    /**
     * 获取最近N天的统计数据
     */
    List<PlatformStatsResponse> getRecentStats(int days, String metricType);

    /**
     * 记录统计数据（内部调用）
     */
    void recordStats(String metricType, Long value, String workspaceId);

    /**
     * 执行每日统计汇总（定时任务）
     */
    void runDailyAggregation();
}
