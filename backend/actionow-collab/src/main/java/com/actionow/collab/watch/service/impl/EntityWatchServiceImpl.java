package com.actionow.collab.watch.service.impl;

import com.actionow.collab.constant.CollabConstants;
import com.actionow.collab.watch.dto.WatchResponse;
import com.actionow.collab.watch.entity.EntityWatch;
import com.actionow.collab.watch.mapper.EntityWatchMapper;
import com.actionow.collab.watch.service.EntityWatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EntityWatchServiceImpl implements EntityWatchService {

    private final EntityWatchMapper entityWatchMapper;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void watch(String entityType, String entityId, String workspaceId, String userId) {
        EntityWatch existing = entityWatchMapper.selectByUserAndEntity(userId, entityType, entityId);
        if (existing != null) return;

        EntityWatch watch = EntityWatch.builder()
                .workspaceId(workspaceId)
                .userId(userId)
                .entityType(entityType)
                .entityId(entityId)
                .watchType("ALL")
                .createdAt(LocalDateTime.now())
                .build();
        entityWatchMapper.insert(watch);

        // Update Redis cache
        String cacheKey = CollabConstants.RedisKey.WATCH + entityType + ":" + entityId;
        redisTemplate.opsForSet().add(cacheKey, userId);
        redisTemplate.expire(cacheKey, 2, TimeUnit.HOURS);
    }

    @Override
    public void unwatch(String entityType, String entityId, String userId) {
        EntityWatch existing = entityWatchMapper.selectByUserAndEntity(userId, entityType, entityId);
        if (existing == null) return;

        entityWatchMapper.deleteById(existing.getId());

        String cacheKey = CollabConstants.RedisKey.WATCH + entityType + ":" + entityId;
        redisTemplate.opsForSet().remove(cacheKey, userId);
    }

    @Override
    public WatchResponse getWatchStatus(String entityType, String entityId, String userId) {
        EntityWatch myWatch = entityWatchMapper.selectByUserAndEntity(userId, entityType, entityId);
        List<EntityWatch> allWatchers = entityWatchMapper.selectByEntity(entityType, entityId);

        List<WatchResponse.WatcherInfo> watcherInfos = allWatchers.stream()
                .map(w -> WatchResponse.WatcherInfo.builder()
                        .userId(w.getUserId())
                        .watchedAt(w.getCreatedAt())
                        .build())
                .toList();

        return WatchResponse.builder()
                .watching(myWatch != null)
                .watchType(myWatch != null ? myWatch.getWatchType() : null)
                .watcherCount(allWatchers.size())
                .watchers(watcherInfos)
                .build();
    }

    @Override
    public List<EntityWatch> getMyWatches(String userId) {
        return entityWatchMapper.selectByUser(userId);
    }

    @Override
    public List<String> getWatcherUserIds(String entityType, String entityId) {
        String cacheKey = CollabConstants.RedisKey.WATCH + entityType + ":" + entityId;
        Set<String> cached = redisTemplate.opsForSet().members(cacheKey);

        if (cached != null && !cached.isEmpty()) {
            return List.copyOf(cached);
        }

        // Fallback to DB and populate cache
        List<EntityWatch> watchers = entityWatchMapper.selectByEntity(entityType, entityId);
        List<String> userIds = watchers.stream()
                .map(EntityWatch::getUserId)
                .collect(Collectors.toList());

        if (!userIds.isEmpty()) {
            redisTemplate.opsForSet().add(cacheKey, userIds.toArray(new String[0]));
            redisTemplate.expire(cacheKey, 2, TimeUnit.HOURS);
        }

        return userIds;
    }
}
