package com.actionow.agent.service;

import com.actionow.agent.dto.request.CreateMissionRequest;
import com.actionow.agent.dto.response.MissionProgressResponse;
import com.actionow.agent.dto.response.MissionResponse;
import com.actionow.agent.dto.response.MissionStepResponse;
import com.actionow.agent.dto.response.MissionTaskResponse;
import com.actionow.agent.dto.response.MissionEventResponse;
import com.actionow.agent.dto.response.MissionTraceResponse;
import com.actionow.agent.entity.AgentMission;
import com.actionow.agent.entity.AgentMissionStep;
import com.actionow.common.core.result.PageResult;

import java.util.List;
import java.util.Map;

/**
 * Mission 服务接口
 *
 * @author Actionow
 */
public interface MissionService {

    /**
     * 创建 Mission
     */
    MissionResponse create(CreateMissionRequest request, String workspaceId, String userId);

    /**
     * 获取 Mission 详情
     */
    MissionResponse getById(String missionId);

    /**
     * 获取 Mission 实体
     */
    AgentMission getEntityById(String missionId);

    /**
     * 获取 Mission 进度
     */
    MissionProgressResponse getProgress(String missionId);

    /**
     * 获取 Mission 步骤列表
     */
    List<MissionStepResponse> getSteps(String missionId);

    /**
     * 获取 Mission 任务列表。
     */
    List<MissionTaskResponse> getTasks(String missionId);

    /**
     * 获取 Mission 事件列表。
     */
    List<MissionEventResponse> getEvents(String missionId);

    /**
     * 获取 Mission 轨迹列表。
     */
    List<MissionTraceResponse> getTraces(String missionId);

    /**
     * 分页查询工作空间的 Mission 列表
     */
    PageResult<MissionResponse> listByWorkspace(String workspaceId, Long current, Long size, String status);

    /**
     * 取消 Mission
     */
    void cancel(String missionId, String userId);

    /**
     * 更新 Mission 状态
     */
    void updateStatus(String missionId, String status);

    /**
     * 更新 Mission 计划
     */
    void updatePlan(String missionId, Map<String, Object> plan);

    /**
     * 更新 Mission 进度
     */
    void updateProgress(String missionId, int progress);

    /**
     * 保存 Mission（更新所有字段）
     */
    void save(AgentMission mission);

    /**
     * 创建步骤记录
     */
    AgentMissionStep createStep(String missionId, int stepNumber, String stepType);

    /**
     * 更新步骤记录
     */
    void updateStep(AgentMissionStep step);

    /**
     * 发布 Mission Step 执行事件到 MQ
     */
    void publishMissionStepEvent(String missionId);
}
