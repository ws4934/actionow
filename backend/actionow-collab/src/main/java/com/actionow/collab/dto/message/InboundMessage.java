package com.actionow.collab.dto.message;

import lombok.Data;

/**
 * 入站消息基类 (Client -> Server)
 *
 * @author Actionow
 */
@Data
public class InboundMessage {

    /**
     * 消息类型
     */
    private String type;

    /**
     * 工作空间ID
     */
    private String workspaceId;

    /**
     * 剧本ID
     */
    private String scriptId;

    /**
     * Tab类型
     */
    private String tab;

    /**
     * 实体类型
     */
    private String entityType;

    /**
     * 实体ID
     */
    private String entityId;
}
