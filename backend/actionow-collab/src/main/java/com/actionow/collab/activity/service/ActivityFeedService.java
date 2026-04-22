package com.actionow.collab.activity.service;

import com.actionow.collab.activity.dto.ActivityFeedItem;
import com.actionow.common.core.result.PageResult;

public interface ActivityFeedService {

    PageResult<ActivityFeedItem> getEntityActivities(String entityType, String entityId, Long pageNum, Long pageSize);

    PageResult<ActivityFeedItem> getScriptActivities(String scriptId, Long pageNum, Long pageSize);
}
