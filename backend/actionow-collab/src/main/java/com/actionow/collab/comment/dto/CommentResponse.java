package com.actionow.collab.comment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentResponse {

    private String id;
    private String targetType;
    private String targetId;
    private String scriptId;
    private String parentId;
    private String content;
    private List<CommentMention> mentions;
    private String status;

    private CommentAuthor author;

    private List<CommentAttachmentDto> attachments;

    private int replyCount;
    private List<ReactionSummary> reactions;
    private List<CommentResponse> latestReplies;

    private String resolvedBy;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommentAuthor {
        private String id;
        private String nickname;
        private String avatar;
    }
}
