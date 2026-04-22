package com.actionow.agent.entity;

import com.actionow.common.data.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * HITL ask/answer 审计记录。每个 {@code ask_user_*} 工具调用在 awaitAnswer
 * 开始时落一行 PENDING，在 submit / timeout / cancel / reject 时更新终态。
 * 便于会话重载、回放以及事后审计用户决策链路。
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_agent_ask_history", autoResultMap = true)
public class AgentAskHistory extends BaseEntity {

    /** 所属会话 */
    private String sessionId;

    /** 业务侧 ask id（UserInteractionService.newAskId），非主键 */
    private String askId;

    /** 面向用户展示的问题 */
    private String question;

    /** single_choice / multi_choice / confirm / text / number */
    private String inputType;

    /** 候选选项（single_choice / multi_choice / confirm），JSON 数组 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> choices;

    /** minSelect / maxSelect / min / max / minLength / maxLength 等约束 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> constraints;

    /** 用户答案载荷（{@link com.actionow.agent.interaction.UserAnswer} 序列化） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> answer;

    /** 终态：PENDING / ANSWERED / REJECTED / TIMEOUT / CANCELLED / ERROR */
    private String status;

    /** 终态到达时间（respondedAt <= createdAt + timeout） */
    private LocalDateTime respondedAt;

    /** 超时秒数（方便分析 SLA） */
    private Integer timeoutSec;
}
