package com.actionow.collab.dto.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 实体变更事件 (来自 actionow-project MQ)
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityChangeEvent {

    /**
     * 事件类型: CREATED, UPDATED, DELETED
     */
    private String eventType;

    /**
     * 实体类型
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
     * 剧本ID
     */
    private String scriptId;

    /**
     * 操作者ID
     */
    private String operatorId;

    /**
     * 变更的字段列表
     */
    private List<String> changedFields;

    /**
     * 变更后的数据
     */
    private Object data;
}
