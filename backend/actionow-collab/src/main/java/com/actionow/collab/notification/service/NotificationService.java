package com.actionow.collab.notification.service;

import com.actionow.collab.notification.dto.*;
import com.actionow.collab.notification.entity.Notification;
import com.actionow.collab.notification.entity.NotificationPreference;
import com.actionow.common.core.result.PageResult;

public interface NotificationService {

    void persist(Notification notification);

    PageResult<NotificationResponse> listByUser(String userId, String workspaceId, String type, Boolean isRead, Long pageNum, Long pageSize);

    UnreadCountResponse getUnreadCount(String userId);

    void markAsRead(String notificationId, String userId);

    void batchMarkAsRead(java.util.List<String> ids, String userId);

    void markAllAsRead(String userId);

    void delete(String notificationId, String userId);

    NotificationPreference getPreference(String userId, String workspaceId);

    void updatePreference(String userId, String workspaceId, NotificationPreferenceRequest request);
}
