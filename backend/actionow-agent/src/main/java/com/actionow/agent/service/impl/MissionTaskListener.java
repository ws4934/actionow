package com.actionow.agent.service.impl;

import com.actionow.agent.constant.MissionStatus;
import com.actionow.agent.constant.MissionStepStatus;
import com.actionow.agent.entity.AgentMission;
import com.actionow.agent.entity.AgentMissionStep;
import com.actionow.agent.mapper.AgentMissionStepMapper;
import com.actionow.agent.service.MissionExecutionRecordService;
import com.actionow.agent.service.MissionService;
import com.actionow.agent.service.MissionSseService;
import com.actionow.common.mq.constant.MqConstants;
import com.actionow.common.mq.message.MessageWrapper;
import com.actionow.common.redis.lock.DistributedLockService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Mission Task 完成监听器
 * 监听 Task 完成/失败事件，推进关联 Mission 的状态
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MissionTaskListener {

    private final MissionService missionService;
    private final AgentMissionStepMapper stepMapper;
    private final DistributedLockService distributedLockService;
    private final MissionSseService missionSseService;
    private final MissionExecutionRecordService missionExecutionRecordService;

    /**
     * 监听 Task 状态变更事件
     * Task 模块在任务完成/失败时发布此事件
     * 也处理 BatchJob 完成/失败事件（batch 级别回调）
     */
    @RabbitListener(queues = MqConstants.Mission.QUEUE_TASK_CALLBACK, containerFactory = "rabbitListenerContainerFactory")
    public void onTaskStatusChanged(MessageWrapper<Map<String, Object>> message, Channel channel,
                                     @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            Map<String, Object> payload = message.getPayload();

            // 判断是否为 BatchJob 完成回调
            if (MqConstants.BatchJob.MSG_COMPLETED.equals(message.getMessageType())) {
                handleBatchJobCompleted(payload);
                channel.basicAck(deliveryTag, false);
                return;
            }

            String taskId = (String) payload.get("taskId");
            String taskStatus = (String) payload.get("status");

            if (taskId == null || taskStatus == null) {
                log.warn("收到无效的任务状态变更消息: {}", payload);
                channel.basicAck(deliveryTag, false);
                return;
            }

            log.info("收到 Task 状态变更: taskId={}, status={}", taskId, taskStatus);

            // 查找关联的 Mission
            var trackedTask = missionExecutionRecordService.findTaskByExternalTaskId(taskId);
            AgentMission mission = trackedTask != null ? missionService.getEntityById(trackedTask.getMissionId()) : null;
            if (mission == null) {
                log.debug("任务 {} 未关联任何 Mission，跳过", taskId);
                channel.basicAck(deliveryTag, false);
                return;
            }
            final AgentMission targetMission = mission;

            // 使用分布式锁保护 JSONB 列表的读-改-写操作
            String lockKey = "mission:" + targetMission.getId();
            boolean executed = distributedLockService.executeWithLock(lockKey, () -> {
                // 锁内重新读取 Mission（防止锁外读到的数据过期）
                AgentMission locked = missionService.getEntityById(targetMission.getId());

                // CANCELLED 状态检查
                if (MissionStatus.CANCELLED.getCode().equals(locked.getStatus())) {
                    log.info("Mission 已取消，忽略回调: missionId={}, taskId={}", locked.getId(), taskId);
                    return;
                }

                // 确保 Mission 处于 WAITING 状态
                if (!MissionStatus.WAITING.getCode().equals(locked.getStatus())) {
                    log.warn("Mission {} 不在 WAITING 状态 (current={}), 跳过任务回调处理",
                            locked.getId(), locked.getStatus());
                    return;
                }

                var currentTask = missionExecutionRecordService.findTaskByExternalTaskId(taskId);
                if (currentTask == null) {
                    log.info("任务 {} 未找到新的 MissionTask 记录，跳过", taskId);
                    return;
                }
                if (!isTaskPending(currentTask.getStatus())) {
                    log.info("任务 {} 已处理完成（当前状态={}），跳过", taskId, currentTask.getStatus());
                    return;
                }

                // 根据任务状态更新 Mission
                boolean isSuccess = "COMPLETED".equalsIgnoreCase(taskStatus);
                if (isSuccess) {
                    missionExecutionRecordService.markTaskCompleted(taskId, payload);
                    log.info("任务完成，更新 Mission: missionId={}, taskId={}", locked.getId(), taskId);
                } else {
                    missionExecutionRecordService.markTaskFailed(taskId, "TASK_FAILED", taskStatus, payload);
                    log.info("任务失败，更新 Mission: missionId={}, taskId={}", locked.getId(), taskId);
                }
                missionExecutionRecordService.recordEvent(
                        locked.getId(),
                        isSuccess ? "TASK_COMPLETED" : "TASK_FAILED",
                        "任务状态更新",
                        Map.of("taskId", taskId, "status", taskStatus)
                );

                // 更新进度
                updateMissionProgress(locked);
                MissionExecutionRecordService.MissionTaskStats stats = missionExecutionRecordService.summarize(locked.getId());

                // SSE 推送任务进度
                int completed = (int) stats.completed();
                int failed = (int) stats.failed();
                int pending = (int) stats.pending();
                int total = completed + failed + pending;
                missionSseService.pushTaskProgress(locked.getId(), completed, failed, total, locked.getProgress());

                // 检查是否所有委派任务都完成
                if (pending == 0) {
                    log.info("Mission {} 所有委派任务已完成，恢复执行", locked.getId());

                    // 完成 WAIT_TASKS 步骤
                    completeWaitStep(locked);

                    // Mission 恢复为 EXECUTING 状态
                    locked.setStatus(MissionStatus.EXECUTING.getCode());
                    locked.setCurrentStep(locked.getCurrentStep() + 1);
                    missionService.save(locked);

                    // 触发下一个 Agent Step
                    missionService.publishMissionStepEvent(locked.getId());
                } else {
                    missionService.save(locked);
                }
            });

            if (!executed) {
                log.warn("获取 Mission 分布式锁失败，发送到 DLQ: missionId={}, taskId={}", mission.getId(), taskId);
                channel.basicNack(deliveryTag, false, false);
                return;
            }

            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("处理 Task 状态变更消息失败", e);
            try {
                // requeue=false: 发送到 DLQ，避免无限重试
                channel.basicNack(deliveryTag, false, false);
            } catch (Exception ackEx) {
                log.error("消息 NACK 失败", ackEx);
            }
        }
    }

    /**
     * 处理 BatchJob 完成回调
     * BatchJob 全部完成时发送一条消息，一次性更新 Mission 状态
     */
    private void handleBatchJobCompleted(Map<String, Object> payload) {
        String missionId = (String) payload.get("missionId");
        String batchJobId = (String) payload.get("batchJobId");
        String batchStatus = (String) payload.get("status");
        int completedItems = payload.get("completedItems") != null ? ((Number) payload.get("completedItems")).intValue() : 0;
        int failedItems = payload.get("failedItems") != null ? ((Number) payload.get("failedItems")).intValue() : 0;

        log.info("收到 BatchJob 完成回调: missionId={}, batchJobId={}, status={}, completed={}, failed={}",
                missionId, batchJobId, batchStatus, completedItems, failedItems);

        if (missionId == null) {
            log.warn("BatchJob 完成回调缺少 missionId: batchJobId={}", batchJobId);
            return;
        }

        String lockKey = "mission:" + missionId;
        boolean executed = distributedLockService.executeWithLock(lockKey, () -> {
            AgentMission mission = missionService.getEntityById(missionId);
            if (mission == null) {
                log.warn("Mission 不存在: missionId={}", missionId);
                return;
            }

            if (MissionStatus.CANCELLED.getCode().equals(mission.getStatus())) {
                log.info("Mission 已取消，忽略 BatchJob 回调: missionId={}", missionId);
                return;
            }

            if (!MissionStatus.WAITING.getCode().equals(mission.getStatus())) {
                log.warn("Mission {} 不在 WAITING 状态 (current={}), 跳过 BatchJob 回调",
                        missionId, mission.getStatus());
                return;
            }

            var registeredBatch = missionExecutionRecordService.findTaskByBatchJobId(batchJobId);
            if (registeredBatch != null) {
                if ("COMPLETED".equalsIgnoreCase(batchStatus)) {
                    missionExecutionRecordService.markTaskCompleted(registeredBatch.getExternalTaskId(), payload);
                } else {
                    missionExecutionRecordService.markTaskFailed(
                            registeredBatch.getExternalTaskId(),
                            "BATCH_JOB_FAILED",
                            batchStatus,
                            payload
                    );
                }
            }
            updateMissionProgress(mission);
            MissionExecutionRecordService.MissionTaskStats stats = missionExecutionRecordService.summarize(missionId);

            // SSE 推送
            missionSseService.pushTaskProgress(
                    missionId,
                    (int) stats.completed(),
                    (int) stats.failed(),
                    (int) stats.total(),
                    mission.getProgress()
            );
            missionExecutionRecordService.recordEvent(
                    missionId,
                    "BATCH_JOB_COMPLETED",
                    "BatchJob 回调完成",
                    Map.of("batchJobId", batchJobId, "status", batchStatus,
                            "completedItems", completedItems, "failedItems", failedItems)
            );

            // 检查是否所有委派任务都已完成（而非无条件恢复）
            if (stats.pending() == 0) {
                // 所有任务完成，恢复 Mission
                completeWaitStep(mission);

                mission.setStatus(MissionStatus.EXECUTING.getCode());
                mission.setCurrentStep(mission.getCurrentStep() + 1);
                missionService.save(mission);

                missionService.publishMissionStepEvent(missionId);

                log.info("BatchJob 回调处理完成，所有任务已完成，Mission 恢复执行: missionId={}", missionId);
            } else {
                // 仍有 pending tasks，仅更新进度，不恢复 Mission
                missionService.save(mission);
                log.info("BatchJob 回调处理完成，仍有 {} 个 pending 任务，Mission 继续等待: missionId={}",
                        stats.pending(), missionId);
            }
        });

        if (!executed) {
            log.warn("获取 Mission 分布式锁失败: missionId={}", missionId);
        }
    }

    /**
     * 更新 Mission 进度
     */
    private void updateMissionProgress(AgentMission mission) {
        MissionExecutionRecordService.MissionTaskStats stats = missionExecutionRecordService.summarize(mission.getId());
        long totalTasks = stats.total();
        long doneTasks = stats.completed() + stats.failed();

        if (totalTasks > 0) {
            // 进度基于委派任务完成比例，但不超过 90%（留给最终完成）
            int taskProgress = (int) ((doneTasks * 80.0) / totalTasks);
            mission.setProgress(Math.min(90, taskProgress));
        }
    }

    /**
     * 完成 WAIT_TASKS 步骤
     */
    private void completeWaitStep(AgentMission mission) {
        AgentMissionStep waitStep = stepMapper.selectLatestByMissionId(mission.getId());
        if (waitStep != null && "WAIT_TASKS".equals(waitStep.getStepType())) {
            MissionExecutionRecordService.MissionTaskStats stats = missionExecutionRecordService.summarize(mission.getId());
            int completed = (int) stats.completed();
            int failed = (int) stats.failed();

            waitStep.setStatus(MissionStepStatus.COMPLETED.getCode());
            waitStep.setOutputSummary("任务完成: " + completed + " 成功, " + failed + " 失败");
            waitStep.setCompletedAt(LocalDateTime.now());
            if (waitStep.getStartedAt() != null) {
                waitStep.setDurationMs(java.time.Duration.between(waitStep.getStartedAt(), waitStep.getCompletedAt()).toMillis());
            }
            missionService.updateStep(waitStep);
        }
    }

    private boolean isTaskPending(String status) {
        return "PENDING".equalsIgnoreCase(status) || "RUNNING".equalsIgnoreCase(status);
    }
}
