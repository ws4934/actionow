package com.actionow.agent.scheduler;

import com.actionow.agent.constant.SessionStatus;
import com.actionow.agent.entity.AgentSessionEntity;
import com.actionow.agent.mapper.AgentSessionMapper;
import com.actionow.common.core.context.UserContext;
import com.actionow.common.core.context.UserContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话归档定时任务
 * 处理空闲会话自动归档和已删除会话清理
 *
 * 触发方式：
 * 1. 由 workspace 服务的定时维护任务通过内部 API 调用（推荐）
 * 2. 直接调用 archiveIdleSessions 方法（需要已设置租户上下文）
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionArchiveScheduler {

    private final AgentSessionMapper sessionMapper;

    /**
     * 空闲超时天数，超过此天数未活跃的会话将被自动归档
     */
    @Value("${actionow.agent.session.idle-archive-days:30}")
    private int idleArchiveDays;

    /**
     * 软删除保留天数，超过此天数的软删除会话将被物理清理
     */
    @Value("${actionow.agent.session.soft-delete-retention-days:90}")
    private int softDeleteRetentionDays;

    /**
     * 每次批量处理的数量
     */
    @Value("${actionow.agent.session.batch-size:100}")
    private int batchSize;

    /**
     * 归档空闲会话（需要租户上下文）
     * 将超过指定天数未活跃的会话自动归档
     *
     * @return 归档的会话数量
     */
    @Transactional
    public int archiveIdleSessions() {
        UserContext context = UserContextHolder.getContext();
        if (context == null || context.getTenantSchema() == null) {
            log.warn("Cannot archive sessions: tenant context not set");
            return 0;
        }

        String tenantSchema = context.getTenantSchema();
        log.debug("Starting idle session archive for tenant: {}", tenantSchema);

        try {
            LocalDateTime idleThreshold = LocalDateTime.now().minusDays(idleArchiveDays);
            LocalDateTime now = LocalDateTime.now();

            int archivedCount = sessionMapper.archiveIdleSessions(idleThreshold, now);

            if (archivedCount > 0) {
                log.info("Archived {} idle sessions for tenant: {} (threshold: {} days)",
                        archivedCount, tenantSchema, idleArchiveDays);
            } else {
                log.debug("No idle sessions to archive for tenant: {}", tenantSchema);
            }

            return archivedCount;

        } catch (Exception e) {
            log.error("Failed to archive idle sessions for tenant: {}", tenantSchema, e);
            return 0;
        }
    }

    /**
     * 清理已删除会话（需要租户上下文）
     * 物理删除超过保留期的软删除会话及其消息
     *
     * @return 清理的会话数量
     */
    @Transactional
    public int cleanupDeletedSessions() {
        UserContext context = UserContextHolder.getContext();
        if (context == null || context.getTenantSchema() == null) {
            log.warn("Cannot cleanup sessions: tenant context not set");
            return 0;
        }

        String tenantSchema = context.getTenantSchema();
        log.debug("Starting deleted session cleanup for tenant: {}", tenantSchema);

        try {
            LocalDateTime deleteThreshold = LocalDateTime.now().minusDays(softDeleteRetentionDays);
            List<AgentSessionEntity> sessionsToDelete = sessionMapper
                    .selectSessionsForPermanentDelete(deleteThreshold, batchSize);

            if (sessionsToDelete.isEmpty()) {
                log.debug("No deleted sessions to cleanup for tenant: {}", tenantSchema);
                return 0;
            }

            int deletedCount = 0;
            for (AgentSessionEntity session : sessionsToDelete) {
                try {
                    // 物理删除会话（消息由外键级联删除或单独清理）
                    sessionMapper.deleteById(session.getId());
                    deletedCount++;
                } catch (Exception e) {
                    log.error("Failed to permanently delete session: {}", session.getId(), e);
                }
            }

            log.info("Permanently deleted {} sessions for tenant: {} (retention: {} days)",
                    deletedCount, tenantSchema, softDeleteRetentionDays);

            return deletedCount;

        } catch (Exception e) {
            log.error("Failed to cleanup deleted sessions for tenant: {}", tenantSchema, e);
            return 0;
        }
    }

    /**
     * 执行完整的会话维护任务（需要租户上下文）
     * 包括：归档空闲会话、清理已删除会话
     *
     * @return 维护结果统计
     */
    @Transactional
    public MaintenanceResult runMaintenance() {
        int archivedCount = archiveIdleSessions();
        int cleanedCount = cleanupDeletedSessions();

        return new MaintenanceResult(archivedCount, cleanedCount);
    }

    /**
     * 维护结果
     */
    public record MaintenanceResult(int archivedCount, int cleanedCount) {
        public boolean hasChanges() {
            return archivedCount > 0 || cleanedCount > 0;
        }

        @Override
        public String toString() {
            return String.format("MaintenanceResult{archived=%d, cleaned=%d}", archivedCount, cleanedCount);
        }
    }
}
