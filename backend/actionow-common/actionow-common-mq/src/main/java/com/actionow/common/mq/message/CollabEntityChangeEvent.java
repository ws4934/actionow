package com.actionow.common.mq.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 实体变更事件 (用于协作服务)
 * 从 actionow-project 发送到 actionow-collab
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollabEntityChangeEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 事件类型: CREATED, UPDATED, DELETED
     */
    private String eventType;

    /**
     * 实体类型: SCRIPT, EPISODE, STORYBOARD, CHARACTER, SCENE, PROP
     */
    private String entityType;

    /**
     * 实体ID
     */
    private String entityId;

    /**
     * 工作空间ID
     */
    private String workspaceId;

    /**
     * 剧本ID (所属剧本)
     */
    private String scriptId;

    /**
     * 操作者ID
     */
    private String operatorId;

    /**
     * 变更的字段列表 (仅 UPDATED 时有值)
     */
    private List<String> changedFields;

    /**
     * 变更后的数据
     */
    private Object data;

    /**
     * 事件类型常量
     */
    public static final class EventType {
        public static final String CREATED = "CREATED";
        public static final String UPDATED = "UPDATED";
        public static final String DELETED = "DELETED";

        private EventType() {}
    }

    /**
     * 实体类型常量
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
}
