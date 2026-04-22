package com.actionow.system.dto;

import lombok.Data;

import java.util.Map;

/**
 * 平台概览响应
 *
 * @author Actionow
 */
@Data
public class PlatformOverviewResponse {

    /**
     * 总用户数
     */
    private Long totalUsers;

    /**
     * 总工作空间数
     */
    private Long totalWorkspaces;

    /**
     * 总脚本数
     */
    private Long totalScripts;

    /**
     * 总任务数
     */
    private Long totalTasks;

    /**
     * 总AI生成次数
     */
    private Long totalGenerations;

    /**
     * 总消耗积分
     */
    private Long totalCreditsConsumed;

    /**
     * 总存储使用量（字节）
     */
    private Long totalStorageUsed;

    /**
     * 今日新增用户
     */
    private Long todayNewUsers;

    /**
     * 今日活跃用户
     */
    private Long todayActiveUsers;

    /**
     * 今日AI生成次数
     */
    private Long todayGenerations;

    /**
     * 今日消耗积分
     */
    private Long todayCreditsConsumed;

    /**
     * 其他统计数据
     */
    private Map<String, Object> extra;
}
