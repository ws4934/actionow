package com.actionow.collab.review.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateReviewRequest {

    @NotBlank(message = "实体类型不能为空")
    private String entityType;

    @NotBlank(message = "实体ID不能为空")
    private String entityId;

    private String title;

    private String description;

    @NotBlank(message = "审核人不能为空")
    private String reviewerId;

    private Integer versionNumber;
}
