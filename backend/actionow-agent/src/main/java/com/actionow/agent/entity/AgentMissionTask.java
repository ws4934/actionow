package com.actionow.agent.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.actionow.common.data.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Mission 异步任务记录。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_agent_mission_task", autoResultMap = true)
public class AgentMissionTask extends BaseEntity {

    private String missionId;

    private String missionStepId;

    private String taskKind;

    private String externalTaskId;

    private String batchJobId;

    private String entityType;

    private String entityId;

    private String status;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> requestPayload;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> resultPayload;

    private String failureCode;

    private String failureMessage;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;
}
