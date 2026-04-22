package com.actionow.task.service.impl;

import com.actionow.common.core.id.UuidGenerator;
import com.actionow.task.config.TaskRuntimeConfigService;
import com.actionow.task.constant.CompensationType;
import com.actionow.task.constant.TaskConstants;
import com.actionow.task.entity.CompensationTask;
import com.actionow.task.mapper.CompensationTaskMapper;
import com.actionow.task.service.CompensationTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 补偿任务创建服务实现。
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompensationTaskServiceImpl implements CompensationTaskService {

    private final CompensationTaskMapper compensationTaskMapper;
    private final TaskRuntimeConfigService runtimeConfig;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createCompensationTask(CompensationType type, String workspaceId, String userId,
                                       String businessId, String businessType,
                                       Long amount, String remark, String errorMessage) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("businessId", businessId);
        payload.put("businessType", businessType);
        payload.put("remark", remark);
        if (amount != null) {
            payload.put("amount", amount);
        }

        CompensationTask task = new CompensationTask();
        task.setId(UuidGenerator.generateUuidV7());
        task.setType(type.getCode());
        task.setWorkspaceId(workspaceId);
        task.setStatus(TaskConstants.CompensationStatus.PENDING);
        task.setRetryCount(0);
        task.setPayload(payload);
        task.setLastError(errorMessage);
        // 首次重试延迟
        task.setNextRetryAt(LocalDateTime.now().plusSeconds(
                runtimeConfig.getCompensationInitialRetryDelaySeconds()));

        compensationTaskMapper.insert(task);
        log.info("创建补偿任务: taskId={}, type={}, businessId={}",
                task.getId(), type.getCode(), businessId);
    }
}
