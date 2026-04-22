package com.actionow.collab.review.dto;

import com.actionow.collab.review.entity.Review;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponse {

    private String id;
    private String workspaceId;
    private String entityType;
    private String entityId;
    private String title;
    private String description;
    private String status;
    private String requesterId;
    private String reviewerId;
    private LocalDateTime reviewedAt;
    private String reviewComment;
    private Integer versionNumber;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ReviewResponse fromEntity(Review review) {
        return ReviewResponse.builder()
                .id(review.getId())
                .workspaceId(review.getWorkspaceId())
                .entityType(review.getEntityType())
                .entityId(review.getEntityId())
                .title(review.getTitle())
                .description(review.getDescription())
                .status(review.getStatus())
                .requesterId(review.getRequesterId())
                .reviewerId(review.getReviewerId())
                .reviewedAt(review.getReviewedAt())
                .reviewComment(review.getReviewComment())
                .versionNumber(review.getVersionNumber())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }
}
