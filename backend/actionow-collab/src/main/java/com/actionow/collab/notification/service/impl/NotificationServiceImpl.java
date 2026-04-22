package com.actionow.collab.notification.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.collab.constant.CollabConstants;
import com.actionow.collab.notification.dto.*;
import com.actionow.collab.notification.entity.Notification;
import com.actionow.collab.notification.entity.NotificationPreference;
import com.actionow.collab.notification.mapper.NotificationMapper;
import com.actionow.collab.notification.mapper.NotificationPreferenceMapper;
import com.actionow.collab.notification.service.NotificationService;
import com.actionow.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final NotificationPreferenceMapper preferenceMapper;
    private final StringRedisTemplate redisTemplate;

    private static final Duration UNREAD_KEY_TTL = Duration.ofDays(30);

    @Override
    public void persist(Notification notification) {
        notificationMapper.insert(notification);
    }

    @Override
    public PageResult<NotificationResponse> listByUser(String userId, String workspaceId,
                                                        String type, Boolean isRead,
                                                        Long pageNum, Long pageSize) {
        Page<Notification> page = new Page<>(pageNum, pageSize);
        notificationMapper.selectPageByUser(page, userId, workspaceId, type, isRead);

        List<NotificationResponse> records = page.getRecords().stream()
                .map(NotificationResponse::fromEntity)
                .toList();

        return PageResult.of(pageNum, pageSize, page.getTotal(), records);
    }

    @Override
    public UnreadCountResponse getUnreadCount(String userId) {
        // Try Redis first
        String unreadKey = CollabConstants.RedisKey.NOTIFY_UNREAD + userId;
        Map<Object, Object> cached = redisTemplate.opsForHash().entries(unreadKey);

        if (!cached.isEmpty()) {
            int total = Integer.parseInt(cached.getOrDefault("total", "0").toString());
            Map<String, Integer> byType = new HashMap<>();
            cached.forEach((k, v) -> {
                String key = k.toString();
                if (!"total".equals(key)) {
                    byType.put(key, Integer.parseInt(v.toString()));
                }
            });
            return UnreadCountResponse.builder().total(total).byType(byType).build();
        }

        // Fallback to DB
        int total = notificationMapper.countUnread(userId);
        List<Map<String, Object>> typeCounts = notificationMapper.countUnreadByType(userId);

        Map<String, Integer> byType = new HashMap<>();
        Map<String, String> redisData = new HashMap<>();
        redisData.put("total", String.valueOf(total));
        for (Map<String, Object> tc : typeCounts) {
            String t = tc.get("type").toString();
            int cnt = ((Number) tc.get("cnt")).intValue();
            byType.put(t, cnt);
            redisData.put(t, String.valueOf(cnt));
        }

        // Cache to Redis
        if (!redisData.isEmpty()) {
            redisTemplate.opsForHash().putAll(unreadKey, redisData);
            redisTemplate.expire(unreadKey, UNREAD_KEY_TTL);
        }

        return UnreadCountResponse.builder().total(total).byType(byType).build();
    }

    @Override
    public void markAsRead(String notificationId, String userId) {
        notificationMapper.markAsRead(notificationId, userId);
        // Recalculate Redis count
        refreshUnreadCount(userId);
    }

    @Override
    @Transactional
    public void batchMarkAsRead(List<String> ids, String userId) {
        for (String id : ids) {
            notificationMapper.markAsRead(id, userId);
        }
        refreshUnreadCount(userId);
    }

    @Override
    public void markAllAsRead(String userId) {
        notificationMapper.markAllAsRead(userId);
        String unreadKey = CollabConstants.RedisKey.NOTIFY_UNREAD + userId;
        redisTemplate.delete(unreadKey);
    }

    @Override
    public void delete(String notificationId, String userId) {
        Notification notification = notificationMapper.selectById(notificationId);
        if (notification != null && notification.getUserId().equals(userId)) {
            notificationMapper.deleteById(notificationId);
            if (!Boolean.TRUE.equals(notification.getIsRead())) {
                refreshUnreadCount(userId);
            }
        }
    }

    @Override
    public NotificationPreference getPreference(String userId, String workspaceId) {
        NotificationPreference pref = preferenceMapper.selectByUserAndWorkspace(userId, workspaceId);
        if (pref == null) {
            // Return defaults
            pref = NotificationPreference.builder()
                    .userId(userId)
                    .workspaceId(workspaceId)
                    .commentMention(true)
                    .commentReply(true)
                    .entityChange(true)
                    .reviewRequest(true)
                    .reviewResult(true)
                    .taskCompleted(true)
                    .systemAlert(true)
                    .build();
        }
        return pref;
    }

    @Override
    @Transactional
    public void updatePreference(String userId, String workspaceId, NotificationPreferenceRequest request) {
        NotificationPreference existing = preferenceMapper.selectByUserAndWorkspace(userId, workspaceId);

        if (existing == null) {
            existing = NotificationPreference.builder()
                    .userId(userId)
                    .workspaceId(workspaceId)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
        }

        if (request.getCommentMention() != null) existing.setCommentMention(request.getCommentMention());
        if (request.getCommentReply() != null) existing.setCommentReply(request.getCommentReply());
        if (request.getEntityChange() != null) existing.setEntityChange(request.getEntityChange());
        if (request.getReviewRequest() != null) existing.setReviewRequest(request.getReviewRequest());
        if (request.getReviewResult() != null) existing.setReviewResult(request.getReviewResult());
        if (request.getTaskCompleted() != null) existing.setTaskCompleted(request.getTaskCompleted());
        if (request.getSystemAlert() != null) existing.setSystemAlert(request.getSystemAlert());
        if (request.getQuietStart() != null) existing.setQuietStart(request.getQuietStart());
        if (request.getQuietEnd() != null) existing.setQuietEnd(request.getQuietEnd());
        existing.setUpdatedAt(LocalDateTime.now());

        if (existing.getId() == null) {
            preferenceMapper.insert(existing);
        } else {
            preferenceMapper.updateById(existing);
        }
    }

    private void refreshUnreadCount(String userId) {
        String unreadKey = CollabConstants.RedisKey.NOTIFY_UNREAD + userId;
        redisTemplate.delete(unreadKey);
        // Next call to getUnreadCount will repopulate from DB
    }

    private static final int CLEANUP_BATCH_SIZE = 1000;

    /**
     * 定时清理过期通知和已读旧通知
     * 每天凌晨 3:17 执行（避开整点）
     * 分批删除避免长时间锁表
     */
    @Scheduled(cron = "0 17 3 * * ?")
    public void cleanupExpiredNotifications() {
        try {
            int expiredTotal = deleteBatched(() -> notificationMapper.deleteExpiredBatch(CLEANUP_BATCH_SIZE));
            int readTotal = deleteBatched(() -> notificationMapper.deleteReadBeforeBatch(
                    LocalDateTime.now().minusDays(90), CLEANUP_BATCH_SIZE));
            if (expiredTotal > 0 || readTotal > 0) {
                log.info("Notification cleanup: deleted {} expired, {} old read notifications",
                        expiredTotal, readTotal);
            }
        } catch (Exception e) {
            log.error("Failed to cleanup expired notifications", e);
        }
    }

    private int deleteBatched(java.util.function.IntSupplier batchDelete) {
        int total = 0;
        int deleted;
        do {
            deleted = batchDelete.getAsInt();
            total += deleted;
        } while (deleted == CLEANUP_BATCH_SIZE);
        return total;
    }
}
