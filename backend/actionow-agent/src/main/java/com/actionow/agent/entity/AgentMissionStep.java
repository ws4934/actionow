package com.actionow.agent.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Agent Mission Step 实体
 * 记录 Mission 执行的每一步
 *
 * 注意：MissionStep 不使用 BaseEntity（无软删除、无 createdBy 等），
 * 生命周期完全跟随 Mission（ON DELETE CASCADE）。
 *
 * @author Actionow
 */
@Data
@TableName(value = "t_agent_mission_step", autoResultMap = true)
public class AgentMissionStep implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 所属 Mission ID
     */
    private String missionId;

    /**
     * 步骤编号
     */
    private Integer stepNumber;

    /**
     * 步骤类型: AGENT_INVOKE / WAIT_TASKS
     */
    private String stepType;

    /**
     * 发给 Agent 的上下文摘要
     */
    private String inputSummary;

    /**
     * Agent 的响应摘要
     */
    private String outputSummary;

    /**
     * Agent 在此步调用的工具
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> toolCalls;

    /**
     * 步骤状态: PENDING / RUNNING / COMPLETED / FAILED
     */
    private String status;

    /**
     * 执行耗时（毫秒）
     */
    private Long durationMs;

    /**
     * 积分消耗
     */
    private Long creditCost;

    /**
     * 输入 token 数。
     */
    private Long inputTokens;

    /**
     * 输出 token 数。
     */
    private Long outputTokens;

    /**
     * 实际使用模型。
     */
    private String modelName;

    /**
     * 步骤产出的结构化数据，供后续步骤消费。
     * 例如：生成的角色 ID 列表、中间文件路径、批次统计结果等。
     * 与 outputSummary（文本摘要，会截断）不同，artifacts 保留完整的结构化信息。
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> artifacts;

    /**
     * 结构化决策类型：CONTINUE / COMPLETE / FAIL / WAIT
     */
    private String decisionType;

    /**
     * 结构化决策负载。
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> decisionPayload;

    /**
     * 开始时间
     */
    private LocalDateTime startedAt;

    /**
     * 完成时间
     */
    private LocalDateTime completedAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
