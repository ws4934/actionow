package com.actionow.collab.comment.controller;

import com.actionow.collab.comment.dto.*;
import com.actionow.collab.comment.service.CommentService;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.result.PageResult;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireWorkspaceMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/collab/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping
    @RequireWorkspaceMember
    public Result<CommentResponse> create(@RequestBody @Valid CreateCommentRequest request) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        String nickname = UserContextHolder.getContext().getNickname();
        String avatar = null; // Avatar not available in context
        return Result.success(commentService.create(request, workspaceId, userId, nickname, avatar));
    }

    @GetMapping("/{targetType}/{targetId}")
    @RequireWorkspaceMember
    public Result<PageResult<CommentResponse>> listByTarget(
            @PathVariable String targetType,
            @PathVariable String targetId,
            @RequestParam(defaultValue = "1") Long pageNum,
            @RequestParam(defaultValue = "20") Long pageSize) {
        String currentUserId = UserContextHolder.getUserId();
        return Result.success(commentService.listByTarget(targetType, targetId, pageNum, pageSize, currentUserId));
    }

    @GetMapping("/{commentId}")
    @RequireWorkspaceMember
    public Result<CommentResponse> getById(@PathVariable String commentId) {
        return Result.success(commentService.getById(commentId));
    }

    @PutMapping("/{commentId}")
    @RequireWorkspaceMember
    public Result<CommentResponse> update(@PathVariable String commentId,
                                          @RequestBody @Valid UpdateCommentRequest request) {
        String userId = UserContextHolder.getUserId();
        return Result.success(commentService.update(commentId, request, userId));
    }

    @DeleteMapping("/{commentId}")
    @RequireWorkspaceMember
    public Result<Void> delete(@PathVariable String commentId) {
        String userId = UserContextHolder.getUserId();
        commentService.delete(commentId, userId);
        return Result.success();
    }

    @GetMapping("/{commentId}/replies")
    @RequireWorkspaceMember
    public Result<PageResult<CommentResponse>> listReplies(
            @PathVariable String commentId,
            @RequestParam(defaultValue = "1") Long pageNum,
            @RequestParam(defaultValue = "20") Long pageSize) {
        String currentUserId = UserContextHolder.getUserId();
        return Result.success(commentService.listReplies(commentId, pageNum, pageSize, currentUserId));
    }

    @PostMapping("/{commentId}/resolve")
    @RequireWorkspaceMember
    public Result<Void> resolve(@PathVariable String commentId) {
        String userId = UserContextHolder.getUserId();
        commentService.resolve(commentId, userId);
        return Result.success();
    }

    @PostMapping("/{commentId}/reopen")
    @RequireWorkspaceMember
    public Result<Void> reopen(@PathVariable String commentId) {
        String userId = UserContextHolder.getUserId();
        commentService.reopen(commentId, userId);
        return Result.success();
    }

    @PostMapping("/{commentId}/reactions")
    @RequireWorkspaceMember
    public Result<Void> addReaction(@PathVariable String commentId,
                                    @RequestBody Map<String, String> body) {
        String workspaceId = UserContextHolder.getWorkspaceId();
        String userId = UserContextHolder.getUserId();
        commentService.addReaction(commentId, body.get("emoji"), workspaceId, userId);
        return Result.success();
    }

    @DeleteMapping("/{commentId}/reactions/{emoji}")
    @RequireWorkspaceMember
    public Result<Void> removeReaction(@PathVariable String commentId,
                                       @PathVariable String emoji) {
        String userId = UserContextHolder.getUserId();
        commentService.removeReaction(commentId, emoji, userId);
        return Result.success();
    }

    @GetMapping("/count/{targetType}/{targetId}")
    @RequireWorkspaceMember
    public Result<Integer> count(@PathVariable String targetType,
                                 @PathVariable String targetId) {
        return Result.success(commentService.countByTarget(targetType, targetId));
    }
}
