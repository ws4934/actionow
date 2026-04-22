package com.actionow.collab.activity.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.collab.activity.dto.ActivityFeedItem;
import com.actionow.collab.activity.service.ActivityFeedService;
import com.actionow.collab.comment.entity.Comment;
import com.actionow.collab.comment.mapper.CommentMapper;
import com.actionow.collab.review.entity.Review;
import com.actionow.collab.review.mapper.ReviewMapper;
import com.actionow.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityFeedServiceImpl implements ActivityFeedService {

    private final CommentMapper commentMapper;
    private final ReviewMapper reviewMapper;

    @Override
    public PageResult<ActivityFeedItem> getEntityActivities(String entityType, String entityId, Long pageNum, Long pageSize) {
        List<ActivityFeedItem> allItems = new ArrayList<>();

        // Comments on this entity
        List<Comment> comments = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getTargetType, entityType)
                .eq(Comment::getTargetId, entityId)
                .orderByDesc(Comment::getCreatedAt)
                .last("LIMIT 50"));

        for (Comment c : comments) {
            String action = c.getParentId() != null ? "REPLIED" : "CREATED";
            String summary = action.equals("REPLIED")
                    ? "回复了评论: " + truncate(c.getContent(), 50)
                    : "添加了评论: " + truncate(c.getContent(), 50);

            allItems.add(ActivityFeedItem.builder()
                    .type("COMMENT")
                    .action(action)
                    .actor(ActivityFeedItem.ActorInfo.builder().id(c.getCreatedBy()).build())
                    .entityType(entityType)
                    .entityId(entityId)
                    .summary(summary)
                    .metadata(Map.of("commentId", c.getId()))
                    .createdAt(c.getCreatedAt())
                    .build());
        }

        // Reviews for this entity
        List<Review> reviews = reviewMapper.selectByEntity(entityType, entityId);
        for (Review r : reviews) {
            String action = "PENDING".equals(r.getStatus()) ? "REQUESTED" : "DECIDED";
            String summary = action.equals("REQUESTED")
                    ? "发起了审核请求" + (r.getTitle() != null ? ": " + r.getTitle() : "")
                    : "审核结果: " + r.getStatus();

            allItems.add(ActivityFeedItem.builder()
                    .type("REVIEW")
                    .action(action)
                    .actor(ActivityFeedItem.ActorInfo.builder()
                            .id(action.equals("REQUESTED") ? r.getRequesterId() : r.getReviewerId())
                            .build())
                    .entityType(entityType)
                    .entityId(entityId)
                    .summary(summary)
                    .metadata(Map.of("reviewId", r.getId(), "status", r.getStatus()))
                    .createdAt(action.equals("REQUESTED") ? r.getCreatedAt()
                            : r.getReviewedAt() != null ? r.getReviewedAt() : r.getUpdatedAt())
                    .build());
        }

        // Sort by time descending
        allItems.sort(Comparator.comparing(ActivityFeedItem::getCreatedAt).reversed());

        // Paginate
        long total = allItems.size();
        int from = (int) ((pageNum - 1) * pageSize);
        int to = Math.min(from + pageSize.intValue(), allItems.size());

        List<ActivityFeedItem> page = from < allItems.size() ? allItems.subList(from, to) : List.of();
        return PageResult.of(pageNum, pageSize, total, page);
    }

    @Override
    public PageResult<ActivityFeedItem> getScriptActivities(String scriptId, Long pageNum, Long pageSize) {
        List<ActivityFeedItem> allItems = new ArrayList<>();

        // Comments in this script
        Page<Comment> commentPage = new Page<>(1, 100);
        commentMapper.selectPage(commentPage, new LambdaQueryWrapper<Comment>()
                .eq(Comment::getScriptId, scriptId)
                .orderByDesc(Comment::getCreatedAt));

        for (Comment c : commentPage.getRecords()) {
            String action = c.getParentId() != null ? "REPLIED" : "CREATED";
            allItems.add(ActivityFeedItem.builder()
                    .type("COMMENT")
                    .action(action)
                    .actor(ActivityFeedItem.ActorInfo.builder().id(c.getCreatedBy()).build())
                    .entityType(c.getTargetType())
                    .entityId(c.getTargetId())
                    .summary((action.equals("REPLIED") ? "回复了评论: " : "添加了评论: ") + truncate(c.getContent(), 50))
                    .metadata(Map.of("commentId", c.getId()))
                    .createdAt(c.getCreatedAt())
                    .build());
        }

        allItems.sort(Comparator.comparing(ActivityFeedItem::getCreatedAt).reversed());

        long total = allItems.size();
        int from = (int) ((pageNum - 1) * pageSize);
        int to = Math.min(from + pageSize.intValue(), allItems.size());

        List<ActivityFeedItem> page = from < allItems.size() ? allItems.subList(from, to) : List.of();
        return PageResult.of(pageNum, pageSize, total, page);
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
