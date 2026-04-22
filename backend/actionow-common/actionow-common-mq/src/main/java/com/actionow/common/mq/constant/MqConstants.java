package com.actionow.common.mq.constant;

/**
 * 消息队列常量
 * 按业务域以嵌套类分组，Exchange 等共享基础设施保留在顶层
 *
 * @author Actionow
 */
public final class MqConstants {

    private MqConstants() {
    }

    // ==================== 共享 Exchange ====================

    public static final String EXCHANGE_DIRECT = "actionow.direct";
    public static final String EXCHANGE_TOPIC = "actionow.topic";
    public static final String EXCHANGE_DEAD_LETTER = "actionow.dlx";
    public static final String EXCHANGE_COLLAB = "actionow.collab";

    // ==================== 死信队列 ====================

    public static final String QUEUE_DEAD_LETTER = "actionow.dlq";

    // ==================== 任务服务 ====================

    public static final class Task {
        public static final String QUEUE = "actionow.task";
        public static final String QUEUE_NOTIFICATION = "actionow.notification";

        public static final String ROUTING_CREATED = "task.created";
        public static final String ROUTING_COMPLETED = "task.completed";

        public static final String MSG_CREATED = "TASK_CREATED";
        public static final String MSG_STATUS_CHANGED = "TASK_STATUS_CHANGED";

        private Task() {
        }
    }

    // ==================== 批量作业 ====================

    public static final class BatchJob {
        public static final String QUEUE = "actionow.batch.job";
        public static final String QUEUE_TASK_CALLBACK = "actionow.batch.job.task.callback";

        public static final String ROUTING_START = "batch.job.start";
        public static final String ROUTING_TASK_CALLBACK = "batch.job.task.callback";
        public static final String ROUTING_COMPLETED = "batch.job.completed";

        public static final String MSG_START = "BATCH_JOB_START";
        public static final String MSG_TASK_CALLBACK = "BATCH_JOB_TASK_CALLBACK";
        public static final String MSG_COMPLETED = "BATCH_JOB_COMPLETED";

        private BatchJob() {
        }
    }

    // ==================== Agent Mission ====================

    public static final class Mission {
        public static final String QUEUE = "actionow.mission";
        public static final String QUEUE_TASK_CALLBACK = "actionow.mission.task.callback";

        public static final String ROUTING_STEP_EXECUTE = "mission.step.execute";
        public static final String ROUTING_TASK_CALLBACK = "mission.task.callback";

        public static final String MSG_STEP_EXECUTE = "MISSION_STEP_EXECUTE";
        public static final String MSG_TASK_CALLBACK = "MISSION_TASK_CALLBACK";

        private Mission() {
        }
    }

    // ==================== 协作服务 ====================

    public static final class Collab {
        public static final String QUEUE_ENTITY_CREATED = "actionow.collab.entity.created";
        public static final String QUEUE_ENTITY_UPDATED = "actionow.collab.entity.updated";
        public static final String QUEUE_ENTITY_DELETED = "actionow.collab.entity.deleted";
        public static final String QUEUE_AGENT_ACTIVITY = "actionow.collab.agent.activity";
        public static final String QUEUE_COMMENT = "actionow.collab.comment";

        public static final String ROUTING_ENTITY_CREATED = "collab.entity.created";
        public static final String ROUTING_ENTITY_UPDATED = "collab.entity.updated";
        public static final String ROUTING_ENTITY_DELETED = "collab.entity.deleted";
        public static final String ROUTING_AGENT_ACTIVITY = "collab.agent.activity";
        public static final String ROUTING_COMMENT = "collab.comment.#";

        public static final String MSG_ENTITY_CREATED = "COLLAB_ENTITY_CREATED";
        public static final String MSG_ENTITY_UPDATED = "COLLAB_ENTITY_UPDATED";
        public static final String MSG_ENTITY_DELETED = "COLLAB_ENTITY_DELETED";
        public static final String MSG_AGENT_ACTIVITY = "COLLAB_AGENT_ACTIVITY";
        public static final String MSG_COMMENT_CREATED = "COMMENT_CREATED";
        public static final String MSG_COMMENT_RESOLVED = "COMMENT_RESOLVED";

        private Collab() {
        }
    }

    // ==================== Canvas 服务 ====================

    public static final class Canvas {
        public static final String QUEUE = "actionow.canvas";

        public static final String ROUTING_ENTITY_CHANGE = "entity.change.#";

        public static final String MSG_ENTITY_CHANGE = "ENTITY_CHANGE";
        public static final String MSG_BATCH_ENTITY_CHANGE = "BATCH_ENTITY_CHANGE";

        private Canvas() {
        }
    }

    // ==================== 文件服务 ====================

    public static final class File {
        public static final String QUEUE = "actionow.file";

        public static final String ROUTING_UPLOADED = "file.uploaded";
        public static final String ROUTING_THUMBNAIL_REQUEST = "file.thumbnail.request";

        public static final String MSG_UPLOADED = "FILE_UPLOADED";
        public static final String MSG_THUMBNAIL_REQUEST = "THUMBNAIL_REQUEST";

        private File() {
        }
    }

    // ==================== 钱包服务 ====================

    public static final class Wallet {
        public static final String QUEUE = "actionow.wallet";

        public static final String ROUTING_CREATE_COMPENSATION = "wallet.create.compensation";
        public static final String ROUTING_CLOSE = "wallet.close";
        public static final String ROUTING_ADJUST_PLAN = "wallet.adjust.plan";

        public static final String MSG_CREATE_COMPENSATION = "WALLET_CREATE_COMPENSATION";
        public static final String MSG_CLOSE = "WALLET_CLOSE";
        public static final String MSG_ADJUST_PLAN = "WALLET_ADJUST_PLAN";

        private Wallet() {
        }
    }

    // ==================== WebSocket 通知 ====================

    public static final class Ws {
        public static final String QUEUE_NOTIFICATION = "actionow.ws.notification";

        public static final String ROUTING_TASK_STATUS = "ws.task.status";
        public static final String ROUTING_ENTITY_CHANGED = "ws.entity.changed";
        public static final String ROUTING_WALLET_BALANCE = "ws.wallet.balance";

        public static final String MSG_TASK_STATUS = "WS_TASK_STATUS";
        public static final String MSG_ENTITY_CHANGED = "WS_ENTITY_CHANGED";
        public static final String MSG_WALLET_BALANCE = "WS_WALLET_BALANCE";

        private Ws() {
        }
    }

    // ==================== 系统告警 ====================

    public static final class Alert {
        public static final String QUEUE = "actionow.system.alert";

        public static final String ROUTING = "system.alert";

        public static final String MSG_TYPE = "SYSTEM_ALERT";

        private Alert() {
        }
    }

    // ==================== Inspiration ====================

    public static final class Inspiration {
        public static final String QUEUE = "actionow.inspiration.task";

        private Inspiration() {
        }
    }
}
