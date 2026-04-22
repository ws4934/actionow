package com.actionow.collab.notification.dto;

import lombok.Data;

import java.time.LocalTime;

@Data
public class NotificationPreferenceRequest {
    private Boolean commentMention;
    private Boolean commentReply;
    private Boolean entityChange;
    private Boolean reviewRequest;
    private Boolean reviewResult;
    private Boolean taskCompleted;
    private Boolean systemAlert;
    private LocalTime quietStart;
    private LocalTime quietEnd;
}
