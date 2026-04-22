package com.actionow.collab.watch.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WatchRequest {
    @NotBlank(message = "实体类型不能为空")
    private String entityType;

    @NotBlank(message = "实体ID不能为空")
    private String entityId;

    private String watchType;
}
