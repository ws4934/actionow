package com.actionow.agent.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Mission 进度响应（轻量级）
 *
 * @author Actionow
 */
@Data
@Builder
public class MissionProgressResponse {

    private String id;
    private String title;
    private String status;
    private Integer progress;
    private Integer currentStep;
    private Integer totalSteps;
    private String currentActivity;

    /**
     * 委派任务统计
     */
    private PendingTaskStats pendingTasks;

    /**
     * 步骤摘要列表
     */
    private List<StepSummary> steps;

    private Long totalCreditCost;
    private LocalDateTime startedAt;

    @Data
    @Builder
    public static class PendingTaskStats {
        private int total;
        private int completed;
        private int failed;
        private int running;
    }

    @Data
    @Builder
    public static class StepSummary {
        private int number;
        private String type;
        private String status;
        private String summary;
    }
}
