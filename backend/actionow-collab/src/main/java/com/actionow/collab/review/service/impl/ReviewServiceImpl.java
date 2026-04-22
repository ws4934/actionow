package com.actionow.collab.review.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.collab.constant.CollabConstants;
import com.actionow.collab.notification.service.NotificationDispatcher;
import com.actionow.collab.review.dto.*;
import com.actionow.collab.review.entity.Review;
import com.actionow.collab.review.mapper.ReviewMapper;
import com.actionow.collab.review.service.ReviewService;
import com.actionow.collab.websocket.CollaborationHub;
import com.actionow.collab.dto.message.OutboundMessage;
import com.actionow.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewMapper reviewMapper;
    private final NotificationDispatcher notificationDispatcher;
    private final CollaborationHub collaborationHub;

    @Override
    @Transactional
    public ReviewResponse create(CreateReviewRequest request, String workspaceId, String userId, String nickname) {
        Review review = new Review();
        review.setWorkspaceId(workspaceId);
        review.setEntityType(request.getEntityType());
        review.setEntityId(request.getEntityId());
        review.setTitle(request.getTitle());
        review.setDescription(request.getDescription());
        review.setStatus("PENDING");
        review.setRequesterId(userId);
        review.setReviewerId(request.getReviewerId());
        review.setVersionNumber(request.getVersionNumber());

        reviewMapper.insert(review);

        // Notify reviewer
        notificationDispatcher.dispatchReviewNotification(
                workspaceId, request.getReviewerId(),
                nickname + " 请求你审核 " + request.getEntityType() + (request.getTitle() != null ? ": " + request.getTitle() : ""),
                request.getDescription(),
                request.getEntityType(), request.getEntityId(),
                userId, nickname,
                "REVIEW_REQUEST",
                Map.of("reviewId", review.getId(), "entityType", request.getEntityType(), "entityId", request.getEntityId())
        );

        return ReviewResponse.fromEntity(review);
    }

    @Override
    public ReviewResponse getById(String reviewId) {
        Review review = reviewMapper.selectById(reviewId);
        if (review == null) return null;
        return ReviewResponse.fromEntity(review);
    }

    @Override
    public List<ReviewResponse> listByEntity(String entityType, String entityId) {
        return reviewMapper.selectByEntity(entityType, entityId).stream()
                .map(ReviewResponse::fromEntity)
                .toList();
    }

    @Override
    public PageResult<ReviewResponse> listPending(String reviewerId, Long pageNum, Long pageSize) {
        Page<Review> page = new Page<>(pageNum, pageSize);
        reviewMapper.selectPendingByReviewer(page, reviewerId);

        List<ReviewResponse> records = page.getRecords().stream()
                .map(ReviewResponse::fromEntity)
                .toList();

        return PageResult.of(pageNum, pageSize, page.getTotal(), records);
    }

    @Override
    @Transactional
    public ReviewResponse decide(String reviewId, ReviewDecisionRequest request, String userId, String nickname) {
        Review review = reviewMapper.selectById(reviewId);
        if (review == null) throw new IllegalArgumentException("审核不存在");
        if (!review.getReviewerId().equals(userId)) throw new IllegalArgumentException("只有审核人可以做出决定");
        if (!"PENDING".equals(review.getStatus())) throw new IllegalArgumentException("审核已完成");

        review.setStatus(request.getStatus());
        review.setReviewComment(request.getComment());
        review.setReviewedAt(LocalDateTime.now());
        reviewMapper.updateById(review);

        // Notify requester
        String statusText = switch (request.getStatus()) {
            case "APPROVED" -> "批准";
            case "REJECTED" -> "拒绝";
            case "CHANGES_REQUESTED" -> "要求修改";
            default -> request.getStatus();
        };

        notificationDispatcher.dispatchReviewNotification(
                review.getWorkspaceId(), review.getRequesterId(),
                nickname + " " + statusText + "了你的审核请求",
                request.getComment(),
                review.getEntityType(), review.getEntityId(),
                userId, nickname,
                "REVIEW_RESULT",
                Map.of("reviewId", reviewId, "status", request.getStatus(),
                        "entityType", review.getEntityType(), "entityId", review.getEntityId())
        );

        // Broadcast to workspace
        collaborationHub.sendToWorkspace(review.getWorkspaceId(),
                OutboundMessage.of(CollabConstants.MessageType.REVIEW_STATUS_CHANGED, Map.of(
                        "reviewId", reviewId,
                        "entityType", review.getEntityType(),
                        "entityId", review.getEntityId(),
                        "status", request.getStatus(),
                        "reviewerName", nickname
                )));

        return ReviewResponse.fromEntity(review);
    }

    @Override
    @Transactional
    public void delete(String reviewId, String userId) {
        Review review = reviewMapper.selectById(reviewId);
        if (review == null) return;
        if (!review.getRequesterId().equals(userId)) throw new IllegalArgumentException("只有发起人可以取消审核请求");

        reviewMapper.deleteById(reviewId);
    }
}
