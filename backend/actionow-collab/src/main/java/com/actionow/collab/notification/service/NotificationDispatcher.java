package com.actionow.collab.notification.service;

import com.actionow.collab.comment.dto.CommentMention;
import com.actionow.collab.comment.entity.Comment;
import com.actionow.collab.comment.mapper.CommentMapper;
import com.actionow.collab.constant.CollabConstants;
import com.actionow.collab.notification.entity.Notification;
import com.actionow.collab.notification.entity.NotificationPreference;
import com.actionow.collab.notification.mapper.NotificationMapper;
import com.actionow.collab.notification.mapper.NotificationPreferenceMapper;
import com.actionow.collab.watch.mapper.EntityWatchMapper;
import com.actionow.collab.watch.entity.EntityWatch;
import com.actionow.collab.websocket.CollaborationHub;
import com.actionow.collab.dto.message.OutboundMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final NotificationMapper notificationMapper;
    private final NotificationPreferenceMapper preferenceMapper;
    private final EntityWatchMapper entityWatchMapper;
    private final CommentMapper commentMapper;
    private final CollaborationHub collaborationHub;
    private final StringRedisTemplate redisTemplate;

    private static final Duration UNREAD_KEY_TTL = Duration.ofDays(30);

    @Async("notificationExecutor")
    public void dispatchCommentNotification(Comment comment, List<CommentMention> mentions,
                                             String workspaceId, String authorId, String authorName) {
        try {
            // 使用 LinkedHashSet 保持插入顺序，优先处理被@用户
            Set<String> targetUserIds = new LinkedHashSet<>();
            // 单独跟踪父评论作者，用于区分通知标题
            String parentAuthorId = null;

            // 1. 被@用户
            if (mentions != null) {
                mentions.stream()
                        .filter(m -> "USER".equals(m.getType()))
                        .map(CommentMention::getId)
                        .forEach(targetUserIds::add);
            }

            // 2. 父评论作者（回复场景）
            if (comment.getParentId() != null) {
                Comment parent = commentMapper.selectById(comment.getParentId());
                if (parent != null && parent.getCreatedBy() != null) {
                    parentAuthorId = parent.getCreatedBy();
                    targetUserIds.add(parentAuthorId);
                }
            }

            // 3. 实体关注者
            List<EntityWatch> watchers = entityWatchMapper.selectByEntity(comment.getTargetType(), comment.getTargetId());
            watchers.stream()
                    .map(EntityWatch::getUserId)
                    .forEach(targetUserIds::add);

            // 4. 移除评论作者本人
            targetUserIds.remove(authorId);

            if (targetUserIds.isEmpty()) return;

            String contentPreview = comment.getContent().length() > 100
                    ? comment.getContent().substring(0, 100) + "..."
                    : comment.getContent();

            // 5. 逐用户检查偏好并发送通知
            for (String userId : targetUserIds) {
                boolean isMentioned = mentions != null && mentions.stream()
                        .anyMatch(m -> "USER".equals(m.getType()) && userId.equals(m.getId()));
                boolean isDirectReplyTarget = userId.equals(parentAuthorId) && !isMentioned;

                String notifType = isMentioned ? "COMMENT_MENTION" : "COMMENT_REPLY";

                // 检查用户偏好
                NotificationPreference pref = preferenceMapper.selectByUserAndWorkspace(userId, workspaceId);
                if (pref != null) {
                    if (isMentioned && !Boolean.TRUE.equals(pref.getCommentMention())) continue;
                    if (!isMentioned && !Boolean.TRUE.equals(pref.getCommentReply())) continue;
                    // 免打扰时段检查
                    if (isInQuietHours(pref)) continue;
                }

                // 根据用户与评论的关系使用不同标题
                String title;
                if (isMentioned) {
                    title = authorName + " 在评论中提到了你";
                } else if (isDirectReplyTarget) {
                    title = authorName + " 回复了你的评论";
                } else {
                    title = authorName + " 在你关注的内容下发表了评论";
                }

                Notification notification = Notification.builder()
                        .workspaceId(workspaceId)
                        .userId(userId)
                        .type(notifType)
                        .title(title)
                        .content(contentPreview)
                        .payload(Map.of(
                                "commentId", comment.getId(),
                                "targetType", comment.getTargetType(),
                                "targetId", comment.getTargetId()
                        ))
                        .entityType("COMMENT")
                        .entityId(comment.getId())
                        .senderId(authorId)
                        .senderName(authorName)
                        .isRead(false)
                        .priority(isMentioned ? 3 : 2)
                        .createdAt(LocalDateTime.now())
                        .build();

                notificationMapper.insert(notification);

                // 更新 Redis 未读计数
                String unreadKey = CollabConstants.RedisKey.NOTIFY_UNREAD + userId;
                long newTotal = redisTemplate.opsForHash().increment(unreadKey, "total", 1);
                redisTemplate.opsForHash().increment(unreadKey, notifType, 1);
                redisTemplate.expire(unreadKey, UNREAD_KEY_TTL);

                // WebSocket 推送：通知 + 计数（合并 delta，避免两次推送）
                collaborationHub.sendToUser(workspaceId, userId,
                        OutboundMessage.of(CollabConstants.MessageType.NOTIFICATION, Map.of(
                                "id", notification.getId(),
                                "type", notifType,
                                "title", title,
                                "unreadTotal", newTotal,
                                "sender", Map.of("id", authorId, "name", authorName),
                                "payload", notification.getPayload()
                        )));
            }

            log.debug("Dispatched comment notifications to {} users for comment {}",
                    targetUserIds.size(), comment.getId());
        } catch (Exception e) {
            log.error("Failed to dispatch comment notifications for comment {}", comment.getId(), e);
        }
    }

    @Async("notificationExecutor")
    public void dispatchReviewNotification(String workspaceId, String targetUserId,
                                            String title, String content,
                                            String entityType, String entityId,
                                            String senderId, String senderName,
                                            String notifType, Map<String, Object> payload) {
        try {
            // 检查用户偏好（审核通知受 reviewRequest / reviewResult 和免打扰控制）
            NotificationPreference pref = preferenceMapper.selectByUserAndWorkspace(targetUserId, workspaceId);
            if (pref != null) {
                boolean isRequest = "REVIEW_REQUEST".equals(notifType);
                if (isRequest && !Boolean.TRUE.equals(pref.getReviewRequest())) return;
                if (!isRequest && !Boolean.TRUE.equals(pref.getReviewResult())) return;
                if (isInQuietHours(pref)) return;
            }

            Notification notification = Notification.builder()
                    .workspaceId(workspaceId)
                    .userId(targetUserId)
                    .type(notifType)
                    .title(title)
                    .content(content)
                    .payload(payload)
                    .entityType(entityType)
                    .entityId(entityId)
                    .senderId(senderId)
                    .senderName(senderName)
                    .isRead(false)
                    .priority(3)
                    .createdAt(LocalDateTime.now())
                    .build();

            notificationMapper.insert(notification);

            // 更新 Redis 未读计数（用 increment 返回值，避免额外 GET）
            String unreadKey = CollabConstants.RedisKey.NOTIFY_UNREAD + targetUserId;
            long newTotal = redisTemplate.opsForHash().increment(unreadKey, "total", 1);
            redisTemplate.opsForHash().increment(unreadKey, notifType, 1);
            redisTemplate.expire(unreadKey, UNREAD_KEY_TTL);

            // WebSocket 推送：通知 + 计数合并
            collaborationHub.sendToUser(workspaceId, targetUserId,
                    OutboundMessage.of(CollabConstants.MessageType.NOTIFICATION, Map.of(
                            "id", notification.getId(),
                            "type", notifType,
                            "title", title,
                            "unreadTotal", newTotal,
                            "sender", Map.of("id", senderId, "name", senderName),
                            "payload", payload != null ? payload : Map.of()
                    )));
        } catch (Exception e) {
            log.error("Failed to dispatch review notification to user {}", targetUserId, e);
        }
    }

    /**
     * 判断当前时间是否处于免打扰时段
     * 支持跨午夜区间：如 22:00 - 07:00
     */
    private boolean isInQuietHours(NotificationPreference pref) {
        if (pref.getQuietStart() == null || pref.getQuietEnd() == null) {
            return false;
        }
        LocalTime now = LocalTime.now();
        LocalTime start = pref.getQuietStart();
        LocalTime end = pref.getQuietEnd();

        if (!start.isAfter(end)) {
            // 同日区间，如 13:00-14:00
            return !now.isBefore(start) && !now.isAfter(end);
        } else {
            // 跨午夜区间，如 22:00-07:00
            return !now.isBefore(start) || !now.isAfter(end);
        }
    }
}
