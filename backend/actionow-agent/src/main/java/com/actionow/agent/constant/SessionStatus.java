package com.actionow.agent.constant;

import lombok.Getter;

/**
 * 会话状态枚举
 *
 * 会话生命周期：
 * - ACTIVE: 活跃状态，可以继续对话
 * - ARCHIVED: 归档状态，保留历史记录但不计入活跃数，可恢复
 * - DELETED: 已删除状态，软删除后 90 天物理清理
 *
 * @author Actionow
 */
@Getter
public enum SessionStatus {

    /**
     * 活跃状态
     * - 用户可以继续发送消息
     * - 计入活跃会话数统计
     */
    ACTIVE("active", "活跃"),

    /**
     * 归档状态
     * - 用户可以查看历史记录
     * - 不计入活跃会话数统计
     * - 可以恢复为活跃状态
     * - 触发条件：用户手动归档 或 超过 30 天未活跃自动归档
     */
    ARCHIVED("archived", "已归档"),

    /**
     * 已删除状态
     * - 软删除，用户不可见
     * - 90 天后物理清理
     * - 触发条件：用户手动删除
     */
    DELETED("deleted", "已删除");

    private final String code;
    private final String name;

    SessionStatus(String code, String name) {
        this.code = code;
        this.name = name;
    }

    /**
     * 根据 code 获取枚举
     */
    public static SessionStatus fromCode(String code) {
        if (code == null) {
            return ACTIVE;
        }
        for (SessionStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        return ACTIVE;
    }

    /**
     * 判断是否为活跃状态
     */
    public boolean isActive() {
        return this == ACTIVE;
    }

    /**
     * 判断是否可以恢复
     */
    public boolean canResume() {
        return this == ARCHIVED;
    }

    /**
     * 判断是否可以发送消息
     */
    public boolean canSendMessage() {
        return this == ACTIVE;
    }
}
