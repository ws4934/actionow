package com.actionow.collab.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreateCommentRequest {

    @NotBlank(message = "评论目标类型不能为空")
    private String targetType;

    @NotBlank(message = "评论目标ID不能为空")
    private String targetId;

    private String scriptId;

    private String parentId;

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 5000, message = "评论内容不能超过5000个字符")
    private String content;

    private List<CommentMention> mentions;

    private List<CommentAttachmentDto> attachments;
}
