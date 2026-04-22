package com.actionow.collab.review.service;

import com.actionow.collab.review.dto.*;
import com.actionow.common.core.result.PageResult;

import java.util.List;

public interface ReviewService {

    ReviewResponse create(CreateReviewRequest request, String workspaceId, String userId, String nickname);

    ReviewResponse getById(String reviewId);

    List<ReviewResponse> listByEntity(String entityType, String entityId);

    PageResult<ReviewResponse> listPending(String reviewerId, Long pageNum, Long pageSize);

    ReviewResponse decide(String reviewId, ReviewDecisionRequest request, String userId, String nickname);

    void delete(String reviewId, String userId);
}
