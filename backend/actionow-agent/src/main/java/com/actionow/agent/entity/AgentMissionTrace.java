package com.actionow.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Mission 内部执行轨迹。
 */
@Data
@TableName(value = "t_agent_mission_trace", autoResultMap = true)
public class AgentMissionTrace implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String missionId;

    private String missionStepId;

    private String traceType;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> payload;

    private LocalDateTime createdAt;
}
