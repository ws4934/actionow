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
 * Mission 事件时间线。
 */
@Data
@TableName(value = "t_agent_mission_event", autoResultMap = true)
public class AgentMissionEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String missionId;

    private String eventType;

    private String message;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> payload;

    private LocalDateTime createdAt;
}
