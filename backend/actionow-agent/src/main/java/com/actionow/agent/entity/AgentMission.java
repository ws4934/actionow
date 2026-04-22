package com.actionow.agent.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.actionow.agent.constant.MissionStatus;
import com.actionow.common.data.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Agent Mission 实体
 * 代表一个自主执行的长任务目标
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_agent_mission", autoResultMap = true)
public class AgentMission extends BaseEntity {

    /**
     * 工作空间 ID
     */
    private String workspaceId;

    /**
     * Mission 内部运行时会话 ID（仅供 Runtime/Memory 隔离使用）
     */
    @TableField("runtime_session_id")
    private String runtimeSessionId;

    /**
     * 创建时快照的 Agent 类型，执行时继承
     */
    private String agentType;

    /**
     * 创建时快照的 Skill 名称列表，执行时继承
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> agentSkillNames;

    /**
     * 创建时快照的 Skill 版本（name → updatedAt epoch millis）
     * 用于执行时检测 Skill 定义是否已变更
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Long> skillVersions;

    /**
     * 创建时快照的租户 Schema，执行时继承
     */
    private String tenantSchema;

    /**
     * 创建者用户 ID
     */
    private String creatorId;

    /**
     * Mission 标题
     */
    private String title;

    /**
     * 用户原始请求
     */
    private String goal;

    /**
     * Agent 当前计划（可变，Agent 可随时修改）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> plan;

    /**
     * Mission 状态
     * @see MissionStatus
     */
    private String status;

    /**
     * 当前步骤编号
     */
    private Integer currentStep;

    /**
     * 进度百分比 (0-100)
     */
    private Integer progress;

    /**
     * 总步骤数
     */
    private Integer totalSteps;

    /**
     * 总积分消耗
     */
    private Long totalCreditCost;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 完成摘要。
     */
    private String resultSummary;

    /**
     * 结构化结果。
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> resultPayload;

    /**
     * 结构化失败码。
     */
    private String failureCode;

    /**
     * 开始执行时间
     */
    private LocalDateTime startedAt;

    /**
     * 完成时间
     */
    private LocalDateTime completedAt;

    // ==================== 便捷方法 ====================

    /**
     * 获取状态枚举
     */
    public MissionStatus getStatusEnum() {
        return MissionStatus.fromCode(status);
    }

    /**
     * 设置状态枚举
     */
    public void setStatusEnum(MissionStatus missionStatus) {
        this.status = missionStatus.getCode();
    }

    /**
     * 是否为终态
     */
    public boolean isTerminal() {
        return getStatusEnum().isTerminal();
    }
}
