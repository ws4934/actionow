package com.actionow.collab.review.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReviewDecisionRequest {

    @NotBlank(message = "审核结果不能为空")
    private String status;

    private String comment;
}
