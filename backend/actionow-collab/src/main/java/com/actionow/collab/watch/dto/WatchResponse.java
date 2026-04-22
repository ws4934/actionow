package com.actionow.collab.watch.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchResponse {
    private boolean watching;
    private String watchType;
    private int watcherCount;
    private List<WatcherInfo> watchers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WatcherInfo {
        private String userId;
        private LocalDateTime watchedAt;
    }
}
