package com.actionow.agent.service;

import com.actionow.agent.entity.AgentMission;

import java.util.Map;

/**
 * 将 Mission 终态投影回来源聊天会话。
 */
public interface MissionEventProjector {

    void projectTerminalState(AgentMission mission, String status, String summary, Map<String, Object> payload);
}
