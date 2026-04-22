package com.actionow.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.id.UuidGenerator;
import com.actionow.common.core.result.Result;
import com.actionow.common.core.result.ResultCode;
import com.actionow.project.dto.asset.AssetResponse;
import com.actionow.project.dto.inspiration.*;
import com.actionow.project.entity.Asset;
import com.actionow.project.entity.AssetMetaInfo;
import com.actionow.project.entity.InspirationRecord;
import com.actionow.project.entity.InspirationRecordAsset;
import com.actionow.project.entity.InspirationSession;
import com.actionow.project.feign.TaskFeignClient;
import com.actionow.project.mapper.InspirationRecordAssetMapper;
import com.actionow.project.mapper.InspirationRecordMapper;
import com.actionow.project.mapper.InspirationSessionMapper;
import com.actionow.project.service.AssetService;
import com.actionow.project.service.InspirationRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 灵感生成记录服务实现。
 *
 * <p><b>已 deprecated</b>：随 {@link InspirationRecordService} 一同冻结。
 *
 * @author Actionow
 */
@Deprecated(since = "3.0", forRemoval = true)
@SuppressWarnings("deprecation")
@Slf4j
@Service
@RequiredArgsConstructor
public class InspirationRecordServiceImpl implements InspirationRecordService {

    private final InspirationRecordMapper recordMapper;
    private final InspirationRecordAssetMapper recordAssetMapper;
    private final InspirationSessionMapper sessionMapper;
    private final TaskFeignClient taskFeignClient;
    private final AssetService assetService;

    @Override
    public Page<InspirationRecordResponse> listRecords(String sessionId, Integer page, Integer size) {
        Page<InspirationRecord> pageParam = new Page<>(page, size);

        LambdaQueryWrapper<InspirationRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InspirationRecord::getSessionId, sessionId)
                .eq(InspirationRecord::getDeleted, 0)
                .orderByAsc(InspirationRecord::getCreatedAt);

        Page<InspirationRecord> resultPage = recordMapper.selectPage(pageParam, wrapper);

        // 批量查询资产
        Set<String> recordIds = resultPage.getRecords().stream()
                .map(InspirationRecord::getId)
                .collect(Collectors.toSet());
        List<InspirationRecordAsset> allAssets = recordAssetMapper.selectByRecordIds(recordIds);
        Map<String, List<InspirationRecordAsset>> assetsByRecordId = allAssets.stream()
                .collect(Collectors.groupingBy(InspirationRecordAsset::getRecordId));

        // 收集 params 中引用的素材 ID（如 image 字段），批量查询 t_asset
        Set<String> refAssetIds = new LinkedHashSet<>();
        for (InspirationRecord record : resultPage.getRecords()) {
            if (record.getParams() != null) {
                collectAssetIdsFromParams(record.getParams(), refAssetIds);
            }
        }
        Map<String, AssetResponse> refAssetMap = refAssetIds.isEmpty()
                ? Map.of()
                : assetService.batchGet(new ArrayList<>(refAssetIds)).stream()
                        .collect(Collectors.toMap(AssetResponse::getId, a -> a, (a, b) -> a));

        // 构建响应
        List<InspirationRecordResponse> records = resultPage.getRecords().stream()
                .map(record -> {
                    InspirationRecordResponse response = InspirationRecordResponse.fromEntity(record);
                    List<InspirationRecordAsset> assets = assetsByRecordId
                            .getOrDefault(record.getId(), List.of());
                    response.setAssets(assets.stream()
                            .map(InspirationAssetResponse::fromEntity)
                            .toList());

                    // 填充 refAssets：从 params 中提取引用的素材
                    if (record.getParams() != null && !refAssetMap.isEmpty()) {
                        Set<String> thisRecordRefIds = new LinkedHashSet<>();
                        collectAssetIdsFromParams(record.getParams(), thisRecordRefIds);
                        List<InspirationAssetResponse> refAssets = thisRecordRefIds.stream()
                                .filter(refAssetMap::containsKey)
                                .map(id -> InspirationAssetResponse.fromAssetResponse(refAssetMap.get(id)))
                                .toList();
                        if (!refAssets.isEmpty()) {
                            response.setRefAssets(refAssets);
                        }
                    }

                    return response;
                })
                .toList();

