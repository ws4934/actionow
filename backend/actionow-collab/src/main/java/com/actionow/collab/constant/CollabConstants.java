package com.actionow.collab.constant;

/**
 * 协作服务常量
 *
 * @author Actionow
 */
public final class CollabConstants {

    private CollabConstants() {}

    /**
     * 页面类型
     */
    public static final class Page {
        public static final String SCRIPT_LIST = "SCRIPT_LIST";
        public static final String SCRIPT_DETAIL = "SCRIPT_DETAIL";

        private Page() {}
    }

    /**
     * Tab类型
     */
    public static final class Tab {
        public static final String DETAIL = "DETAIL";
        public static final String EPISODES = "EPISODES";
        public static final String STORYBOARDS = "STORYBOARDS";
        public static final String CHARACTERS = "CHARACTERS";
        public static final String SCENES = "SCENES";
        public static final String PROPS = "PROPS";
        public static final String ASSETS = "ASSETS";
        public static final String STYLES = "STYLES";

        private Tab() {}
    }

    /**
     * 实体类型
     */
    public static final class EntityType {
        public static final String SCRIPT = "SCRIPT";
        public static final String EPISODE = "EPISODE";
        public static final String STORYBOARD = "STORYBOARD";
        public static final String CHARACTER = "CHARACTER";
        public static final String SCENE = "SCENE";
        public static final String PROP = "PROP";
        public static final String STYLE = "STYLE";
        public static final String ASSET = "ASSET";

        private EntityType() {}
    }

    /**
     * 在线状态
     */
    public static final class Status {
        public static final String ONLINE = "ONLINE";
        public static final String AWAY = "AWAY";
        public static final String OFFLINE = "OFFLINE";

        private Status() {}
    }

    /**
     * 协作状态
     */
    public static final class CollabStatus {
        public static final String VIEWING = "VIEWING";
        public static final String EDITING = "EDITING";

        private CollabStatus() {}
    }

    /**
     * WebSocket 消息类型
     */
    public static final class MessageType {
        // Client -> Server
        public static final String PING = "PING";
        public static final String ENTER_WORKSPACE = "ENTER_WORKSPACE";
        public static final String LEAVE_WORKSPACE = "LEAVE_WORKSPACE";
        public static final String ENTER_SCRIPT = "ENTER_SCRIPT";
        public static final String LEAVE_SCRIPT = "LEAVE_SCRIPT";
        public static final String SWITCH_TAB = "SWITCH_TAB";
        public static final String FOCUS_ENTITY = "FOCUS_ENTITY";
        public static final String BLUR_ENTITY = "BLUR_ENTITY";
        public static final String START_EDITING = "START_EDITING";
        public static final String STOP_EDITING = "STOP_EDITING";
        public static final String HEARTBEAT = "HEARTBEAT";

        // Server -> Client
        public static final String PONG = "PONG";
        public static final String CONNECTED = "CONNECTED";
        public static final String ERROR = "ERROR";
        public static final String USER_JOINED = "USER_JOINED";
        public static final String USER_LEFT = "USER_LEFT";
        public static final String USER_LOCATION_CHANGED = "USER_LOCATION_CHANGED";
        public static final String SCRIPT_COLLABORATION = "SCRIPT_COLLABORATION";
        public static final String ENTITY_COLLABORATION = "ENTITY_COLLABORATION";
        public static final String ENTITY_UPDATED = "ENTITY_UPDATED";
        public static final String EDITING_LOCKED = "EDITING_LOCKED";
        public static final String EDITING_UNLOCKED = "EDITING_UNLOCKED";
        // Agent 活动消息
        public static final String AGENT_ACTIVITY = "AGENT_ACTIVITY";

        // 评论消息
        public static final String COMMENT_CREATED = "COMMENT_CREATED";
        public static final String COMMENT_UPDATED = "COMMENT_UPDATED";
        public static final String COMMENT_DELETED = "COMMENT_DELETED";
        public static final String COMMENT_RESOLVED = "COMMENT_RESOLVED";
        public static final String COMMENT_REOPENED = "COMMENT_REOPENED";
        public static final String COMMENT_REACTION = "COMMENT_REACTION";

        // 通知消息
        public static final String NOTIFICATION = "NOTIFICATION";
        public static final String NOTIFICATION_COUNT = "NOTIFICATION_COUNT";

        // 审核消息
        public static final String REVIEW_STATUS_CHANGED = "REVIEW_STATUS_CHANGED";

        private MessageType() {}
    }

    /**
     * Redis Key 前缀
     */
    public static final class RedisKey {
        public static final String PREFIX = "collab:";
        public static final String PRESENCE = PREFIX + "presence:";
        public static final String LOCATION = PREFIX + "location:";
        public static final String WORKSPACE_USERS = PREFIX + "ws:users:";
        public static final String SCRIPT_USERS = PREFIX + "script:users:";
        public static final String ENTITY_VIEWERS = PREFIX + "entity:viewers:";
        public static final String ENTITY_EDITOR = PREFIX + "entity:editor:";

        // 通知未读计数
        public static final String NOTIFY_UNREAD = PREFIX + "notify:unread:";
        // 评论计数
        public static final String COMMENT_COUNT = PREFIX + "comment:count:";
        // 实体关注者
        public static final String WATCH = PREFIX + "watch:";

        private RedisKey() {}
    }

    /**
     * 配置常量
     * 优化版：调整 TTL 和心跳间隔以减少 Redis 负载
     */
    public static final class Config {
        public static final int PRESENCE_TTL_SECONDS = 90;       // 60 → 90
        public static final int HEARTBEAT_INTERVAL_SECONDS = 45; // 30 → 45
        public static final int AWAY_THRESHOLD_SECONDS = 300;
        public static final int EDIT_LOCK_TTL_SECONDS = 180;     // 300 → 180

        private Config() {}
    }

    /**
     * MQ 队列常量 - 使用 actionow-common-mq 中的共享常量
     */
    public static final class MqQueue {
        public static final String ENTITY_CREATED = com.actionow.common.mq.constant.MqConstants.Collab.QUEUE_ENTITY_CREATED;
        public static final String ENTITY_UPDATED = com.actionow.common.mq.constant.MqConstants.Collab.QUEUE_ENTITY_UPDATED;
        public static final String ENTITY_DELETED = com.actionow.common.mq.constant.MqConstants.Collab.QUEUE_ENTITY_DELETED;
        public static final String AGENT_ACTIVITY = com.actionow.common.mq.constant.MqConstants.Collab.QUEUE_AGENT_ACTIVITY;

        private MqQueue() {}
    }

    /**
     * MQ Exchange 常量 - 使用 actionow-common-mq 中的共享常量
     */
    public static final class MqExchange {
        public static final String COLLAB_EXCHANGE = com.actionow.common.mq.constant.MqConstants.EXCHANGE_COLLAB;

        private MqExchange() {}
    }

    /**
     * MQ Routing Key 常量 - 使用 actionow-common-mq 中的共享常量
     */
    public static final class MqRoutingKey {
        public static final String ENTITY_CREATED = com.actionow.common.mq.constant.MqConstants.Collab.ROUTING_ENTITY_CREATED;
        public static final String ENTITY_UPDATED = com.actionow.common.mq.constant.MqConstants.Collab.ROUTING_ENTITY_UPDATED;
        public static final String ENTITY_DELETED = com.actionow.common.mq.constant.MqConstants.Collab.ROUTING_ENTITY_DELETED;
        public static final String AGENT_ACTIVITY = com.actionow.common.mq.constant.MqConstants.Collab.ROUTING_AGENT_ACTIVITY;

        private MqRoutingKey() {}
    }
}
