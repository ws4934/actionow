package com.actionow.collab.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_notification_preference")
public class NotificationPreference implements Serializable {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;

    private String workspaceId;

    private Boolean commentMention;

    private Boolean commentReply;

    private Boolean entityChange;

    private Boolean reviewRequest;

    private Boolean reviewResult;

    private Boolean taskCompleted;

    private Boolean systemAlert;

    private LocalTime quietStart;

    private LocalTime quietEnd;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
