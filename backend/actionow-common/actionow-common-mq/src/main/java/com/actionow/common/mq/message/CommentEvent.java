package com.actionow.common.mq.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 评论事件 (用于跨服务通知)
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String eventType;
    private String commentId;
    private String workspaceId;
    private String scriptId;
    private String targetType;
    private String targetId;
    private String parentId;
    private String authorId;
    private String authorName;
    private List<MentionInfo> mentions;
    private String contentPreview;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MentionInfo implements Serializable {
        private String type;
        private String id;
        private String name;
    }
}
