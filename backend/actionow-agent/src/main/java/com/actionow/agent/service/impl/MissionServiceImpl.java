package com.actionow.agent.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.agent.config.AgentRuntimeConfigService;
import com.actionow.agent.constant.MissionStatus;
import com.actionow.agent.constant.MissionStepStatus;
import com.actionow.agent.dto.request.CreateMissionRequest;
import com.actionow.agent.dto.response.MissionProgressResponse;
import com.actionow.agent.dto.response.MissionResponse;
import com.actionow.agent.dto.response.MissionStepResponse;
import com.actionow.agent.dto.response.MissionTaskResponse;
import com.actionow.agent.dto.response.MissionEventResponse;
import com.actionow.agent.dto.response.MissionTraceResponse;
import com.actionow.agent.entity.AgentMission;
import com.actionow.agent.entity.AgentMissionStep;
import com.actionow.agent.feign.TaskFeignClient;
import com.actionow.agent.registry.DatabaseSkillRegistry;
import com.actionow.agent.core.execution.ExecutionRegistry;
import com.actionow.agent.mapper.AgentMissionMapper;
import com.actionow.agent.mapper.AgentMissionStepMapper;
import com.actionow.agent.service.MissionExecutionRecordService;
import com.actionow.agent.service.MissionService;
import com.actionow.agent.service.MissionSseService;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.id.UuidGenerator;
import com.actionow.common.core.result.PageResult;
import com.actionow.common.mq.constant.MqConstants;
import com.actionow.common.mq.message.MessageWrapper;
import com.actionow.common.mq.producer.MessageProducer;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Mission 服务实现
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MissionServiceImpl implements MissionService {

    private final AgentMissionMapper missionMapper;
    private final AgentMissionStepMapper stepMapper;
    private final MessageProducer messageProducer;
    private final TaskFeignClient taskFeignClient;
    private final ExecutionRegistry executionRegistry;
    private final MissionSseService missionSseService;
    private final MissionExecutionRecordService missionExecutionRecordService;
    private final AgentRuntimeConfigService runtimeConfig;
    private final DatabaseSkillRegistry skillRegistry;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MissionResponse create(CreateMissionRequest request, String workspaceId, String userId) {
        // 工作空间级并发控制
        int activeMissions = missionMapper.countActiveByWorkspace(workspaceId);
        int maxConcurrent = runtimeConfig.getMissionMaxConcurrentPerWorkspace();
        if (activeMissions >= maxConcurrent) {
            throw new BusinessException("工作空间并发 Mission 数已达上限 (" + maxConcurrent
                    + ")，当前活跃: " + activeMissions + "，请等待现有任务完成后再创建");
        }

        AgentMission mission = new AgentMission();
        mission.setId(UuidGenerator.generateUuidV7());
        mission.setWorkspaceId(workspaceId);
        mission.setCreatorId(userId);
        mission.setTitle(request.getTitle());
        mission.setGoal(request.getGoal());
        mission.setPlan(request.getPlan());
        mission.setRuntimeSessionId(normalizeSessionId(request.getRuntimeSessionId()));
        mission.setTenantSchema(request.getTenantSchema());
        mission.setAgentType(request.getAgentType());
        mission.setAgentSkillNames(request.getSkillNames());
        mission.setSkillVersions(skillRegistry.getSkillVersions(request.getSkillNames(), workspaceId));
        mission.setStatus(MissionStatus.CREATED.getCode());
        mission.setCurrentStep(0);
        mission.setProgress(0);
        mission.setTotalSteps(0);
        mission.setTotalCreditCost(0L);

        missionMapper.insert(mission);

        String missionId = mission.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publishMissionStepEvent(missionId);
            }
        });

        log.info("Mission 创建成功: missionId={}, title={}, workspaceId={}", mission.getId(), mission.getTitle(), workspaceId);
        missionExecutionRecordService.recordEvent(
                mission.getId(),
                "MISSION_CREATED",
                "Mission 已创建",
                Map.of("title", mission.getTitle(), "goal", mission.getGoal())
        );

        return MissionResponse.fromEntity(mission);
    }

    @Override
    public MissionResponse getById(String missionId) {
        AgentMission mission = getMissionOrThrow(missionId);
        return MissionResponse.fromEntity(mission);
    }

    @Override
    public AgentMission getEntityById(String missionId) {
        return getMissionOrThrow(missionId);
    }

    @Override
    public MissionProgressResponse getProgress(String missionId) {
        AgentMission mission = getMissionOrThrow(missionId);
        List<AgentMissionStep> steps = stepMapper.selectByMissionId(missionId);

        MissionExecutionRecordService.MissionTaskStats taskStats = missionExecutionRecordService.summarize(missionId);
        int pendingCount = (int) taskStats.pending();
        int completedCount = (int) taskStats.completed();
        int failedCount = (int) taskStats.failed();

        // 从最新步骤提取当前活动描述
        String currentActivity = null;
        if (!steps.isEmpty()) {
            AgentMissionStep latestStep = steps.get(steps.size() - 1);
            currentActivity = latestStep.getOutputSummary();
        }

        List<MissionProgressResponse.StepSummary> stepSummaries = steps.stream()
                .map(step -> MissionProgressResponse.StepSummary.builder()
                        .number(step.getStepNumber())
                        .type(step.getStepType())
                        .status(step.getStatus())
                        .summary(step.getOutputSummary())
                        .build())
                .collect(Collectors.toList());

        return MissionProgressResponse.builder()
                .id(mission.getId())
                .title(mission.getTitle())
                .status(mission.getStatus())
                .progress(mission.getProgress())
                .currentStep(mission.getCurrentStep())
                .totalSteps(mission.getTotalSteps())
                .currentActivity(currentActivity)
                .pendingTasks(MissionProgressResponse.PendingTaskStats.builder()
                        .total(pendingCount + completedCount + failedCount)
                        .completed(completedCount)
                        .failed(failedCount)
                        .running(pendingCount)
                        .build())
                .steps(stepSummaries)
                .totalCreditCost(mission.getTotalCreditCost())
                .startedAt(mission.getStartedAt())
                .build();
    }

    @Override
    public List<MissionStepResponse> getSteps(String missionId) {
        getMissionOrThrow(missionId);
        List<AgentMissionStep> steps = stepMapper.selectByMissionId(missionId);
        return steps.stream()
                .map(MissionStepResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<MissionTaskResponse> getTasks(String missionId) {
        getMissionOrThrow(missionId);
        return missionExecutionRecordService.listTasks(missionId).stream()
                .map(MissionTaskResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<MissionEventResponse> getEvents(String missionId) {
        getMissionOrThrow(missionId);
        return missionExecutionRecordService.listEvents(missionId).stream()
                .map(MissionEventResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<MissionTraceResponse> getTraces(String missionId) {
        getMissionOrThrow(missionId);
        return missionExecutionRecordService.listTraces(missionId).stream()
                .map(MissionTraceResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public PageResult<MissionResponse> listByWorkspace(String workspaceId, Long current, Long size, String status) {
        if (current == null || current < 1) current = 1L;
        if (size == null || size < 1) size = 20L;
        if (size > 100) size = 100L;

        Page<AgentMission> page = new Page<>(current, size);
        IPage<AgentMission> missionPage = missionMapper.selectPageByWorkspace(page, workspaceId, status);

        if (missionPage.getRecords().isEmpty()) {
            return PageResult.empty(current, size);
        }

        List<MissionResponse> records = missionPage.getRecords().stream()
                .map(MissionResponse::fromEntity)
                .collect(Collectors.toList());

        return PageResult.of(missionPage.getCurrent(), missionPage.getSize(), missionPage.getTotal(), records);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(String missionId, String userId) {
        AgentMission mission = getMissionOrThrow(missionId);

        if (!MissionStatus.fromCode(mission.getStatus()).isCancellable()) {
            throw new BusinessException("Mission 已处于终态，无法取消");
        }

        // 1. 先设终态，阻止 MissionExecutor 拾取新步骤
        mission.setStatus(MissionStatus.CANCELLED.getCode());
        mission.setCompletedAt(LocalDateTime.now());
        missionMapper.updateById(mission);

        // 2. 取消运行中的 Agent 会话
        if (mission.getRuntimeSessionId() != null) {
            executionRegistry.requestCancellation(mission.getRuntimeSessionId());
        }

        // 3. 将所有 RUNNING/PENDING 的 MissionStep 标记为 FAILED
        List<AgentMissionStep> activeSteps = stepMapper.selectList(new LambdaQueryWrapper<AgentMissionStep>()
                .eq(AgentMissionStep::getMissionId, missionId)
                .in(AgentMissionStep::getStatus, MissionStepStatus.RUNNING.getCode(), MissionStepStatus.PENDING.getCode()));
        for (AgentMissionStep step : activeSteps) {
            step.setStatus(MissionStepStatus.FAILED.getCode());
            step.setOutputSummary("Mission 已取消");
            step.setCompletedAt(LocalDateTime.now());
            stepMapper.updateById(step);
        }

        // 4. 取消所有 pending/running 委派任务（best-effort）
        long cancelledTasks = 0;
        for (var task : missionExecutionRecordService.listTasks(missionId)) {
            if (!"PENDING".equalsIgnoreCase(task.getStatus()) && !"RUNNING".equalsIgnoreCase(task.getStatus())) {
                continue;
            }
            try {
                if (task.getBatchJobId() != null && !task.getBatchJobId().isBlank()) {
                    taskFeignClient.cancelBatchJob(task.getBatchJobId(), userId);
                } else if (task.getExternalTaskId() != null && !task.getExternalTaskId().isBlank()) {
                    taskFeignClient.cancelTask(task.getExternalTaskId(), userId);
                }
                cancelledTasks++;
            } catch (Exception e) {
                log.warn("取消委派任务失败（best-effort）: taskId={}, batchJobId={}, error={}",
                        task.getExternalTaskId(), task.getBatchJobId(), e.getMessage());
            }
        }

        missionExecutionRecordService.recordEvent(
                missionId,
                "MISSION_CANCELLED",
                "Mission 已取消",
                Map.of("cancelledTasks", cancelledTasks, "cancelledSteps", activeSteps.size())
        );

        log.info("Mission 取消成功（级联）: missionId={}, cancelledTasks={}, cancelledSteps={}",
                missionId,
                cancelledTasks,
                activeSteps.size());

        // SSE 推送取消事件
        missionSseService.pushMissionCancelled(missionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(String missionId, String status) {
        AgentMission mission = getMissionOrThrow(missionId);
        mission.setStatus(status);
        if (MissionStatus.fromCode(status).isTerminal()) {
            mission.setCompletedAt(LocalDateTime.now());
        }
        missionMapper.updateById(mission);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePlan(String missionId, Map<String, Object> plan) {
        AgentMission mission = getMissionOrThrow(missionId);
        mission.setPlan(plan);
        missionMapper.updateById(mission);
    }

    @Override
    public void updateProgress(String missionId, int progress) {
        missionMapper.updateProgress(missionId, Math.min(100, Math.max(0, progress)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(AgentMission mission) {
        missionMapper.updateById(mission);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentMissionStep createStep(String missionId, int stepNumber, String stepType) {
        AgentMissionStep step = new AgentMissionStep();
        step.setId(UuidGenerator.generateUuidV7());
        step.setMissionId(missionId);
        step.setStepNumber(stepNumber);
        step.setStepType(stepType);
        step.setStatus(MissionStepStatus.PENDING.getCode());
        step.setCreditCost(0L);
        step.setCreatedAt(LocalDateTime.now());

        stepMapper.insert(step);

        log.debug("Mission Step 创建: missionId={}, stepNumber={}, type={}", missionId, stepNumber, stepType);

        return step;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStep(AgentMissionStep step) {
        stepMapper.updateById(step);
    }

    // ==================== 内部方法 ====================

    private AgentMission getMissionOrThrow(String missionId) {
        AgentMission mission = missionMapper.selectById(missionId);
        if (mission == null) {
            throw new BusinessException("Mission 不存在: " + missionId);
        }
        return mission;
    }

    private String normalizeSessionId(String sessionId) {
        if (sessionId == null) {
            return null;
        }

        String normalized = sessionId.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        if ("none".equalsIgnoreCase(normalized)
                || "null".equalsIgnoreCase(normalized)
                || "undefined".equalsIgnoreCase(normalized)) {
            return null;
        }

        return normalized;
    }

    /**
     * 发布 Mission Step 执行事件到 MQ
     */
    @Override
    public void publishMissionStepEvent(String missionId) {
        try {
            Map<String, Object> payload = Map.of("missionId", missionId);
            MessageWrapper<Map<String, Object>> message = MessageWrapper.wrap(
                    MqConstants.Mission.MSG_STEP_EXECUTE,
                    payload
            );
            messageProducer.send(MqConstants.EXCHANGE_DIRECT, MqConstants.Mission.ROUTING_STEP_EXECUTE, message);
        } catch (Exception e) {
            log.error("发布 Mission Step 执行事件失败: missionId={}", missionId, e);
        }
    }
}
