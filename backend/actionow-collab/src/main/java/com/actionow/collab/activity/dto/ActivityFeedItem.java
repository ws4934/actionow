package com.actionow.collab.activity.dto;

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
public class ActivityFeedItem {

    private String type;
    private String action;
    private ActorInfo actor;
    private String entityType;
    private String entityId;
    private String summary;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActorInfo {
        private String id;
        private String nickname;
        private String avatar;
    }
}
