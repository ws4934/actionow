package com.actionow.collab.manager;

import com.actionow.collab.constant.CollabConstants;
import com.actionow.collab.dto.Collaborator;
import com.actionow.collab.dto.EntityCollaboration;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 实体协作管理器
 * 管理实体的查看者和编辑者状态
 * 优化版：使用 Hash 存储查看者，避免 JSON 序列化开销
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EntityCollaborationManager {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // 查看者数据分隔符
    private static final String VIEWER_DATA_SEPARATOR = "|";

    /**
     * 添加查看者 - 优化版
     * 使用 Hash 存储，field=userId, value=简化数据（避免 JSON）
     */
    public void addViewer(String entityType, String entityId, Collaborator viewer) {
        String key = CollabConstants.RedisKey.ENTITY_VIEWERS + entityType + ":" + entityId;

        // 使用分隔符拼接数据，避免 JSON 序列化
        String viewerData = String.join(VIEWER_DATA_SEPARATOR,
                viewer.getNickname() != null ? viewer.getNickname() : "",
                viewer.getAvatar() != null ? viewer.getAvatar() : "",
                viewer.getStatus() != null ? viewer.getStatus() : CollabConstants.CollabStatus.VIEWING,
                viewer.getJoinedAt() != null ? viewer.getJoinedAt().toString() : LocalDateTime.now().toString()
        );

        redisTemplate.opsForHash().put(key, viewer.getUserId(), viewerData);
        redisTemplate.expire(key, Duration.ofSeconds(CollabConstants.Config.PRESENCE_TTL_SECONDS));

        log.debug("Added viewer {} to entity {}:{}", viewer.getUserId(), entityType, entityId);
    }

    /**
     * 移除查看者 - 优化版
     * O(1) 操作，直接通过 userId 删除
     */
    public void removeViewer(String entityType, String entityId, String userId) {
        String key = CollabConstants.RedisKey.ENTITY_VIEWERS + entityType + ":" + entityId;
        redisTemplate.opsForHash().delete(key, userId);
        log.debug("Removed viewer {} from entity {}:{}", userId, entityType, entityId);
    }

    /**
     * 清除用户在所有实体上的查看状态
     */
    public void clearUserViewerStatus(String entityType, String entityId, String userId) {
        removeViewer(entityType, entityId, userId);
    }

    /**
     * 尝试获取编辑锁
     *
     * @return true 如果成功获取锁
     */
    public boolean tryAcquireEditLock(String entityType, String entityId, Collaborator editor) {
        String key = CollabConstants.RedisKey.ENTITY_EDITOR + entityType + ":" + entityId;
        try {
            String editorJson = objectMapper.writeValueAsString(editor);
            Boolean success = redisTemplate.opsForValue().setIfAbsent(
                    key, editorJson,
                    Duration.ofSeconds(CollabConstants.Config.EDIT_LOCK_TTL_SECONDS)
            );
            if (Boolean.TRUE.equals(success)) {
                log.debug("User {} acquired edit lock for entity {}:{}", editor.getUserId(), entityType, entityId);
                return true;
            }
            return false;
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize editor", e);
            return false;
        }
    }

    /**
     * 释放编辑锁
     */
    public void releaseEditLock(String entityType, String entityId, String userId) {
        String key = CollabConstants.RedisKey.ENTITY_EDITOR + entityType + ":" + entityId;
        String editorJson = redisTemplate.opsForValue().get(key);
        if (editorJson != null) {
            try {
                Collaborator editor = objectMapper.readValue(editorJson, Collaborator.class);
                if (editor.getUserId().equals(userId)) {
                    redisTemplate.delete(key);
                    log.debug("User {} released edit lock for entity {}:{}", userId, entityType, entityId);
                }
            } catch (JsonProcessingException e) {
                log.error("Failed to parse editor", e);
            }
        }
    }

    /**
     * 续期编辑锁
     */
    public void renewEditLock(String entityType, String entityId, String userId) {
        String key = CollabConstants.RedisKey.ENTITY_EDITOR + entityType + ":" + entityId;
        String editorJson = redisTemplate.opsForValue().get(key);
        if (editorJson != null) {
            try {
                Collaborator editor = objectMapper.readValue(editorJson, Collaborator.class);
                if (editor.getUserId().equals(userId)) {
                    redisTemplate.expire(key, Duration.ofSeconds(CollabConstants.Config.EDIT_LOCK_TTL_SECONDS));
                }
            } catch (JsonProcessingException e) {
                log.error("Failed to parse editor", e);
            }
        }
    }

    /**
     * 获取实体的当前编辑者
     */
    public Collaborator getCurrentEditor(String entityType, String entityId) {
        String key = CollabConstants.RedisKey.ENTITY_EDITOR + entityType + ":" + entityId;
        String editorJson = redisTemplate.opsForValue().get(key);
        if (editorJson != null) {
            try {
                return objectMapper.readValue(editorJson, Collaborator.class);
            } catch (JsonProcessingException e) {
                log.error("Failed to parse editor", e);
            }
        }
        return null;
    }

    /**
     * 获取实体的所有查看者 - 优化版
     * 从 Hash 中读取并解析
     */
    public List<Collaborator> getViewers(String entityType, String entityId) {
        String key = CollabConstants.RedisKey.ENTITY_VIEWERS + entityType + ":" + entityId;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);

        if (entries.isEmpty()) {
            return List.of();
        }

        return entries.entrySet().stream()
                .map(entry -> parseViewer((String) entry.getKey(), (String) entry.getValue()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 从分隔符数据解析查看者
     */
    private Collaborator parseViewer(String userId, String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        try {
            String[] parts = data.split("\\" + VIEWER_DATA_SEPARATOR, 4);
            return Collaborator.builder()
                    .userId(userId)
                    .nickname(parts.length > 0 ? parts[0] : "")
                    .avatar(parts.length > 1 ? parts[1] : "")
                    .status(parts.length > 2 ? parts[2] : CollabConstants.CollabStatus.VIEWING)
                    .joinedAt(parts.length > 3 ? LocalDateTime.parse(parts[3]) : LocalDateTime.now())
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse viewer data for userId {}: {}", userId, data);
            return null;
        }
    }

    /**
     * 快速获取查看者数量（不需要获取完整数据）
     */
    public long getViewerCount(String entityType, String entityId) {
        String key = CollabConstants.RedisKey.ENTITY_VIEWERS + entityType + ":" + entityId;
        Long size = redisTemplate.opsForHash().size(key);
        return size != null ? size : 0;
    }

    /**
     * 获取实体协作状态
     */
    public EntityCollaboration getEntityCollaboration(String entityType, String entityId) {
        List<Collaborator> viewers = getViewers(entityType, entityId);
        Collaborator editor = getCurrentEditor(entityType, entityId);

        return EntityCollaboration.builder()
                .entityType(entityType)
                .entityId(entityId)
                .viewers(viewers)
                .editor(editor)
                .build();
    }

    /**
     * 批量获取实体协作状态
     */
    public Map<String, EntityCollaboration> batchGetEntityCollaboration(String entityType, List<String> entityIds) {
        Map<String, EntityCollaboration> result = new HashMap<>();
        for (String entityId : entityIds) {
            result.put(entityId, getEntityCollaboration(entityType, entityId));
        }
        return result;
    }

    /**
     * 检查实体是否被编辑
     */
    public boolean isEntityBeingEdited(String entityType, String entityId) {
        String key = CollabConstants.RedisKey.ENTITY_EDITOR + entityType + ":" + entityId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 检查实体是否被指定用户编辑
     */
    public boolean isEntityBeingEditedBy(String entityType, String entityId, String userId) {
        Collaborator editor = getCurrentEditor(entityType, entityId);
        return editor != null && editor.getUserId().equals(userId);
    }

    /**
     * 创建协作者对象
     */
    public Collaborator createCollaborator(String userId, String nickname, String avatar, String status) {
        return Collaborator.builder()
                .userId(userId)
                .nickname(nickname)
                .avatar(avatar)
                .status(status)
                .joinedAt(LocalDateTime.now())
                .build();
    }
}
