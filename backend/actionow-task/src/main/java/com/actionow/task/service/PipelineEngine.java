package com.actionow.task.service;

import com.actionow.common.core.id.UuidGenerator;
import com.actionow.common.mq.constant.MqConstants;
import com.actionow.common.mq.message.MessageWrapper;
import com.actionow.common.mq.producer.MessageProducer;
import com.actionow.task.constant.TaskConstants;
import com.actionow.task.entity.BatchJob;
import com.actionow.task.entity.BatchJobItem;
import com.actionow.task.entity.Pipeline;
import com.actionow.task.entity.PipelineStep;
import com.actionow.task.mapper.BatchJobItemMapper;
import com.actionow.task.mapper.BatchJobMapper;
import com.actionow.task.mapper.PipelineMapper;
import com.actionow.task.mapper.PipelineStepMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Pipeline 引擎
 * 负责:
 * 1. 从模板或自定义配置创建 Pipeline + Steps
 * 2. 推进步骤执行（当前步骤完成 → 解析下一步参数 → 生成新 items → 提交）
 * 3. 依赖关系解析（DAG 检查）
 * 4. Fan-out 展开（一个步骤生成 N 个 items）
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineEngine {

    private static final String IMAGE_PROMPT_PROVIDER_ID = "00000000-0000-0000-0004-000000000001";
    private static final String VIDEO_PROMPT_PROVIDER_ID = "00000000-0000-0000-0004-000000000003";
    private static final String AUDIO_PROMPT_PROVIDER_ID = "00000000-0000-0000-0004-000000000005";

    private final PipelineMapper pipelineMapper;
    private final PipelineStepMapper pipelineStepMapper;
    private final BatchJobMapper batchJobMapper;
    private final BatchJobItemMapper batchJobItemMapper;
    private final PipelineStepResolver stepResolver;
    private final BatchJobSseService sseService;
    private final MessageProducer messageProducer;

    /**
     * 从模板创建 Pipeline 及其步骤
     *
     * @param batchJob              所属 BatchJob
     * @param templateCode          模板代码
     * @param customSteps           自定义步骤（为 null 时使用模板默认步骤）
     * @param stepProviderOverrides 步骤 Provider 覆盖（key: 步骤编号从1开始, value: providerId）
     * @return 创建的 Pipeline
     */
    @Transactional(rollbackFor = Exception.class)
    public Pipeline createPipeline(BatchJob batchJob, String templateCode,
                                    List<PipelineStepConfig> customSteps,
                                    Map<Integer, String> stepProviderOverrides) {
        Pipeline pipeline = new Pipeline();
        pipeline.setBatchJobId(batchJob.getId());
        pipeline.setTemplateCode(templateCode);
        pipeline.setStatus(TaskConstants.PipelineStatus.CREATED);
        pipeline.setCurrentStep(0);
        pipeline.setCreatedAt(LocalDateTime.now());
        pipeline.setUpdatedAt(LocalDateTime.now());

        List<PipelineStepConfig> stepConfigs;
        if (customSteps != null && !customSteps.isEmpty()) {
            stepConfigs = customSteps;
            pipeline.setName("Custom Pipeline");
        } else {
            stepConfigs = getTemplateSteps(templateCode);
            pipeline.setName(getTemplateName(templateCode));
        }

        pipeline.setTotalSteps(stepConfigs.size());
        pipelineMapper.insert(pipeline);

        // 创建步骤
        for (int i = 0; i < stepConfigs.size(); i++) {
            PipelineStepConfig config = stepConfigs.get(i);
            PipelineStep step = new PipelineStep();
            step.setPipelineId(pipeline.getId());
            step.setStepNumber(i + 1);
            step.setName(config.name);
            step.setStepType(config.stepType);
            step.setGenerationType(config.generationType);

            // Provider: stepProviderOverrides 优先，其次模板默认值
            String providerId = config.providerId;
            if (stepProviderOverrides != null && stepProviderOverrides.containsKey(i + 1)) {
                providerId = stepProviderOverrides.get(i + 1);
            }
            step.setProviderId(providerId);

            step.setParamsTemplate(config.paramsTemplate);
            step.setDependsOn(config.dependsOn);
            step.setFanOutCount(config.fanOutCount != null ? config.fanOutCount : 1);
            step.setStatus(TaskConstants.PipelineStepStatus.PENDING);
            step.setCreatedAt(LocalDateTime.now());
            step.setUpdatedAt(LocalDateTime.now());
            pipelineStepMapper.insert(step);
        }

        log.info("Pipeline 已创建: id={}, batchJobId={}, template={}, steps={}",
                pipeline.getId(), batchJob.getId(), templateCode, stepConfigs.size());

        return pipeline;
    }

    /**
     * 启动 Pipeline 的第一个步骤
     * 为 step 1 创建初始 batch_job_items 并触发提交
     *
     * @param pipeline     Pipeline 实体
     * @param batchJob     所属 BatchJob
     * @param inputItems   初始输入（每个元素代表一个实体的初始参数）
     */
    @Transactional(rollbackFor = Exception.class)
    public void startPipeline(Pipeline pipeline, BatchJob batchJob,
                               List<Map<String, Object>> inputItems) {
        PipelineStep firstStep = pipelineStepMapper.selectByPipelineIdAndStepNumber(
                pipeline.getId(), 1);
        if (firstStep == null) {
            log.error("Pipeline 无步骤: pipelineId={}", pipeline.getId());
            return;
        }

        // 标记 pipeline 运行中
        pipeline.setStatus(TaskConstants.PipelineStatus.RUNNING);
        pipeline.setCurrentStep(1);
        pipeline.setUpdatedAt(LocalDateTime.now());
        pipelineMapper.updateById(pipeline);

        // 标记步骤运行中
        firstStep.setStatus(TaskConstants.PipelineStepStatus.RUNNING);
        firstStep.setStartedAt(LocalDateTime.now());
        firstStep.setUpdatedAt(LocalDateTime.now());
        pipelineStepMapper.updateById(firstStep);

        // 为第一步创建 batch items
        createItemsForStep(batchJob, firstStep, inputItems, null);

        // 更新 batch job 总数
        int totalItems = batchJobItemMapper.selectByBatchJobId(batchJob.getId()).size();
        batchJob.setTotalItems(totalItems);
        batchJobMapper.updateById(batchJob);

        // 发送 MQ 触发提交
        sendBatchStartMessage(batchJob);
    }

    /**
     * 检查并推进 Pipeline 步骤（DAG 并行执行版本）
     * 在 BatchJobTaskListener 中每次 item 完成时调用
     *
     * @param batchJobId 批量作业ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void checkAndAdvanceStep(String batchJobId) {
        Pipeline pipeline = pipelineMapper.selectByBatchJobId(batchJobId);
        if (pipeline == null) {
            return;
        }

        if (!TaskConstants.PipelineStatus.RUNNING.equals(pipeline.getStatus())) {
            return;
        }

        List<PipelineStep> allSteps = pipelineStepMapper.selectByPipelineId(pipeline.getId());

        // 第一轮: 遍历所有 RUNNING 步骤，检查是否已完成 → 标记 COMPLETED + SSE 推送
        for (PipelineStep step : allSteps) {
            if (!TaskConstants.PipelineStepStatus.RUNNING.equals(step.getStatus())) {
                continue;
            }
            if (isStepComplete(batchJobId, step.getId())) {
                log.info("Pipeline 步骤 {} 已完成: pipelineId={}", step.getStepNumber(), pipeline.getId());

                step.setStatus(TaskConstants.PipelineStepStatus.COMPLETED);
                step.setCompletedAt(LocalDateTime.now());
                step.setUpdatedAt(LocalDateTime.now());
                pipelineStepMapper.updateById(step);

                // SSE 推送步骤完成
                int stepCompleted = batchJobItemMapper.countByStatusAndStepId(
                        batchJobId, TaskConstants.BatchItemStatus.COMPLETED, step.getId());
                int stepFailed = batchJobItemMapper.countByStatusAndStepId(
                        batchJobId, TaskConstants.BatchItemStatus.FAILED, step.getId());
                sseService.sendStepCompleted(batchJobMapper.selectById(batchJobId),
                        step.getStepNumber(), step.getName(), stepCompleted, stepFailed);
            }
        }

        // 检查是否全部完成
        boolean allCompleted = allSteps.stream()
                .allMatch(s -> TaskConstants.PipelineStepStatus.COMPLETED.equals(s.getStatus()));
        if (allCompleted) {
            pipeline.setStatus(TaskConstants.PipelineStatus.COMPLETED);
            pipeline.setUpdatedAt(LocalDateTime.now());
            pipelineMapper.updateById(pipeline);
            log.info("Pipeline 全部完成: pipelineId={}", pipeline.getId());
            return;
        }

        // 第二轮: 遍历所有 PENDING 步骤，检查依赖是否满足 → 启动
        int maxActiveStep = pipeline.getCurrentStep();
        for (PipelineStep step : allSteps) {
            if (!TaskConstants.PipelineStepStatus.PENDING.equals(step.getStatus())) {
                if (TaskConstants.PipelineStepStatus.RUNNING.equals(step.getStatus())
                        || TaskConstants.PipelineStepStatus.COMPLETED.equals(step.getStatus())) {
                    maxActiveStep = Math.max(maxActiveStep, step.getStepNumber());
                }
                continue;
            }
            if (areDependenciesMet(step, allSteps)) {
                advanceToStep(pipeline, batchJobId, step.getStepNumber());
                maxActiveStep = Math.max(maxActiveStep, step.getStepNumber());
            }
        }

        // 更新 pipeline.currentStep 为最大活跃步骤编号（向后兼容）
        if (maxActiveStep != pipeline.getCurrentStep()) {
            pipeline.setCurrentStep(maxActiveStep);
            pipeline.setUpdatedAt(LocalDateTime.now());
            pipelineMapper.updateById(pipeline);
        }
    }

    /**
     * 检查步骤的所有依赖是否已完成
     */
    private boolean areDependenciesMet(PipelineStep step, List<PipelineStep> allSteps) {
        List<Integer> deps = step.getDependsOn();
        if (deps == null || deps.isEmpty()) {
            // 无显式依赖 → 默认依赖 stepNumber - 1（保持顺序语义）
            int prevStepNum = step.getStepNumber() - 1;
            if (prevStepNum < 1) {
                return true; // 第一步无依赖
            }
            deps = List.of(prevStepNum);
        }
        for (Integer depNum : deps) {
            boolean depCompleted = allSteps.stream()
                    .filter(s -> s.getStepNumber().equals(depNum))
                    .anyMatch(s -> TaskConstants.PipelineStepStatus.COMPLETED.equals(s.getStatus()));
            if (!depCompleted) {
                return false;
            }
        }
        return true;
    }

    /**
     * 推进到指定步骤
     * 依赖检查已在 checkAndAdvanceStep / areDependenciesMet 中完成
     */
    private void advanceToStep(Pipeline pipeline, String batchJobId, int stepNumber) {
        PipelineStep nextStep = pipelineStepMapper.selectByPipelineIdAndStepNumber(
                pipeline.getId(), stepNumber);
        if (nextStep == null) {
            log.error("Pipeline 步骤不存在: pipelineId={}, stepNumber={}", pipeline.getId(), stepNumber);
            return;
        }

        log.info("Pipeline 推进到步骤 {}: pipelineId={}", stepNumber, pipeline.getId());

        // 标记步骤运行中
        nextStep.setStatus(TaskConstants.PipelineStepStatus.RUNNING);
        nextStep.setStartedAt(LocalDateTime.now());
        nextStep.setUpdatedAt(LocalDateTime.now());
        pipelineStepMapper.updateById(nextStep);

        // 收集前序步骤输出
        BatchJob batchJob = batchJobMapper.selectById(batchJobId);
        Map<Integer, List<BatchJobItem>> stepOutputs = stepResolver.collectStepOutputs(batchJobId);

        // 解析参数模板
        Map<String, Object> resolvedParams = stepResolver.resolveParams(
                nextStep, batchJobId, stepOutputs,
                batchJob.getSharedParams());

        // 准备输入：从前序步骤的输出 items 中获取实体信息
        List<Map<String, Object>> inputs = buildInputsFromPreviousSteps(
                stepOutputs, nextStep, resolvedParams);

        // 创建新 items
        createItemsForStep(batchJob, nextStep, inputs, resolvedParams);

        // 更新总数
        int totalItems = batchJobItemMapper.selectByBatchJobId(batchJobId).size();
        batchJob.setTotalItems(totalItems);
        batchJobMapper.updateById(batchJob);

        // 触发提交
        sendBatchStartMessage(batchJob);
    }

    /**
     * 为步骤创建 batch items
     *
     * @param batchJob       所属 BatchJob
     * @param step           Pipeline 步骤
     * @param inputs         输入数据列表
     * @param resolvedParams 已解析的共享参数（可为 null，step 1 时由 inputs 自带参数）
     */
    private void createItemsForStep(BatchJob batchJob, PipelineStep step,
                                     List<Map<String, Object>> inputs,
                                     Map<String, Object> resolvedParams) {
        int existingCount = batchJobItemMapper.selectByBatchJobId(batchJob.getId()).size();
        int seq = existingCount + 1;
        int fanOut = step.getFanOutCount() != null ? step.getFanOutCount() : 1;

        for (Map<String, Object> input : inputs) {
            for (int v = 0; v < fanOut; v++) {
                BatchJobItem item = new BatchJobItem();
                item.setBatchJobId(batchJob.getId());
                item.setSequenceNumber(seq++);
                item.setPipelineStepId(step.getId());

                // 实体信息
                item.setEntityType((String) input.get("entityType"));
                item.setEntityId((String) input.get("entityId"));
                item.setEntityName((String) input.get("entityName"));

                // Provider 和 generationType
                item.setProviderId(step.getProviderId() != null
                        ? step.getProviderId() : batchJob.getProviderId());
                item.setGenerationType(step.getGenerationType() != null
                        ? step.getGenerationType() : batchJob.getGenerationType());

                // 合并参数: sharedParams + resolvedParams + input.params
                Map<String, Object> mergedParams = new HashMap<>();
                if (batchJob.getSharedParams() != null) {
                    mergedParams.putAll(batchJob.getSharedParams());
                }
                if (resolvedParams != null) {
                    mergedParams.putAll(resolvedParams);
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> inputParams = (Map<String, Object>) input.get("params");
                if (inputParams != null) {
                    mergedParams.putAll(inputParams);
                }

                // 标记所属步骤编号（供 PipelineStepResolver 使用）
                mergedParams.put("_stepNumber", step.getStepNumber());
                mergedParams.put("_pipelineStepId", step.getId());

                item.setParams(mergedParams);

                // 变体
                item.setVariantIndex(v);
                if (fanOut > 1) {
                    item.setVariantSeed(new Random().nextLong());
                }

                item.setSkipCondition(TaskConstants.SkipCondition.NONE);
                item.setSkipped(false);
                item.setCreditCost(0L);
                item.setStatus(TaskConstants.BatchItemStatus.PENDING);
                item.setCreatedAt(LocalDateTime.now());
                item.setUpdatedAt(LocalDateTime.now());

                batchJobItemMapper.insert(item);
            }
        }

        log.info("Pipeline 步骤 {} 创建了 {} 个 items: batchJobId={}",
                step.getStepNumber(), inputs.size() * fanOut, batchJob.getId());
    }

    /**
     * 从前序步骤的输出中构建下一步的输入列表
     */
    private List<Map<String, Object>> buildInputsFromPreviousSteps(
            Map<Integer, List<BatchJobItem>> stepOutputs,
            PipelineStep nextStep,
            Map<String, Object> resolvedParams) {

        List<Map<String, Object>> inputs = new ArrayList<>();

        // 确定上游步骤编号
        int prevStepNum = nextStep.getStepNumber() - 1;
        if (nextStep.getDependsOn() != null && !nextStep.getDependsOn().isEmpty()) {
            prevStepNum = nextStep.getDependsOn().get(0);
        }

        List<BatchJobItem> prevItems = stepOutputs.get(prevStepNum);
        if (prevItems == null || prevItems.isEmpty()) {
            // 无前序输出，创建一个默认输入
            Map<String, Object> defaultInput = new HashMap<>();
            if (resolvedParams != null) {
                defaultInput.put("params", new HashMap<>(resolvedParams));
            }
            inputs.add(defaultInput);
            return inputs;
        }

        // 检查 resolvedParams 中是否包含 List 类型值（聚合引用）
        // 如果有，说明是 fan-out 聚合场景，将所有前序 items 合并为单个输入
        boolean hasAggregateParam = resolvedParams != null && resolvedParams.values().stream()
                .anyMatch(v -> v instanceof List);

        if (hasAggregateParam) {
            // 聚合模式: 所有前序 items 合并为一个输入
            Map<String, Object> input = new HashMap<>();
            // 使用第一个 item 的实体信息
            BatchJobItem firstItem = prevItems.get(0);
            input.put("entityType", firstItem.getEntityType());
            input.put("entityId", firstItem.getEntityId());
            input.put("entityName", firstItem.getEntityName());

            Map<String, Object> params = new HashMap<>();
            if (resolvedParams != null) {
                params.putAll(resolvedParams);
            }
            input.put("params", params);
            inputs.add(input);
            return inputs;
        }

        // 每个前序 item 的输出作为当前步骤的一个输入
        for (BatchJobItem prevItem : prevItems) {
            Map<String, Object> input = new HashMap<>();
            input.put("entityType", prevItem.getEntityType());
            input.put("entityId", prevItem.getEntityId());
            input.put("entityName", prevItem.getEntityName());

            // 将前序 item 的输出合并到参数中
            Map<String, Object> params = new HashMap<>();
            if (resolvedParams != null) {
                params.putAll(resolvedParams);
            }
            // 传递上游的 assetId 作为参考素材
            if (prevItem.getAssetId() != null) {
                params.put("reference_asset_id", prevItem.getAssetId());
            }
            // 传递上游的输出文本（如 LLM 生成的 prompt）
            if (prevItem.getParams() != null) {
                Object output = prevItem.getParams().get("_output");
                if (output instanceof Map<?, ?> outputMap) {
                    Object text = outputMap.get("text");
                    if (text != null) {
                        params.put("prompt", String.valueOf(text));
                    }
                    Object fileUrl = outputMap.get("file_url");
                    if (fileUrl != null) {
                        params.put("reference_image_url", String.valueOf(fileUrl));
                    }
                }
            }

            input.put("params", params);
            inputs.add(input);
        }

        return inputs;
    }

    /**
     * 检查步骤是否完成（所有属于该步骤的 items 都不再是 PENDING/SUBMITTED/RUNNING）
     */
    private boolean isStepComplete(String batchJobId, String stepId) {
        int pending = batchJobItemMapper.countByStatusAndStepId(
                batchJobId, TaskConstants.BatchItemStatus.PENDING, stepId);
        int submitted = batchJobItemMapper.countByStatusAndStepId(
                batchJobId, TaskConstants.BatchItemStatus.SUBMITTED, stepId);
        int running = batchJobItemMapper.countByStatusAndStepId(
                batchJobId, TaskConstants.BatchItemStatus.RUNNING, stepId);
        return pending == 0 && submitted == 0 && running == 0;
    }

    private void sendBatchStartMessage(BatchJob job) {
        Map<String, Object> payload = Map.of(
                "batchJobId", job.getId(),
                "workspaceId", job.getWorkspaceId()
        );
        MessageWrapper<Map<String, Object>> message = MessageWrapper.<Map<String, Object>>builder()
                .messageId(UuidGenerator.generateUuidV7())
                .messageType(MqConstants.BatchJob.MSG_START)
                .payload(payload)
                .workspaceId(job.getWorkspaceId())
                .senderId(job.getCreatorId())
                .build();
        messageProducer.sendDirect(MqConstants.BatchJob.ROUTING_START, message);
    }

    /**
     * 重试 Pipeline 指定步骤的失败项
     *
     * @param batchJobId 批量作业ID
     * @param stepNumber 步骤编号（从1开始）
     */
    @Transactional(rollbackFor = Exception.class)
    public void retryPipelineStep(String batchJobId, int stepNumber) {
        Pipeline pipeline = pipelineMapper.selectByBatchJobId(batchJobId);
        if (pipeline == null) {
            throw new IllegalArgumentException("Pipeline 不存在: batchJobId=" + batchJobId);
        }

        PipelineStep step = pipelineStepMapper.selectByPipelineIdAndStepNumber(
                pipeline.getId(), stepNumber);
        if (step == null) {
            throw new IllegalArgumentException("Pipeline 步骤不存在: stepNumber=" + stepNumber);
        }

        // 查找该步骤的 FAILED items
        List<BatchJobItem> stepItems = batchJobItemMapper.selectByStepId(batchJobId, step.getId());
        List<BatchJobItem> failedItems = stepItems.stream()
                .filter(item -> TaskConstants.BatchItemStatus.FAILED.equals(item.getStatus()))
                .toList();

        if (failedItems.isEmpty()) {
            throw new IllegalArgumentException("步骤 " + stepNumber + " 没有失败的子项");
        }

        // 重置 FAILED items 为 PENDING
        for (BatchJobItem item : failedItems) {
            item.setStatus(TaskConstants.BatchItemStatus.PENDING);
            item.setErrorMessage(null);
            item.setTaskId(null);
            item.setUpdatedAt(LocalDateTime.now());
            batchJobItemMapper.updateById(item);
        }

        // 重置步骤状态为 RUNNING
        step.setStatus(TaskConstants.PipelineStepStatus.RUNNING);
        step.setCompletedAt(null);
        step.setUpdatedAt(LocalDateTime.now());
        pipelineStepMapper.updateById(step);

        // 恢复 Pipeline 状态为 RUNNING（若已 FAILED/COMPLETED）
        if (!TaskConstants.PipelineStatus.RUNNING.equals(pipeline.getStatus())) {
            pipeline.setStatus(TaskConstants.PipelineStatus.RUNNING);
            pipeline.setUpdatedAt(LocalDateTime.now());
            pipelineMapper.updateById(pipeline);
        }

        // 恢复 BatchJob 状态为 RUNNING 并调整 failedItems 计数
        BatchJob job = batchJobMapper.selectById(batchJobId);
        if (job != null) {
            if (!TaskConstants.BatchStatus.RUNNING.equals(job.getStatus())) {
                job.setStatus(TaskConstants.BatchStatus.RUNNING);
            }
            int currentFailed = job.getFailedItems() != null ? job.getFailedItems() : 0;
            job.setFailedItems(Math.max(0, currentFailed - failedItems.size()));
            batchJobMapper.updateById(job);

            // 触发重新提交
            sendBatchStartMessage(job);
        }

        log.info("Pipeline 步骤重试: batchJobId={}, stepNumber={}, retryCount={}",
                batchJobId, stepNumber, failedItems.size());
    }

    // ==================== 模板定义 ====================

    /**
     * 获取模板对应的步骤配置
     */
    private List<PipelineStepConfig> getTemplateSteps(String templateCode) {
        if (templateCode == null) {
            return Collections.emptyList();
        }
        return switch (templateCode) {
            case TaskConstants.PipelineTemplate.TEXT_TO_PROMPT_TO_IMAGE -> List.of(
                    new PipelineStepConfig("润色提示词",
                            TaskConstants.PipelineStepType.GENERATE_TEXT, "TEXT",
                            IMAGE_PROMPT_PROVIDER_ID, Map.of("system_prompt", "你是一个专业的AI绘画提示词专家。请根据以下描述生成高质量的英文提示词。"),
                            null, 1),
                    new PipelineStepConfig("生成图片",
                            TaskConstants.PipelineStepType.GENERATE_IMAGE, "IMAGE",
                            null, Map.of("prompt", "{{steps[1].output.text}}"),
                            List.of(1), 1)
            );
            case TaskConstants.PipelineTemplate.TEXT_TO_PROMPT_TO_VIDEO -> List.of(
                    new PipelineStepConfig("润色提示词",
                            TaskConstants.PipelineStepType.GENERATE_TEXT, "TEXT",
                            VIDEO_PROMPT_PROVIDER_ID, Map.of("system_prompt", "你是一个专业的AI视频提示词专家。请根据以下描述生成高质量的英文提示词。"),
                            null, 1),
                    new PipelineStepConfig("生成视频",
                            TaskConstants.PipelineStepType.GENERATE_VIDEO, "VIDEO",
                            null, Map.of("prompt", "{{steps[1].output.text}}"),
                            List.of(1), 1)
            );
            case TaskConstants.PipelineTemplate.TEXT_TO_PROMPT_TO_AUDIO -> List.of(
                    new PipelineStepConfig("润色提示词",
                            TaskConstants.PipelineStepType.GENERATE_TEXT, "TEXT",
                            AUDIO_PROMPT_PROVIDER_ID, Map.of("system_prompt", "你是一个专业的AI音频提示词专家。请根据以下描述生成高质量的英文提示词。"),
                            null, 1),
                    new PipelineStepConfig("生成音频",
                            TaskConstants.PipelineStepType.GENERATE_AUDIO, "AUDIO",
                            null, Map.of("prompt", "{{steps[1].output.text}}"),
                            List.of(1), 1)
            );
            case TaskConstants.PipelineTemplate.TEXT_TO_IMAGE_TO_VIDEO -> List.of(
                    new PipelineStepConfig("生成图片",
                            TaskConstants.PipelineStepType.GENERATE_IMAGE, "IMAGE",
                            null, Map.of(), null, 1),
                    new PipelineStepConfig("图生视频",
                            TaskConstants.PipelineStepType.GENERATE_VIDEO, "VIDEO",
                            null, Map.of("reference_image_url", "{{steps[1].output.file_url}}"),
                            List.of(1), 1)
            );
            case TaskConstants.PipelineTemplate.TEXT_TO_KEYFRAMES_TO_VIDEO -> List.of(
                    new PipelineStepConfig("生成关键帧",
                            TaskConstants.PipelineStepType.GENERATE_IMAGE, "IMAGE",
                            null, Map.of(), null, 3),
                    new PipelineStepConfig("多图生视频",
                            TaskConstants.PipelineStepType.GENERATE_VIDEO, "VIDEO",
                            null, Map.of("reference_image_urls", "{{steps[1].output.all_file_url}}"),
                            List.of(1), 1)
            );
            case TaskConstants.PipelineTemplate.FULL_STORYBOARD -> List.of(
                    new PipelineStepConfig("润色提示词",
                            TaskConstants.PipelineStepType.GENERATE_TEXT, "TEXT",
                            IMAGE_PROMPT_PROVIDER_ID, Map.of("system_prompt", "你是一个专业的分镜提示词专家。请根据分镜描述生成高质量的英文图片提示词。"),
                            null, 1),
                    new PipelineStepConfig("生成分镜图",
                            TaskConstants.PipelineStepType.GENERATE_IMAGE, "IMAGE",
                            null, Map.of("prompt", "{{steps[1].output.text}}"),
                            List.of(1), 1),
                    new PipelineStepConfig("图生分镜视频",
                            TaskConstants.PipelineStepType.GENERATE_VIDEO, "VIDEO",
                            null, Map.of("reference_image_url", "{{steps[2].output.file_url}}"),
                            List.of(2), 1)
            );
            default -> {
                log.warn("未知的 Pipeline 模板: {}", templateCode);
                yield Collections.emptyList();
            }
        };
    }

    private String getTemplateName(String templateCode) {
        if (templateCode == null) return "Pipeline";
        return switch (templateCode) {
            case TaskConstants.PipelineTemplate.TEXT_TO_PROMPT_TO_IMAGE -> "提示词优化 → 图片生成";
            case TaskConstants.PipelineTemplate.TEXT_TO_PROMPT_TO_VIDEO -> "提示词优化 → 视频生成";
            case TaskConstants.PipelineTemplate.TEXT_TO_PROMPT_TO_AUDIO -> "提示词优化 → 音频生成";
            case TaskConstants.PipelineTemplate.TEXT_TO_IMAGE_TO_VIDEO -> "文生图 → 图生视频";
            case TaskConstants.PipelineTemplate.TEXT_TO_KEYFRAMES_TO_VIDEO -> "关键帧生成 → 视频合成";
            case TaskConstants.PipelineTemplate.FULL_STORYBOARD -> "分镜全流程";
            default -> "Pipeline";
        };
    }

    /**
     * 步骤配置 DTO（内部使用）
     */
    public record PipelineStepConfig(
            String name,
            String stepType,
            String generationType,
            String providerId,
            Map<String, Object> paramsTemplate,
            List<Integer> dependsOn,
            Integer fanOutCount
    ) {}
}
