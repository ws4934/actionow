package com.actionow.agent.billing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.actionow.agent.constant.BillingSessionStatus;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Agent 计费会话实体
 * 记录每个会话的计费信息
 * 存储在 public schema 中，以支持跨租户的结算任务
 *
 * @author Actionow
 */
@Data
@TableName(value = "t_agent_billing_session", autoResultMap = true)
public class AgentBillingSession implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID（UUIDv7）
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 工作空间 ID
     */
    private String workspaceId;

    /**
     * 关联的会话 ID（t_agent_session.id）
     */
    private String conversationId;

    /**
     * 用户 ID
     */
    private String userId;

    /**
     * 使用的模型厂商
     */
    private String modelProvider;

    /**
     * 使用的模型 ID
     */
    private String modelId;

    /**
     * 使用的模型名称
     */
    private String modelName;

    /**
     * 钱包冻结事务 ID
     */
    private String transactionId;

    /**
     * 冻结金额（积分）
     */
    private Long frozenAmount;

    /**
     * 总输入 token 数
     */
    private Long totalInputTokens;

    /**
     * 总输出 token 数
     */
    private Long totalOutputTokens;

    /**
     * 总思考 token 数（模型内部推理）
     */
    private Long totalThoughtTokens;

    /**
     * 总缓存 token 数（复用缓存）
     */
    private Long totalCachedTokens;

    /**
     * LLM 对话消费（积分）
     */
    private Long llmCost;

    /**
     * AI 工具调用次数
     */
    private Integer aiToolCalls;

    /**
     * AI 工具消费（积分）
     */
    private Long aiToolCost;

    /**
     * 总消费 = llmCost + aiToolCost
     */
    private Long totalCost;

    /**
     * 定价快照（会话创建时记录）
     * 结构:
     * {
     *   "modelProvider": "GOOGLE",
     *   "modelId": "gemini-3-flash-preview",
     *   "inputPrice": 0.5,
     *   "outputPrice": 1.5,
     *   "snapshotAt": "2026-01-27T00:00:00Z"
     * }
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> pricingSnapshot;

    /**
     * 状态
     * ACTIVE - 活跃中
     * SETTLING - 结算中
     * SETTLED - 已结算
     * FAILED - 结算失败
     */
    private String status;

    /**
     * 结算金额
     */
    private Long settledAmount;

    /**
     * 结算时间
     */
    private LocalDateTime settledAt;

    /**
     * 结算失败原因
     */
    private String settleError;

    /**
     * 最后活动时间
     */
    private LocalDateTime lastActivityAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 获取计费会话状态枚举
     */
    public BillingSessionStatus getStatusEnum() {
        return BillingSessionStatus.fromCode(status);
    }

    /**
     * 设置计费会话状态
     */
    public void setStatusEnum(BillingSessionStatus sessionStatus) {
        this.status = sessionStatus.getCode();
    }

    /**
     * 判断是否为活跃状态
     */
    public boolean isActive() {
        return BillingSessionStatus.ACTIVE.getCode().equals(status);
    }

    /**
     * 判断是否为最终状态
     */
    public boolean isFinal() {
        return getStatusEnum().isFinal();
    }

    /**
     * 判断是否可以记录消费
     */
    public boolean canRecordUsage() {
        return getStatusEnum().canRecordUsage();
    }
}
