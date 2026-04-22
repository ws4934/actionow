package com.actionow.task.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 任务统计服务接口
 *
 * @author Actionow
 */
public interface TaskStatisticsService {

    /**
     * 获取工作空间任务统计概览
     *
     * @param workspaceId 工作空间ID
     * @return 统计概览
     */
    TaskStatsSummary getWorkspaceSummary(String workspaceId);

    /**
     * 获取用户任务统计
     *
     * @param userId 用户ID
     * @return 统计数据
     */
    TaskStatsSummary getUserSummary(String userId);

    /**
     * 获取每日统计
     *
     * @param workspaceId 工作空间ID
     * @param date        日期
     * @return 每日统计
     */
    DailyStats getDailyStats(String workspaceId, LocalDate date);

    /**
     * 获取日期范围内的统计趋势
     *
     * @param workspaceId 工作空间ID
     * @param startDate   开始日期
     * @param endDate     结束日期
     * @return 趋势数据
     */
    List<DailyStats> getStatsTrend(String workspaceId, LocalDate startDate, LocalDate endDate);

    /**
     * 获取提供商使用统计
     *
     * @param workspaceId 工作空间ID
     * @return 提供商统计列表
     */
    List<ProviderUsageStats> getProviderUsageStats(String workspaceId);

    /**
     * 获取任务类型分布
     *
     * @param workspaceId 工作空间ID
     * @return 类型分布
     */
    Map<String, Long> getTaskTypeDistribution(String workspaceId);

    /**
     * 获取实时队列状态
     *
     * @return 队列状态
     */
    QueueStatus getQueueStatus();

    /**
     * 任务统计概览
     */
    record TaskStatsSummary(
            long totalTasks,
            long pendingTasks,
            long runningTasks,
            long completedTasks,
            long failedTasks,
            long cancelledTasks,
            double successRate,
            long avgDurationMs,
            long totalCreditsUsed
    ) {}

    /**
     * 每日统计
     */
    record DailyStats(
            LocalDate date,
            long totalTasks,
            long completedTasks,
            long failedTasks,
            double successRate,
            long avgDurationMs,
            long creditsUsed,
            long peakHour,
            long peakHourCount
    ) {}

    /**
     * 提供商使用统计
     */
    record ProviderUsageStats(
            String providerId,
            String providerName,
            String providerType,
            long totalExecutions,
            long successCount,
            long failedCount,
            double successRate,
            long avgDurationMs,
            long totalCreditsUsed
    ) {}

    /**
     * 队列状态
     */
    record QueueStatus(
            int totalQueued,
            int highPriorityQueued,
            int normalPriorityQueued,
            int lowPriorityQueued,
            int currentlyRunning,
            int maxConcurrent,
            double utilizationRate
    ) {}
}
