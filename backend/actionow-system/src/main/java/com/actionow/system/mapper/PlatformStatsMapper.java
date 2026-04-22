package com.actionow.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.system.entity.PlatformStats;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/**
 * 平台统计 Mapper
 *
 * @author Actionow
 */
@Mapper
public interface PlatformStatsMapper extends BaseMapper<PlatformStats> {

    /**
     * 查询指定日期的统计数据
     */
    @Select("SELECT * FROM platform_stats WHERE stats_date = #{statsDate} AND period = #{period} " +
            "AND metric_type = #{metricType} AND (workspace_id = #{workspaceId} OR workspace_id IS NULL)")
    PlatformStats selectByDateAndMetric(@Param("statsDate") LocalDate statsDate,
                                         @Param("period") String period,
                                         @Param("metricType") String metricType,
                                         @Param("workspaceId") String workspaceId);

    /**
     * 查询日期范围内的统计数据
     */
    @Select("SELECT * FROM platform_stats WHERE stats_date BETWEEN #{startDate} AND #{endDate} " +
            "AND period = #{period} AND metric_type = #{metricType} " +
            "AND (workspace_id = #{workspaceId} OR #{workspaceId} IS NULL) " +
            "ORDER BY stats_date")
    List<PlatformStats> selectByDateRange(@Param("startDate") LocalDate startDate,
                                           @Param("endDate") LocalDate endDate,
                                           @Param("period") String period,
                                           @Param("metricType") String metricType,
                                           @Param("workspaceId") String workspaceId);

    /**
     * 查询最近N天的统计数据
     */
    @Select("SELECT * FROM platform_stats WHERE stats_date >= #{startDate} AND period = 'DAILY' " +
            "AND metric_type = #{metricType} AND workspace_id IS NULL ORDER BY stats_date DESC")
    List<PlatformStats> selectRecentDays(@Param("startDate") LocalDate startDate,
                                          @Param("metricType") String metricType);

    /**
     * 获取平台总计数据
     */
    @Select("SELECT metric_type, SUM(metric_value) as total FROM platform_stats " +
            "WHERE period = 'DAILY' AND workspace_id IS NULL GROUP BY metric_type")
    List<Object[]> selectPlatformTotals();
}
