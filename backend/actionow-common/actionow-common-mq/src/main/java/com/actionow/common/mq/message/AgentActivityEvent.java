package com.actionow.common.mq.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Agent 活动事件 (用于协作服务)
 * 从 actionow-agent 发送到 actionow-collab
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentActivityEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 活动类型
     */
    private String activityType;

    /**
     * Agent 会话ID
     */
    private String sessionId;

    /**
     * 工作空间ID
     */
    private String workspaceId;

    /**
     * 剧本ID (作用域)
     */
    private String scriptId;

    /**
     * 触发用户ID
     */
    private String userId;

    /**
     * Agent 类型 (如: scriptwriting)
     */
    private String agentType;

    /**
     * 工具名称 (仅 TOOL_CALL, TOOL_RESULT 时有值)
     */
    private String toolName;

    /**
     * 工具调用ID
     */
    private String toolCallId;

    /**
     * 工具参数 (仅 TOOL_CALL 时有值)
     */
    private Map<String, Object> toolArguments;

    /**
     * 工具结果 (仅 TOOL_RESULT 时有值)
     */
    private Map<String, Object> toolResult;

    /**
     * 目标实体类型 (如: SCRIPT, CHARACTER)
     */
    private String targetEntityType;

    /**
     * 目标实体ID
     */
    private String targetEntityId;

    /**
     * 事件时间
     */
    private LocalDateTime timestamp;

    /**
     * 额外数据
     */
    private Map<String, Object> extras;

    /**
     * 活动类型常量
     */
    public static final class ActivityType {
        /**
         * Agent 开始执行
         */
        public static final String AGENT_STARTED = "AGENT_STARTED";

        /**
         * Agent 执行完成
         */
        public static final String AGENT_COMPLETED = "AGENT_COMPLETED";

        /**
         * Agent 执行失败
         */
        public static final String AGENT_FAILED = "AGENT_FAILED";

        /**
         * 工具调用开始
         */
        public static final String TOOL_CALL = "TOOL_CALL";

        /**
         * 工具调用完成
         */
        public static final String TOOL_RESULT = "TOOL_RESULT";

        /**
         * Agent 思考中
         */
        public static final String THINKING = "THINKING";

        private ActivityType() {}
    }
}
