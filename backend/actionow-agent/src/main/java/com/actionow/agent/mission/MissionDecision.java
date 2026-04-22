package com.actionow.agent.mission;

import java.util.List;

/**
 * Mission Step 控制决策
 * 由 MissionDecisionValidator 从 Agent 工具调用中提取
 *
 * @author Actionow
 */
public sealed interface MissionDecision {

    /**
     * 继续执行下一步（无控制工具调用，或仅有 update_mission_plan）
     */
    record Continue(String summary) implements MissionDecision {}

    /**
     * Agent 决定完成 Mission
     */
    record Complete(String summary) implements MissionDecision {}

    /**
     * Agent 决定终止 Mission
     */
    record Fail(String code, String reason) implements MissionDecision {}

    /**
     * Agent 委派了异步任务，Mission 进入 WAITING
     */
    record Wait(String summary, List<String> delegatedToolNames) implements MissionDecision {}
}
