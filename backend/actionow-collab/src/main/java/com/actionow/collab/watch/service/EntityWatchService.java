package com.actionow.collab.watch.service;

import com.actionow.collab.watch.dto.WatchResponse;
import com.actionow.collab.watch.entity.EntityWatch;

import java.util.List;

public interface EntityWatchService {

    void watch(String entityType, String entityId, String workspaceId, String userId);

    void unwatch(String entityType, String entityId, String userId);

    WatchResponse getWatchStatus(String entityType, String entityId, String userId);

    List<EntityWatch> getMyWatches(String userId);

    List<String> getWatcherUserIds(String entityType, String entityId);
}