        Page<InspirationRecordResponse> responsePage = new Page<>(
                resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        responsePage.setRecords(records);
        return responsePage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRecord(String sessionId, String recordId) {
        InspirationRecord record = recordMapper.selectById(recordId);
        if (record == null || record.getDeleted() != 0 || !sessionId.equals(record.getSessionId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "生成记录不存在");
        }

        // 软删除关联资产
        LambdaUpdateWrapper<InspirationRecordAsset> assetWrapper = new LambdaUpdateWrapper<>();
        assetWrapper.eq(InspirationRecordAsset::getRecordId, recordId)
                .set(InspirationRecordAsset::getDeleted, 1);
        recordAssetMapper.update(null, assetWrapper);

        // 软删除记录
        recordMapper.deleteById(recordId);

        // 更新会话记录数
        sessionMapper.decrementRecordCount(sessionId);

        log.info("删除灵感记录: recordId={}, sessionId={}", recordId, sessionId);
    }

    @Override
    public InspirationGenerateResponse submitGeneration(InspirationGenerateRequest request,
                                                         String workspaceId, String userId) {
        // 验证会话存在
        InspirationSession session = sessionMapper.selectById(request.getSessionId());
        if (session == null || session.getDeleted() != 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "灵感会话不存在");
        }

        // Phase 1: 本地数据库操作（事务内）
        InspirationRecord record = createRecordInTransaction(request, session, userId);

        // Phase 2: 远程调用（事务外，避免 Feign 调用持有 DB 连接）
        InspirationGenerateResponse response = new InspirationGenerateResponse();
        response.setRecordId(record.getId());

        Map<String, Object> taskRequest = new HashMap<>();
        taskRequest.put("generationType", request.getGenerationType());
        taskRequest.put("providerId", request.getProviderId());
        taskRequest.put("params", buildParams(request));
        taskRequest.put("source", "INSPIRATION");
        if (request.getCount() != null && request.getCount() > 1) {
            taskRequest.put("count", request.getCount());
        }

        try {
            Result<Map<String, Object>> taskResult = taskFeignClient.submitAiGeneration(
                    workspaceId, userId, taskRequest);

            if (taskResult != null && taskResult.isSuccess() && taskResult.getData() != null) {
                Map<String, Object> data = taskResult.getData();
                String taskId = (String) data.get("taskId");
                String taskStatus = (String) data.get("status");
                Number creditCost = (Number) data.get("creditCost");
                String providerName = (String) data.get("providerName");

                // Phase 3: 更新记录（独立事务）
                LambdaUpdateWrapper<InspirationRecord> updateWrapper = new LambdaUpdateWrapper<>();
                updateWrapper.eq(InspirationRecord::getId, record.getId())
                        .set(InspirationRecord::getTaskId, taskId)
                        .set(InspirationRecord::getStatus, "RUNNING");
                if (creditCost != null) {
                    updateWrapper.set(InspirationRecord::getCreditCost,
                            BigDecimal.valueOf(creditCost.doubleValue()));
                }
                if (StringUtils.hasText(providerName)) {
                    updateWrapper.set(InspirationRecord::getProviderName, providerName);
                }
                recordMapper.update(null, updateWrapper);

                response.setTaskId(taskId);
                response.setTaskStatus(taskStatus != null ? taskStatus : "PENDING");
                response.setCreditCost(creditCost != null
                        ? BigDecimal.valueOf(creditCost.doubleValue()) : BigDecimal.ZERO);
                response.setSuccess(true);
            } else {
                String errorMsg = taskResult != null ? taskResult.getMessage() : "任务服务调用失败";
                markRecordFailed(record.getId(), errorMsg);

                response.setSuccess(false);
                response.setErrorMessage(errorMsg);
                response.setTaskStatus("FAILED");
            }
        } catch (Exception e) {
            log.error("提交灵感生成任务失败: recordId={}, error={}", record.getId(), e.getMessage(), e);
            markRecordFailed(record.getId(), e.getMessage());

            response.setSuccess(false);
            response.setErrorMessage("生成任务提交失败: " + e.getMessage());
            response.setTaskStatus("FAILED");
        }

        return response;
    }

    /**
     * Phase 1: 在事务内创建记录和更新会话
     */
    @Transactional(rollbackFor = Exception.class)
    public InspirationRecord createRecordInTransaction(InspirationGenerateRequest request,
                                                        InspirationSession session, String userId) {
        InspirationRecord record = new InspirationRecord();
        record.setId(UuidGenerator.generateUuidV7());
        record.setSessionId(request.getSessionId());
        record.setPrompt(request.getPrompt());
        record.setNegativePrompt(request.getNegativePrompt());
        record.setGenerationType(request.getGenerationType());
        record.setProviderId(request.getProviderId());
        record.setParams(request.getParams());
        record.setStatus("PENDING");
        record.setCreditCost(BigDecimal.ZERO);
        record.setProgress(0);
        record.setVersion(1);
        record.setCreatedBy(userId);

        recordMapper.insert(record);

        // 自动填充会话标题（首次生成时）
        if (!StringUtils.hasText(session.getTitle())) {
            String autoTitle = request.getPrompt().length() > 50
                    ? request.getPrompt().substring(0, 50)
                    : request.getPrompt();
            LambdaUpdateWrapper<InspirationSession> titleWrapper = new LambdaUpdateWrapper<>();
            titleWrapper.eq(InspirationSession::getId, session.getId())
                    .set(InspirationSession::getTitle, autoTitle);
            sessionMapper.update(null, titleWrapper);
        }

        // 更新会话记录数和活跃时间
        sessionMapper.incrementRecordCount(request.getSessionId());

        return record;
    }

