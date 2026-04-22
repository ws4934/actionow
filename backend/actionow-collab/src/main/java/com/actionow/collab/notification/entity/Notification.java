package com.actionow.collab.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "t_notification", autoResultMap = true)
public class Notification implements Serializable {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String workspaceId;

    private String userId;

    private String type;

    private String title;

    private String content;

    @TableField(value = "payload", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> payload;

    private String entityType;

    private String entityId;

    private String senderId;

    private String senderName;

    private Boolean isRead;

    private LocalDateTime readAt;

    private Integer priority;

    private LocalDateTime expireAt;

    /**
     * 幂等事件ID：基于业务键派生，用于跨发布者/多消费者去重
     */
    private String eventId;

    private LocalDateTime createdAt;
}
