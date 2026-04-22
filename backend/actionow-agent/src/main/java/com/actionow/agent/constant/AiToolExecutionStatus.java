package com.actionow.agent.constant;

import lombok.Getter;

/**
 * AI 工具执行状态枚举
 *
 * 执行生命周期：
 * - PENDING: 任务已提交，等待处理
 * - RUNNING: 任务正在执行中
 * - COMPLETED: 任务执行成功
 * - FAILED: 任务执行失败
 *
 * @author Actionow
 */
@Getter
public enum AiToolExecutionStatus {

    /**
     * 等待处理
     * - 任务已提交到队列
     * - 等待资源分配
     */
    PENDING("PENDING", "等待处理"),

    /**
     * 执行中
     * - 任务正在处理
     * - 可能是长时间运行的任务（如视频生成）
     */
    RUNNING("RUNNING", "执行中"),

    /**
     * 执行成功
     * - 任务已完成
     * - 结果可用
     */
    COMPLETED("COMPLETED", "执行成功"),

    /**
     * 执行失败
     * - 任务执行出错
     * - 查看 errorMessage 获取详情
     */
    FAILED("FAILED", "执行失败");

    private final String code;
    private final String name;

    AiToolExecutionStatus(String code, String name) {
        this.code = code;
        this.name = name;
    }

    /**
     * 根据 code 获取枚举
     */
    public static AiToolExecutionStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (AiToolExecutionStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 判断是否为成功状态
     */
    public boolean isCompleted() {
        return this == COMPLETED;
    }

    /**
     * 判断是否为进行中状态（需要轮询）
     */
    public boolean isInProgress() {
        return this == PENDING || this == RUNNING;
    }

    /**
     * 判断是否为最终状态
     */
    public boolean isFinal() {
        return this == COMPLETED || this == FAILED;
    }

    /**
     * 判断是否为失败状态
     */
    public boolean isFailed() {
        return this == FAILED;
    }
}
