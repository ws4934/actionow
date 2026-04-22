package com.actionow.collab.review.controller;

import com.actionow.collab.review.dto.*;
import com.actionow.collab.review.service.ReviewService;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.result.PageResult;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireWorkspaceMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/collab/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    @RequireWorkspaceMember
    public Result<ReviewResponse> create(@RequestBody @Valid CreateReviewRequest request) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        String nickname = UserContextHolder.getContext().getNickname();
        return Result.success(reviewService.create(request, workspaceId, userId, nickname));
    }

    @GetMapping("/{reviewId}")
    @RequireWorkspaceMember
    public Result<ReviewResponse> getById(@PathVariable String reviewId) {
        return Result.success(reviewService.getById(reviewId));
    }

    @GetMapping("/entity/{entityType}/{entityId}")
    @RequireWorkspaceMember
    public Result<List<ReviewResponse>> listByEntity(@PathVariable String entityType, @PathVariable String entityId) {
        return Result.success(reviewService.listByEntity(entityType, entityId));
    }

    @GetMapping("/pending")
    @RequireWorkspaceMember
    public Result<PageResult<ReviewResponse>> listPending(
            @RequestParam(defaultValue = "1") Long pageNum,
            @RequestParam(defaultValue = "20") Long pageSize) {
        String userId = UserContextHolder.getUserId();
        return Result.success(reviewService.listPending(userId, pageNum, pageSize));
    }

    @PutMapping("/{reviewId}/decide")
    @RequireWorkspaceMember
    public Result<ReviewResponse> decide(@PathVariable String reviewId,
                                          @RequestBody @Valid ReviewDecisionRequest request) {
        String userId = UserContextHolder.getUserId();
        String nickname = UserContextHolder.getContext().getNickname();
        return Result.success(reviewService.decide(reviewId, request, userId, nickname));
    }

    @DeleteMapping("/{reviewId}")
    @RequireWorkspaceMember
    public Result<Void> delete(@PathVariable String reviewId) {
        String userId = UserContextHolder.getUserId();
        reviewService.delete(reviewId, userId);
        return Result.success();
    }
}
