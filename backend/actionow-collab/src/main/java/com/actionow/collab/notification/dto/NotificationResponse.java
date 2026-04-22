package com.actionow.collab.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    private String id;
    private String workspaceId;
    private String type;
    private String title;
    private String content;
    private Map<String, Object> payload;
    private String entityType;
    private String entityId;
    private String senderId;
    private String senderName;
    private boolean read;
    private LocalDateTime readAt;
    private Integer priority;
    private LocalDateTime createdAt;

    public static NotificationResponse fromEntity(com.actionow.collab.notification.entity.Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .workspaceId(n.getWorkspaceId())
                .type(n.getType())
                .title(n.getTitle())
                .content(n.getContent())
                .payload(n.getPayload())
                .entityType(n.getEntityType())
                .entityId(n.getEntityId())
                .senderId(n.getSenderId())
                .senderName(n.getSenderName())
                .read(Boolean.TRUE.equals(n.getIsRead()))
                .readAt(n.getReadAt())
                .priority(n.getPriority())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
