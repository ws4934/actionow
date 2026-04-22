package com.actionow.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.id.UuidGenerator;
import com.actionow.common.core.result.ResultCode;
import com.actionow.project.dto.inspiration.CreateSessionRequest;
import com.actionow.project.dto.inspiration.InspirationSessionResponse;
import com.actionow.project.dto.inspiration.UpdateSessionRequest;
import com.actionow.common.core.constant.CommonConstants;
import com.actionow.project.entity.Asset;
import com.actionow.project.entity.InspirationRecord;
import com.actionow.project.entity.InspirationRecordAsset;
import com.actionow.project.entity.InspirationSession;
import com.actionow.project.mapper.AssetMapper;
import com.actionow.project.mapper.InspirationRecordAssetMapper;
import com.actionow.project.mapper.InspirationRecordMapper;
import com.actionow.project.mapper.InspirationSessionMapper;
import com.actionow.project.service.InspirationSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 灵感会话服务实现。
 *
 * <p><b>已 deprecated</b>：随 {@link InspirationSessionService} 一同冻结。
 *
 * @author Actionow
 */
@Deprecated(since = "3.0", forRemoval = true)
@SuppressWarnings("deprecation")
@Slf4j
@Service
@RequiredArgsConstructor
public class InspirationSessionServiceImpl implements InspirationSessionService {

    private final InspirationSessionMapper sessionMapper;
    private final InspirationRecordMapper recordMapper;
    private final InspirationRecordAssetMapper recordAssetMapper;
    private final AssetMapper assetMapper;

    @Override
    public Page<InspirationSessionResponse> listSessions(String workspaceId, String userId,
                                                          Integer page, Integer size, String status) {
        Page<InspirationSession> pageParam = new Page<>(page, size);

        LambdaQueryWrapper<InspirationSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InspirationSession::getWorkspaceId, workspaceId)
                .eq(InspirationSession::getUserId, userId)
                .eq(InspirationSession::getDeleted, 0);

        if (StringUtils.hasText(status)) {
            wrapper.eq(InspirationSession::getStatus, status);
        }

        wrapper.orderByDesc(InspirationSession::getLastActiveAt);

        Page<InspirationSession> resultPage = sessionMapper.selectPage(pageParam, wrapper);

        Page<InspirationSessionResponse> responsePage = new Page<>(
                resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        responsePage.setRecords(resultPage.getRecords().stream()
                .map(InspirationSessionResponse::fromEntity)
                .toList());

        return responsePage;
    }

    @Override
    public InspirationSessionResponse getSession(String sessionId) {
        InspirationSession session = getSessionOrThrow(sessionId);
        return InspirationSessionResponse.fromEntity(session);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InspirationSessionResponse createSession(CreateSessionRequest request,
                                                     String workspaceId, String userId) {
        InspirationSession session = new InspirationSession();
        session.setId(UuidGenerator.generateUuidV7());
        session.setWorkspaceId(workspaceId);
        session.setUserId(userId);
        session.setTitle(StringUtils.hasText(request.getTitle()) ? request.getTitle() : "");
        session.setRecordCount(0);
        session.setTotalCredits(BigDecimal.ZERO);
        session.setStatus("ACTIVE");
        session.setLastActiveAt(LocalDateTime.now());
        session.setVersion(1);
        session.setCreatedBy(userId);

        sessionMapper.insert(session);

        log.info("创建灵感会话: sessionId={}, workspaceId={}", session.getId(), workspaceId);
        return InspirationSessionResponse.fromEntity(session);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InspirationSessionResponse updateSession(String sessionId, UpdateSessionRequest request) {
        InspirationSession session = getSessionOrThrow(sessionId);
        session.setTitle(request.getTitle());

        int rows = sessionMapper.updateById(session);
        if (rows == 0) {
            throw new BusinessException(ResultCode.CONCURRENT_OPERATION);
        }

        return InspirationSessionResponse.fromEntity(session);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(String sessionId) {
        InspirationSession session = getSessionOrThrow(sessionId);

        // 查出该会话下所有记录 ID，用于级联删除资产
        List<InspirationRecord> records = recordMapper.selectBySessionId(sessionId);
        if (!records.isEmpty()) {
            List<String> recordIds = records.stream().map(InspirationRecord::getId).toList();

            // 收集关联的 t_asset ID，用于级联软删
            List<InspirationRecordAsset> recordAssets = recordAssetMapper.selectByRecordIds(
                    new LinkedHashSet<>(recordIds));
            List<String> assetIds = recordAssets.stream()
                    .map(InspirationRecordAsset::getAssetId)
                    .filter(id -> id != null && !id.isBlank())
                    .toList();

            // 批量软删除 t_inspiration_record_asset
            LambdaUpdateWrapper<InspirationRecordAsset> assetWrapper = new LambdaUpdateWrapper<>();
            assetWrapper.in(InspirationRecordAsset::getRecordId, recordIds)
                    .eq(InspirationRecordAsset::getDeleted, 0)
                    .set(InspirationRecordAsset::getDeleted, 1);
            recordAssetMapper.update(null, assetWrapper);

            // 批量软删除关联的 t_asset 记录
            if (!assetIds.isEmpty()) {
                LambdaUpdateWrapper<Asset> tAssetWrapper = new LambdaUpdateWrapper<>();
                tAssetWrapper.in(Asset::getId, assetIds)
                        .eq(Asset::getDeleted, CommonConstants.NOT_DELETED)
                        .set(Asset::getDeleted, 1)
                        .set(Asset::getDeletedAt, LocalDateTime.now());
                assetMapper.update(null, tAssetWrapper);
            }

            // 批量软删除记录
            LambdaUpdateWrapper<InspirationRecord> recordWrapper = new LambdaUpdateWrapper<>();
            recordWrapper.eq(InspirationRecord::getSessionId, sessionId)
                    .eq(InspirationRecord::getDeleted, 0)
                    .set(InspirationRecord::getDeleted, 1);
            recordMapper.update(null, recordWrapper);
        }

        // 软删除会话
        sessionMapper.deleteById(session.getId());

        log.info("删除灵感会话: sessionId={}, 级联删除 {} 条记录", sessionId, records.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InspirationSessionResponse archiveSession(String sessionId) {
        InspirationSession session = getSessionOrThrow(sessionId);
        session.setStatus("ARCHIVED");

        int rows = sessionMapper.updateById(session);
        if (rows == 0) {
            throw new BusinessException(ResultCode.CONCURRENT_OPERATION);
        }

        log.info("归档灵感会话: sessionId={}", sessionId);
        return InspirationSessionResponse.fromEntity(session);
    }

    private InspirationSession getSessionOrThrow(String sessionId) {
        InspirationSession session = sessionMapper.selectById(sessionId);
        if (session == null || session.getDeleted() != 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "灵感会话不存在");
        }
        return session;
    }
}
