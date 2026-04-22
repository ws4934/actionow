package com.actionow.agent.mission;

/**
 * Mission 决策校验异常
 * 当一次 Agent 响应中检测到多种冲突控制决策时抛出
 *
 * @author Actionow
 */
public class MissionDecisionException extends RuntimeException {

    public MissionDecisionException(String message) {
        super(message);
    }
}
