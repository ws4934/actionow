package com.actionow.collab.comment.service;

import com.actionow.collab.comment.dto.*;
import com.actionow.common.core.result.PageResult;

import java.util.List;

public interface CommentService {

    CommentResponse create(CreateCommentRequest request, String workspaceId, String userId, String nickname, String avatar);

    CommentResponse getById(String commentId);

    PageResult<CommentResponse> listByTarget(String targetType, String targetId, Long pageNum, Long pageSize, String currentUserId);

    PageResult<CommentResponse> listReplies(String commentId, Long pageNum, Long pageSize, String currentUserId);

    CommentResponse update(String commentId, UpdateCommentRequest request, String userId);

    void delete(String commentId, String userId);

    void resolve(String commentId, String userId);

    void reopen(String commentId, String userId);

    void addReaction(String commentId, String emoji, String workspaceId, String userId);

    void removeReaction(String commentId, String emoji, String userId);

    int countByTarget(String targetType, String targetId);
}
