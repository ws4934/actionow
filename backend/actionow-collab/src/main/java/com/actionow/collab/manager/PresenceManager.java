package com.actionow.collab.manager;

import com.actionow.collab.constant.CollabConstants;
import com.actionow.collab.dto.UserPresence;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 在线状态管理器
 * 使用 Redis 管理用户在线状态
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceManager {

    private final StringRedisTemplate redisTemplate;

    /**
     * 用户上线
     */
    public void userOnline(String workspaceId, String userId, String nickname, String avatar) {
        String presenceKey = CollabConstants.RedisKey.PRESENCE + workspaceId + ":" + userId;
        String workspaceUsersKey = CollabConstants.RedisKey.WORKSPACE_USERS + workspaceId;

        Map<String, String> presence = new HashMap<>();
        presence.put("userId", userId);
        presence.put("nickname", nickname != null ? nickname : "");
        presence.put("avatar", avatar != null ? avatar : "");
        presence.put("status", CollabConstants.Status.ONLINE);
        presence.put("lastActiveAt", LocalDateTime.now().toString());

        redisTemplate.opsForHash().putAll(presenceKey, presence);
        redisTemplate.expire(presenceKey, Duration.ofSeconds(CollabConstants.Config.PRESENCE_TTL_SECONDS));

        redisTemplate.opsForSet().add(workspaceUsersKey, userId);
        redisTemplate.expire(workspaceUsersKey, Duration.ofHours(24));

        log.debug("User {} online in workspace {}", userId, workspaceId);
    }

    /**
     * 用户下线
     */
    public void userOffline(String workspaceId, String userId) {
        String presenceKey = CollabConstants.RedisKey.PRESENCE + workspaceId + ":" + userId;
        String workspaceUsersKey = CollabConstants.RedisKey.WORKSPACE_USERS + workspaceId;

        redisTemplate.delete(presenceKey);
        redisTemplate.opsForSet().remove(workspaceUsersKey, userId);

        log.debug("User {} offline in workspace {}", userId, workspaceId);
    }

    /**
     * 心跳续期
     */
    public void heartbeat(String workspaceId, String userId) {
        String presenceKey = CollabConstants.RedisKey.PRESENCE + workspaceId + ":" + userId;

        if (Boolean.TRUE.equals(redisTemplate.hasKey(presenceKey))) {
            redisTemplate.opsForHash().put(presenceKey, "lastActiveAt", LocalDateTime.now().toString());
            redisTemplate.opsForHash().put(presenceKey, "status", CollabConstants.Status.ONLINE);
            redisTemplate.expire(presenceKey, Duration.ofSeconds(CollabConstants.Config.PRESENCE_TTL_SECONDS));
        }
    }

    /**
     * 进入剧本
     */
    public void enterScript(String workspaceId, String userId, String scriptId) {
        String scriptUsersKey = CollabConstants.RedisKey.SCRIPT_USERS + scriptId;

        redisTemplate.opsForSet().add(scriptUsersKey, userId);
        redisTemplate.expire(scriptUsersKey, Duration.ofHours(24));

        log.debug("User {} entered script {} in workspace {}", userId, scriptId, workspaceId);
    }

    /**
     * 离开剧本
     */
    public void leaveScript(String workspaceId, String userId, String scriptId) {
        String scriptUsersKey = CollabConstants.RedisKey.SCRIPT_USERS + scriptId;

        redisTemplate.opsForSet().remove(scriptUsersKey, userId);

        log.debug("User {} left script {} in workspace {}", userId, scriptId, workspaceId);
    }

    /**
     * 获取工作空间在线用户
     * 使用 Redis Pipeline 批量获取，避免 N+1 问题
     */
    public List<UserPresence> getWorkspaceOnlineUsers(String workspaceId) {
        String workspaceUsersKey = CollabConstants.RedisKey.WORKSPACE_USERS + workspaceId;
        Set<String> userIds = redisTemplate.opsForSet().members(workspaceUsersKey);

        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }

        // 保持顺序以便后续匹配
        List<String> userIdList = new ArrayList<>(userIds);

        // Pipeline 批量获取所有用户状态
        List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection stringConn = (StringRedisConnection) connection;
            for (String userId : userIdList) {
                String presenceKey = CollabConstants.RedisKey.PRESENCE + workspaceId + ":" + userId;
                stringConn.hGetAll(presenceKey);
            }
            return null;
        });

        List<UserPresence> presences = new ArrayList<>();
        for (int i = 0; i < userIdList.size(); i++) {
            Object result = results.get(i);
            if (result instanceof Map<?, ?> data && !data.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, String> stringData = (Map<String, String>) data;
                UserPresence presence = buildUserPresence(stringData);
                if (presence != null) {
                    presences.add(presence);
                }
            }
        }

        return presences;
    }

    /**
     * 从 Map 构建 UserPresence 对象
     */
    private UserPresence buildUserPresence(Map<String, String> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        return UserPresence.builder()
                .userId(data.get("userId"))
                .nickname(data.get("nickname"))
                .avatar(data.get("avatar"))
                .status(data.get("status"))
                .lastActiveAt(parseDateTime(data.get("lastActiveAt")))
                .build();
    }

    /**
     * 获取剧本在线用户ID列表
     */
    public Set<String> getScriptOnlineUserIds(String scriptId) {
        String scriptUsersKey = CollabConstants.RedisKey.SCRIPT_USERS + scriptId;
        Set<String> userIds = redisTemplate.opsForSet().members(scriptUsersKey);
        return userIds != null ? userIds : Set.of();
    }

    /**
     * 获取单个用户的在线状态
     */
    public UserPresence getUserPresence(String workspaceId, String userId) {
        String presenceKey = CollabConstants.RedisKey.PRESENCE + workspaceId + ":" + userId;
        Map<Object, Object> data = redisTemplate.opsForHash().entries(presenceKey);

        if (data.isEmpty()) {
            return null;
        }

        return UserPresence.builder()
                .userId((String) data.get("userId"))
                .nickname((String) data.get("nickname"))
                .avatar((String) data.get("avatar"))
                .status((String) data.get("status"))
                .lastActiveAt(parseDateTime((String) data.get("lastActiveAt")))
                .build();
    }

    /**
     * 检查用户是否在线
     */
    public boolean isUserOnline(String workspaceId, String userId) {
        String presenceKey = CollabConstants.RedisKey.PRESENCE + workspaceId + ":" + userId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(presenceKey));
    }

    /**
     * 检查用户是否在剧本中
     */
    public boolean isUserInScript(String scriptId, String userId) {
        String scriptUsersKey = CollabConstants.RedisKey.SCRIPT_USERS + scriptId;
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(scriptUsersKey, userId));
    }

    /**
     * 获取工作空间在线用户数
     */
    public long getWorkspaceOnlineCount(String workspaceId) {
        String workspaceUsersKey = CollabConstants.RedisKey.WORKSPACE_USERS + workspaceId;
        Long count = redisTemplate.opsForSet().size(workspaceUsersKey);
        return count != null ? count : 0;
    }

    /**
     * 获取剧本在线用户数
     */
    public long getScriptOnlineCount(String scriptId) {
        String scriptUsersKey = CollabConstants.RedisKey.SCRIPT_USERS + scriptId;
        Long count = redisTemplate.opsForSet().size(scriptUsersKey);
        return count != null ? count : 0;
    }

    /**
     * 批量心跳续期
     * 使用 Pipeline 批量更新多个用户的在线状态
     */
    public void batchHeartbeat(String workspaceId, List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }

        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection stringConn = (StringRedisConnection) connection;
            String now = LocalDateTime.now().toString();

            for (String userId : userIds) {
                String presenceKey = CollabConstants.RedisKey.PRESENCE + workspaceId + ":" + userId;
                stringConn.hSet(presenceKey, "lastActiveAt", now);
                stringConn.hSet(presenceKey, "status", CollabConstants.Status.ONLINE);
                stringConn.expire(presenceKey, CollabConstants.Config.PRESENCE_TTL_SECONDS);
            }
            return null;
        });

        log.debug("Batch heartbeat for {} users in workspace {}", userIds.size(), workspaceId);
    }

    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(dateTimeStr);
        } catch (Exception e) {
            return null;
        }
    }
}
