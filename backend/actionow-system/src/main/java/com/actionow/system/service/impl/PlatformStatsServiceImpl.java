package com.actionow.system.service.impl;

import com.actionow.system.constant.SystemConstants;
import com.actionow.system.dto.PlatformOverviewResponse;
import com.actionow.system.dto.PlatformStatsResponse;
import com.actionow.system.entity.PlatformStats;
import com.actionow.system.mapper.PlatformStatsMapper;
import com.actionow.system.service.PlatformStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 平台统计服务实现
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformStatsServiceImpl implements PlatformStatsService {

    private final PlatformStatsMapper statsMapper;
    private final StringRedisTemplate redisTemplate;

    @Override
    public PlatformOverviewResponse getOverview() {
        PlatformOverviewResponse response = new PlatformOverviewResponse();

        // 从 Redis 获取实时计数或从数据库获取
        response.setTotalUsers(getCountFromCache(SystemConstants.StatsMetric.USER_COUNT));
        response.setTotalWorkspaces(getCountFromCache(SystemConstants.StatsMetric.WORKSPACE_COUNT));
        response.setTotalScripts(getCountFromCache(SystemConstants.StatsMetric.SCRIPT_COUNT));
        response.setTotalTasks(getCountFromCache(SystemConstants.StatsMetric.TASK_COUNT));
        response.setTotalGenerations(getCountFromCache(SystemConstants.StatsMetric.AI_GENERATION_COUNT));
        response.setTotalCreditsConsumed(getCountFromCache(SystemConstants.StatsMetric.CREDIT_CONSUMED));
        response.setTotalStorageUsed(getCountFromCache(SystemConstants.StatsMetric.STORAGE_USED));

        // 今日数据
        LocalDate today = LocalDate.now();
        response.setTodayNewUsers(getTodayCount(SystemConstants.StatsMetric.USER_COUNT));
        response.setTodayGenerations(getTodayCount(SystemConstants.StatsMetric.AI_GENERATION_COUNT));
        response.setTodayCreditsConsumed(getTodayCount(SystemConstants.StatsMetric.CREDIT_CONSUMED));

        return response;
    }

    @Override
    public PlatformOverviewResponse getWorkspaceOverview(String workspaceId) {
        PlatformOverviewResponse response = new PlatformOverviewResponse();

        // 工作空间维度的统计需要从数据库聚合
        LocalDate today = LocalDate.now();
        LocalDate startOfMonth = today.withDayOfMonth(1);

        List<PlatformStats> monthlyStats = statsMapper.selectByDateRange(
                startOfMonth, today, SystemConstants.StatsPeriod.DAILY, null, workspaceId);

        long totalScripts = 0;
        long totalGenerations = 0;
        long totalCredits = 0;

        for (PlatformStats stats : monthlyStats) {
            if (SystemConstants.StatsMetric.SCRIPT_COUNT.equals(stats.getMetricType())) {
                totalScripts += stats.getMetricValue();
            } else if (SystemConstants.StatsMetric.AI_GENERATION_COUNT.equals(stats.getMetricType())) {
                totalGenerations += stats.getMetricValue();
            } else if (SystemConstants.StatsMetric.CREDIT_CONSUMED.equals(stats.getMetricType())) {
                totalCredits += stats.getMetricValue();
            }
        }

        response.setTotalScripts(totalScripts);
        response.setTotalGenerations(totalGenerations);
        response.setTotalCreditsConsumed(totalCredits);

        return response;
    }

    @Override
    public List<PlatformStatsResponse> getStatsByDateRange(LocalDate startDate, LocalDate endDate,
                                                            String period, String metricType,
                                                            String workspaceId) {
        return statsMapper.selectByDateRange(startDate, endDate, period, metricType, workspaceId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<PlatformStatsResponse> getRecentStats(int days, String metricType) {
        LocalDate startDate = LocalDate.now().minusDays(days);
        return statsMapper.selectRecentDays(startDate, metricType)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void recordStats(String metricType, Long value, String workspaceId) {
        LocalDate today = LocalDate.now();

        PlatformStats existing = statsMapper.selectByDateAndMetric(today,
                SystemConstants.StatsPeriod.DAILY, metricType, workspaceId);

        if (existing != null) {
            existing.setMetricValue(existing.getMetricValue() + value);
            statsMapper.updateById(existing);
        } else {
            PlatformStats stats = new PlatformStats();
            stats.setStatsDate(today);
            stats.setPeriod(SystemConstants.StatsPeriod.DAILY);
            stats.setMetricType(metricType);
            stats.setWorkspaceId(workspaceId);
            stats.setMetricValue(value);
            statsMapper.insert(stats);
        }

        // 更新 Redis 计数
        String cacheKey = SystemConstants.CacheKey.STATS_PREFIX + metricType;
        redisTemplate.opsForValue().increment(cacheKey, value);
    }

    @Override
    @Scheduled(cron = "0 5 0 * * ?")  // 每天凌晨 00:05 执行
    public void runDailyAggregation() {
        log.info("开始执行每日统计汇总...");
        // 这里可以添加更多统计聚合逻辑
        log.info("每日统计汇总完成");
    }

    private Long getCountFromCache(String metricType) {
        String cacheKey = SystemConstants.CacheKey.STATS_PREFIX + metricType;
        String value = redisTemplate.opsForValue().get(cacheKey);
        return value != null ? Long.parseLong(value) : 0L;
    }

    private Long getTodayCount(String metricType) {
        String cacheKey = SystemConstants.CacheKey.STATS_PREFIX + "today:" + metricType;
        String value = redisTemplate.opsForValue().get(cacheKey);
        return value != null ? Long.parseLong(value) : 0L;
    }

    private PlatformStatsResponse toResponse(PlatformStats stats) {
        PlatformStatsResponse response = new PlatformStatsResponse();
        BeanUtils.copyProperties(stats, response);
        return response;
    }
}
