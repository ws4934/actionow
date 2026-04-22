package com.actionow.collab.notification.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class BatchMarkReadRequest {
    @NotEmpty(message = "通知ID列表不能为空")
    private List<String> ids;
}