    private void markRecordFailed(String recordId, String errorMsg) {
        LambdaUpdateWrapper<InspirationRecord> failWrapper = new LambdaUpdateWrapper<>();
        failWrapper.eq(InspirationRecord::getId, recordId)
                .set(InspirationRecord::getStatus, "FAILED")
                .set(InspirationRecord::getErrorMessage, errorMsg)
                .set(InspirationRecord::getCompletedAt, LocalDateTime.now());
        recordMapper.update(null, failWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleTaskCompleted(String taskId, String status, Map<String, Object> outputResult,
                                     Number creditCost, String errorMessage,
                                     Map<String, Object> inputParams) {
        InspirationRecord record = recordMapper.selectByTaskId(taskId);
        if (record == null) {
            log.debug("任务回调无匹配灵感记录，忽略: taskId={}", taskId);
            return;
        }

        log.info("处理灵感任务回调: taskId={}, recordId={}, status={}", taskId, record.getId(), status);

        LambdaUpdateWrapper<InspirationRecord> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(InspirationRecord::getId, record.getId())
                .set(InspirationRecord::getStatus, status)
                .set(InspirationRecord::getCompletedAt, LocalDateTime.now());

        // 从 inputParams 补充 providerName（submitGeneration Phase 3 可能未获取到）
        if (inputParams != null) {
            String providerName = (String) inputParams.get("providerName");
            if (StringUtils.hasText(providerName) && !StringUtils.hasText(record.getProviderName())) {
                updateWrapper.set(InspirationRecord::getProviderName, providerName);
            }
        }

        if ("COMPLETED".equals(status)) {
            updateWrapper.set(InspirationRecord::getProgress, 100);

            // 积分：优先使用 task 上报值，回退到 inputParams.costPoints（冻结时的预估值）
            BigDecimal actualCost = resolveActualCreditCost(creditCost, inputParams);
            if (actualCost.compareTo(BigDecimal.ZERO) > 0) {
                updateWrapper.set(InspirationRecord::getCreditCost, actualCost);
                sessionMapper.addCredits(record.getSessionId(), actualCost);
            }

            // 解析并保存资产
            if (outputResult != null) {
                saveAssetsFromResult(record, outputResult);
            }

        } else if ("FAILED".equals(status)) {
            updateWrapper.set(InspirationRecord::getErrorMessage, errorMessage);
        }

        recordMapper.update(null, updateWrapper);
    }

    /**
     * 解析实际积分消耗：优先 task.creditCost，回退 inputParams.costPoints
     */
    private BigDecimal resolveActualCreditCost(Number creditCost, Map<String, Object> inputParams) {
        if (creditCost != null && creditCost.doubleValue() > 0) {
            return BigDecimal.valueOf(creditCost.doubleValue());
        }
        if (inputParams != null && inputParams.get("costPoints") != null) {
            return BigDecimal.valueOf(((Number) inputParams.get("costPoints")).doubleValue());
        }
        return BigDecimal.ZERO;
    }

    /**
     * 从任务结果中解析并保存资产。
     * 先在 t_asset 中创建记录（统一存储），再关联到 t_inspiration_record_asset。
     *
     * 支持两种结构：
     * 1. 列表形式：outputResult.assets = [{url, thumbnailUrl, ...}, ...]
     * 2. 扁平形式：outputResult = {fileUrl, thumbnailUrl, mimeType, metaInfo, ...}（ProviderExecutionResult.toMap）
     */
    @SuppressWarnings("unchecked")
    private void saveAssetsFromResult(InspirationRecord record, Map<String, Object> outputResult) {
        List<Map<String, Object>> assetMaps = new ArrayList<>();

        Object assetsObj = outputResult.get("assets");
        if (assetsObj instanceof List<?> assetsList && !assetsList.isEmpty()) {
            for (Object item : assetsList) {
                if (item instanceof Map<?, ?> m) {
                    assetMaps.add((Map<String, Object>) m);
                }
            }
        } else if (StringUtils.hasText((String) outputResult.get("fileUrl"))) {
            Map<String, Object> flat = new HashMap<>();
            flat.put("url", outputResult.get("fileUrl"));
            flat.put("thumbnailUrl", outputResult.get("thumbnailUrl"));
            flat.put("mimeType", outputResult.get("mimeType"));
            flat.put("fileSize", outputResult.get("fileSize"));
            if (outputResult.get("metaInfo") instanceof Map<?, ?> rawMeta) {
                AssetMetaInfo meta = AssetMetaInfo.fromMap((Map<String, Object>) rawMeta);
                if (meta.getWidth() != null) flat.put("width", meta.getWidth());
                if (meta.getHeight() != null) flat.put("height", meta.getHeight());
                if (meta.getDuration() != null) flat.put("duration", meta.getDuration());
            }
            assetMaps.add(flat);
        }

        if (assetMaps.isEmpty()) {
            return;
        }

        String workspaceId = UserContextHolder.getContext().getWorkspaceId();
        String firstThumbnailUrl = null;

        for (Map<String, Object> assetMap : assetMaps) {
            String url = (String) assetMap.get("url");
            String thumbnailUrl = (String) assetMap.get("thumbnailUrl");
            String mimeType = (String) assetMap.get("mimeType");
            Long fileSize = assetMap.get("fileSize") instanceof Number s ? s.longValue() : null;

            Map<String, Object> metaInfo = AssetMetaInfo.fromMap(assetMap, false).toMap();

            // 1. 在 t_asset 中创建记录
            Asset asset = assetService.createForInspiration(
                    url, thumbnailUrl, record.getGenerationType(),
                    mimeType, fileSize,
                    metaInfo,
                    workspaceId, record.getCreatedBy());

            // 2. 在 t_inspiration_record_asset 中创建关联（冗余 URL 便于快速查询）
            InspirationRecordAsset recordAsset = new InspirationRecordAsset();
            recordAsset.setId(UuidGenerator.generateUuidV7());
            recordAsset.setRecordId(record.getId());
            recordAsset.setAssetId(asset.getId());
            recordAsset.setAssetType(record.getGenerationType());
            recordAsset.setUrl(url);
            recordAsset.setThumbnailUrl(thumbnailUrl);
            recordAsset.setMimeType(mimeType);
            recordAsset.setFileSize(fileSize);
            if (assetMap.get("width") instanceof Number w) recordAsset.setWidth(w.intValue());
            if (assetMap.get("height") instanceof Number h) recordAsset.setHeight(h.intValue());
            if (assetMap.get("duration") instanceof Number d) recordAsset.setDuration(d.doubleValue());
            recordAsset.setVersion(1);
            recordAsset.setCreatedBy(record.getCreatedBy());
            recordAssetMapper.insert(recordAsset);

            if (firstThumbnailUrl == null) {
                firstThumbnailUrl = thumbnailUrl != null ? thumbnailUrl : url;
            }
        }

        // 更新会话封面（如果当前无封面）
        if (firstThumbnailUrl != null) {
            String coverUrl = firstThumbnailUrl;
            InspirationSession session = sessionMapper.selectById(record.getSessionId());
            if (session != null && !StringUtils.hasText(session.getCoverUrl())) {
                LambdaUpdateWrapper<InspirationSession> coverWrapper = new LambdaUpdateWrapper<>();
                coverWrapper.eq(InspirationSession::getId, session.getId())
                        .set(InspirationSession::getCoverUrl, coverUrl);
                sessionMapper.update(null, coverWrapper);
            }
        }
    }

    /**
     * 构建传递给 task 服务的 params
     */
    private Map<String, Object> buildParams(InspirationGenerateRequest request) {
        Map<String, Object> params = request.getParams() != null
                ? new HashMap<>(request.getParams()) : new HashMap<>();
        params.put("prompt", request.getPrompt());
        if (StringUtils.hasText(request.getNegativePrompt())) {
            params.put("negativePrompt", request.getNegativePrompt());
        }
        return params;
    }

    /**
     * 从 params 中收集 UUID 格式的素材引用 ID（如 image 字段中的 asset ID）
     */
    private void collectAssetIdsFromParams(Map<String, Object> params, Set<String> assetIds) {
        for (Object value : params.values()) {
            if (value instanceof String s && isUuid(s)) {
                assetIds.add(s);
            } else if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof String s && isUuid(s)) {
                        assetIds.add(s);
                    }
                }
            }
        }
    }

    private static boolean isUuid(String value) {
        return value.length() == 36 && value.matches("^[0-9a-fA-F-]{36}$");
    }
}
