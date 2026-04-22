package com.actionow.task.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 任务统计 Mapper
 * 独立于 TaskMapper，仅包含统计聚合查询（@Select 原生 SQL）。
 * 不继承 BaseMapper，避免 MyBatis-Plus 对同一实体注册两个 BaseMapper。
 *
 * @author Actionow
 */
@Mapper
public interface TaskStatisticsMapper {

    /**
     * 获取工作空间任务统计
     */
    @Select("SELECT " +
            "COUNT(*) as total, " +
            "SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) as pending, " +
            "SUM(CASE WHEN status = 'RUNNING' THEN 1 ELSE 0 END) as running, " +
            "SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) as completed, " +
            "SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as failed, " +
            "SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END) as cancelled, " +
            "AVG(CASE WHEN completed_at IS NOT NULL AND started_at IS NOT NULL " +
            "    THEN EXTRACT(EPOCH FROM (completed_at - started_at)) * 1000 END) as avgDurationMs " +
            "FROM t_task WHERE workspace_id = #{workspaceId} AND deleted = 0")
    Map<String, Object> selectStatsByWorkspace(@Param("workspaceId") String workspaceId);

    /**
     * 获取用户任务统计
     */
    @Select("SELECT " +
            "COUNT(*) as total, " +
            "SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) as pending, " +
            "SUM(CASE WHEN status = 'RUNNING' THEN 1 ELSE 0 END) as running, " +
            "SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) as completed, " +
            "SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as failed, " +
            "SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END) as cancelled, " +
            "AVG(CASE WHEN completed_at IS NOT NULL AND started_at IS NOT NULL " +
            "    THEN EXTRACT(EPOCH FROM (completed_at - started_at)) * 1000 END) as avgDurationMs " +
            "FROM t_task WHERE creator_id = #{creatorId} AND deleted = 0")
    Map<String, Object> selectStatsByCreator(@Param("creatorId") String creatorId);

    /**
     * 获取每日统计
     */
    @Select("SELECT " +
            "COUNT(*) as total, " +
            "SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) as completed, " +
            "SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as failed, " +
            "AVG(CASE WHEN completed_at IS NOT NULL AND started_at IS NOT NULL " +
            "    THEN EXTRACT(EPOCH FROM (completed_at - started_at)) * 1000 END) as avgDurationMs " +
            "FROM t_task WHERE workspace_id = #{workspaceId} " +
            "AND DATE(created_at) = #{date} AND deleted = 0")
    Map<String, Object> selectDailyStats(@Param("workspaceId") String workspaceId,
                                          @Param("date") LocalDate date);

    /**
     * 获取日期范围内的统计趋势
     */
    @Select("SELECT " +
            "DATE(created_at) as date, " +
            "COUNT(*) as total, " +
            "SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) as completed, " +
            "SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as failed, " +
            "AVG(CASE WHEN completed_at IS NOT NULL AND started_at IS NOT NULL " +
            "    THEN EXTRACT(EPOCH FROM (completed_at - started_at)) * 1000 END) as avgDurationMs " +
            "FROM t_task WHERE workspace_id = #{workspaceId} " +
            "AND DATE(created_at) BETWEEN #{startDate} AND #{endDate} AND deleted = 0 " +
            "GROUP BY DATE(created_at) ORDER BY DATE(created_at)")
    List<Map<String, Object>> selectStatsTrend(@Param("workspaceId") String workspaceId,
                                                @Param("startDate") LocalDate startDate,
                                                @Param("endDate") LocalDate endDate);

    /**
     * 获取提供商使用统计
     */
    @Select("SELECT " +
            "input_params->>'providerId' as providerId, " +
            "COUNT(*) as total, " +
            "SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) as success, " +
            "SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as failed, " +
            "AVG(CASE WHEN completed_at IS NOT NULL AND started_at IS NOT NULL " +
            "    THEN EXTRACT(EPOCH FROM (completed_at - started_at)) * 1000 END) as avgDurationMs " +
            "FROM t_task WHERE workspace_id = #{workspaceId} " +
            "AND input_params->>'providerId' IS NOT NULL AND deleted = 0 " +
            "GROUP BY input_params->>'providerId'")
    List<Map<String, Object>> selectProviderUsageStats(@Param("workspaceId") String workspaceId);

    /**
     * 获取任务类型分布
     */
    @Select("SELECT task_type as type, COUNT(*) as count FROM t_task " +
            "WHERE workspace_id = #{workspaceId} AND deleted = 0 " +
            "GROUP BY task_type ORDER BY count DESC")
    List<Map<String, Object>> selectTaskTypeDistribution(@Param("workspaceId") String workspaceId);
}
