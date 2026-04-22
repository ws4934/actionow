package com.actionow.collab.comment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.collab.comment.dto.*;
import com.actionow.collab.comment.entity.Comment;
import com.actionow.collab.comment.entity.CommentAttachment;
import com.actionow.collab.comment.entity.CommentReaction;
import com.actionow.collab.comment.mapper.CommentAttachmentMapper;
import com.actionow.collab.comment.mapper.CommentMapper;
import com.actionow.collab.comment.mapper.CommentReactionMapper;
import com.actionow.collab.comment.service.CommentService;
import com.actionow.collab.constant.CollabConstants;
import com.actionow.collab.feign.UserBasicInfo;
import com.actionow.collab.feign.UserFeignClient;
import com.actionow.collab.notification.service.NotificationDispatcher;
import com.actionow.collab.watch.service.EntityWatchService;
import com.actionow.collab.websocket.CollaborationHub;
import com.actionow.collab.dto.message.OutboundMessage;
import com.actionow.common.core.result.PageResult;
import com.actionow.common.core.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentMapper commentMapper;
    private final CommentAttachmentMapper attachmentMapper;
    private final CommentReactionMapper reactionMapper;
    private final CollaborationHub collaborationHub;
    private final NotificationDispatcher notificationDispatcher;
    private final EntityWatchService entityWatchService;
    private final UserFeignClient userFeignClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public CommentResponse create(CreateCommentRequest request, String workspaceId, String userId, String nickname, String avatar) {
        Comment comment = new Comment();
        comment.setWorkspaceId(workspaceId);
        comment.setTargetType(request.getTargetType());
        comment.setTargetId(request.getTargetId());
        comment.setScriptId(request.getScriptId());
        comment.setParentId(request.getParentId());
        comment.setContent(request.getContent());
        comment.setStatus("OPEN");

        if (request.getMentions() != null) {
            List<Object> mentionData = request.getMentions().stream()
                    .map(m -> (Object) objectMapper.convertValue(m, Map.class))
                    .toList();
            comment.setMentions(mentionData);
        }

        commentMapper.insert(comment);

        // Save attachments
        if (request.getAttachments() != null && !request.getAttachments().isEmpty()) {
            for (int i = 0; i < request.getAttachments().size(); i++) {
                CommentAttachmentDto dto = request.getAttachments().get(i);
                CommentAttachment attachment = CommentAttachment.builder()
                        .commentId(comment.getId())
                        .assetId(dto.getAssetId())
                        .assetType(dto.getAssetType())
                        .fileName(dto.getFileName())
                        .fileUrl(dto.getFileUrl())
                        .thumbnailUrl(dto.getThumbnailUrl())
                        .fileSize(dto.getFileSize())
                        .mimeType(dto.getMimeType())
                        .metaInfo(dto.getMetaInfo())
                        .sequence(i)
                        .createdAt(LocalDateTime.now())
                        .build();
                attachmentMapper.insert(attachment);
            }
        }

        // Auto-watch: commenter watches entity
        try {
            entityWatchService.watch(request.getTargetType(), request.getTargetId(), workspaceId, userId);
        } catch (Exception e) {
            log.debug("Auto-watch failed (may already exist): {}", e.getMessage());
        }

        // Invalidate comment count cache
        String countKey = CollabConstants.RedisKey.COMMENT_COUNT + request.getTargetType() + ":" + request.getTargetId();
        redisTemplate.delete(countKey);

        CommentResponse response = buildResponse(comment, userId, nickname, avatar);

        // Broadcast to script
        if (request.getScriptId() != null) {
            collaborationHub.sendToScript(request.getScriptId(),
                    OutboundMessage.of(CollabConstants.MessageType.COMMENT_CREATED, Map.of("comment", response)));
        }

        // Async notification dispatch
        notificationDispatcher.dispatchCommentNotification(comment, request.getMentions(), workspaceId, userId, nickname);

        return response;
    }

    @Override
    public CommentResponse getById(String commentId) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) return null;
        CommentResponse response = buildFullResponse(comment, null);
        enrichUserInfo(List.of(response));
        return response;
    }

    @Override
    public PageResult<CommentResponse> listByTarget(String targetType, String targetId, Long pageNum, Long pageSize, String currentUserId) {
        Page<Comment> page = new Page<>(pageNum, pageSize);
        commentMapper.selectPageByTarget(page, targetType, targetId);

        List<CommentResponse> records = page.getRecords().stream()
                .map(c -> buildFullResponse(c, currentUserId))
                .toList();

        enrichUserInfo(records);

        return PageResult.of(pageNum, pageSize, page.getTotal(), records);
    }

    @Override
    public PageResult<CommentResponse> listReplies(String commentId, Long pageNum, Long pageSize, String currentUserId) {
        Page<Comment> page = new Page<>(pageNum, pageSize);
        commentMapper.selectReplies(page, commentId);

        List<CommentResponse> records = page.getRecords().stream()
                .map(c -> buildFullResponse(c, currentUserId))
                .toList();

        enrichUserInfo(records);

        return PageResult.of(pageNum, pageSize, page.getTotal(), records);
    }

    @Override
    @Transactional
    public CommentResponse update(String commentId, UpdateCommentRequest request, String userId) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null || !comment.getCreatedBy().equals(userId)) {
            throw new IllegalArgumentException("评论不存在或无权修改");
        }

        comment.setContent(request.getContent());
        if (request.getMentions() != null) {
            List<Object> mentionData = request.getMentions().stream()
                    .map(m -> (Object) objectMapper.convertValue(m, Map.class))
                    .toList();
            comment.setMentions(mentionData);
        }
        commentMapper.updateById(comment);

        CommentResponse response = buildFullResponse(comment, null);

        if (comment.getScriptId() != null) {
            collaborationHub.sendToScript(comment.getScriptId(),
                    OutboundMessage.of(CollabConstants.MessageType.COMMENT_UPDATED, Map.of("comment", response)));
        }

        return response;
    }

    @Override
    @Transactional
    public void delete(String commentId, String userId) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) return;

        commentMapper.deleteById(commentId);

        if (comment.getScriptId() != null) {
            collaborationHub.sendToScript(comment.getScriptId(),
                    OutboundMessage.of(CollabConstants.MessageType.COMMENT_DELETED,
                            Map.of("commentId", commentId, "targetType", comment.getTargetType(), "targetId", comment.getTargetId())));
        }

        // Invalidate count cache
        String countKey = CollabConstants.RedisKey.COMMENT_COUNT + comment.getTargetType() + ":" + comment.getTargetId();
        redisTemplate.delete(countKey);
    }

    @Override
    @Transactional
    public void resolve(String commentId, String userId) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) throw new IllegalArgumentException("评论不存在");

        comment.setStatus("RESOLVED");
        comment.setResolvedBy(userId);
        comment.setResolvedAt(LocalDateTime.now());
        commentMapper.updateById(comment);

        if (comment.getScriptId() != null) {
            collaborationHub.sendToScript(comment.getScriptId(),
                    OutboundMessage.of(CollabConstants.MessageType.COMMENT_RESOLVED,
                            Map.of("commentId", commentId, "resolvedBy", userId)));
        }
    }

    @Override
    @Transactional
    public void reopen(String commentId, String userId) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) throw new IllegalArgumentException("评论不存在");

        comment.setStatus("OPEN");
        comment.setResolvedBy(null);
        comment.setResolvedAt(null);
        commentMapper.updateById(comment);

        if (comment.getScriptId() != null) {
            collaborationHub.sendToScript(comment.getScriptId(),
                    OutboundMessage.of(CollabConstants.MessageType.COMMENT_REOPENED,
                            Map.of("commentId", commentId)));
        }
    }

    @Override
    @Transactional
    public void addReaction(String commentId, String emoji, String workspaceId, String userId) {
        CommentReaction existing = reactionMapper.selectByCommentIdAndUserAndEmoji(commentId, userId, emoji);
        if (existing != null) return;

        CommentReaction reaction = CommentReaction.builder()
                .workspaceId(workspaceId)
                .commentId(commentId)
                .emoji(emoji)
                .createdBy(userId)
                .createdAt(LocalDateTime.now())
                .build();
        reactionMapper.insert(reaction);

        Comment comment = commentMapper.selectById(commentId);
        if (comment != null && comment.getScriptId() != null) {
            collaborationHub.sendToScript(comment.getScriptId(),
                    OutboundMessage.of(CollabConstants.MessageType.COMMENT_REACTION,
                            Map.of("commentId", commentId, "emoji", emoji, "action", "ADDED", "userId", userId)));
        }
    }

    @Override
    @Transactional
    public void removeReaction(String commentId, String emoji, String userId) {
        CommentReaction reaction = reactionMapper.selectByCommentIdAndUserAndEmoji(commentId, userId, emoji);
        if (reaction == null) return;

        reactionMapper.deleteById(reaction.getId());

        Comment comment = commentMapper.selectById(commentId);
        if (comment != null && comment.getScriptId() != null) {
            collaborationHub.sendToScript(comment.getScriptId(),
                    OutboundMessage.of(CollabConstants.MessageType.COMMENT_REACTION,
                            Map.of("commentId", commentId, "emoji", emoji, "action", "REMOVED", "userId", userId)));
        }
    }

    @Override
    public int countByTarget(String targetType, String targetId) {
        String countKey = CollabConstants.RedisKey.COMMENT_COUNT + targetType + ":" + targetId;
        String cached = redisTemplate.opsForValue().get(countKey);
        if (cached != null) {
            return Integer.parseInt(cached);
        }

        int count = commentMapper.countByTarget(targetType, targetId);
        redisTemplate.opsForValue().set(countKey, String.valueOf(count), 1, TimeUnit.HOURS);
        return count;
    }

    // ==================== Private Helpers ====================

    /**
     * 批量填充评论列表中的用户信息（nickname、avatar）
     * 同时处理 latestReplies 中的嵌套评论
     */
    private void enrichUserInfo(List<CommentResponse> responses) {
        if (responses == null || responses.isEmpty()) return;

        // 收集所有需要查询的用户ID（包括嵌套回复）
        Set<String> userIds = new HashSet<>();
        for (CommentResponse r : responses) {
            if (r.getAuthor() != null && r.getAuthor().getId() != null) {
                userIds.add(r.getAuthor().getId());
            }
            if (r.getLatestReplies() != null) {
                for (CommentResponse reply : r.getLatestReplies()) {
                    if (reply.getAuthor() != null && reply.getAuthor().getId() != null) {
                        userIds.add(reply.getAuthor().getId());
                    }
                }
            }
        }
        if (userIds.isEmpty()) return;

        // 批量查询用户信息
        Map<String, UserBasicInfo> userMap;
        try {
            Result<Map<String, UserBasicInfo>> result = userFeignClient.batchGetUserBasicInfo(new ArrayList<>(userIds));
            userMap = (result != null && result.isSuccess() && result.getData() != null)
                    ? result.getData() : Collections.emptyMap();
        } catch (Exception e) {
            log.warn("批量获取用户信息失败，评论作者信息将不完整: {}", e.getMessage());
            return;
        }

        // 填充到评论响应中
        for (CommentResponse r : responses) {
            fillAuthor(r.getAuthor(), userMap);
            if (r.getLatestReplies() != null) {
                for (CommentResponse reply : r.getLatestReplies()) {
                    fillAuthor(reply.getAuthor(), userMap);
                }
            }
        }
    }

    private void fillAuthor(CommentResponse.CommentAuthor author, Map<String, UserBasicInfo> userMap) {
        if (author == null || author.getId() == null) return;
        UserBasicInfo info = userMap.get(author.getId());
        if (info != null) {
            author.setNickname(info.getNickname());
            author.setAvatar(info.getAvatar());
        }
    }

    private CommentResponse buildResponse(Comment comment, String userId, String nickname, String avatar) {
        List<CommentAttachment> attachments = attachmentMapper.selectByCommentId(comment.getId());
        List<CommentAttachmentDto> attachmentDtos = attachments.stream()
                .map(a -> CommentAttachmentDto.builder()
                        .assetId(a.getAssetId()).assetType(a.getAssetType())
                        .fileName(a.getFileName()).fileUrl(a.getFileUrl())
                        .thumbnailUrl(a.getThumbnailUrl()).fileSize(a.getFileSize())
                        .mimeType(a.getMimeType()).metaInfo(a.getMetaInfo())
                        .build())
                .toList();

        List<CommentMention> mentions = parseMentions(comment.getMentions());

        return CommentResponse.builder()
                .id(comment.getId())
                .targetType(comment.getTargetType())
                .targetId(comment.getTargetId())
                .scriptId(comment.getScriptId())
                .parentId(comment.getParentId())
                .content(comment.getContent())
                .mentions(mentions)
                .status(comment.getStatus())
                .author(CommentResponse.CommentAuthor.builder()
                        .id(userId).nickname(nickname).avatar(avatar).build())
                .attachments(attachmentDtos)
                .replyCount(0)
                .reactions(List.of())
                .latestReplies(List.of())
                .resolvedBy(comment.getResolvedBy())
                .resolvedAt(comment.getResolvedAt())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }

    private CommentResponse buildFullResponse(Comment comment, String currentUserId) {
        List<CommentAttachment> attachments = attachmentMapper.selectByCommentId(comment.getId());
        List<CommentAttachmentDto> attachmentDtos = attachments.stream()
                .map(a -> CommentAttachmentDto.builder()
                        .assetId(a.getAssetId()).assetType(a.getAssetType())
                        .fileName(a.getFileName()).fileUrl(a.getFileUrl())
                        .thumbnailUrl(a.getThumbnailUrl()).fileSize(a.getFileSize())
                        .mimeType(a.getMimeType()).metaInfo(a.getMetaInfo())
                        .build())
                .toList();

        List<CommentMention> mentions = parseMentions(comment.getMentions());
        List<ReactionSummary> reactions = buildReactionSummaries(comment.getId(), currentUserId);
        int replyCount = commentMapper.countReplies(comment.getId());

        // Get latest 2 replies preview
        List<CommentResponse> latestReplies = List.of();
        if (comment.getParentId() == null && replyCount > 0) {
            Page<Comment> replyPage = new Page<>(1, 2);
            commentMapper.selectReplies(replyPage, comment.getId());
            latestReplies = replyPage.getRecords().stream()
                    .map(r -> {
                        List<CommentAttachment> replyAttachments = attachmentMapper.selectByCommentId(r.getId());
                        List<CommentAttachmentDto> replyAttachmentDtos = replyAttachments.stream()
                                .map(a -> CommentAttachmentDto.builder()
                                        .assetId(a.getAssetId()).assetType(a.getAssetType())
                                        .fileName(a.getFileName()).fileUrl(a.getFileUrl())
                                        .thumbnailUrl(a.getThumbnailUrl()).fileSize(a.getFileSize())
                                        .mimeType(a.getMimeType()).metaInfo(a.getMetaInfo())
                                        .build())
                                .toList();
                        List<CommentMention> replyMentions = parseMentions(r.getMentions());
                        return CommentResponse.builder()
                                .id(r.getId())
                                .content(r.getContent())
                                .mentions(replyMentions)
                                .author(CommentResponse.CommentAuthor.builder()
                                        .id(r.getCreatedBy()).build())
                                .attachments(replyAttachmentDtos)
                                .replyCount(0)
                                .createdAt(r.getCreatedAt())
                                .updatedAt(r.getUpdatedAt())
                                .build();
                    })
                    .toList();
        }

        return CommentResponse.builder()
                .id(comment.getId())
                .targetType(comment.getTargetType())
                .targetId(comment.getTargetId())
                .scriptId(comment.getScriptId())
                .parentId(comment.getParentId())
                .content(comment.getContent())
                .mentions(mentions)
                .status(comment.getStatus())
                .author(CommentResponse.CommentAuthor.builder()
                        .id(comment.getCreatedBy()).build())
                .attachments(attachmentDtos)
                .replyCount(replyCount)
                .reactions(reactions)
                .latestReplies(latestReplies)
                .resolvedBy(comment.getResolvedBy())
                .resolvedAt(comment.getResolvedAt())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }

    private List<ReactionSummary> buildReactionSummaries(String commentId, String currentUserId) {
        List<CommentReaction> allReactions = reactionMapper.selectByCommentId(commentId);
        if (allReactions.isEmpty()) return List.of();

        Map<String, List<CommentReaction>> grouped = allReactions.stream()
                .collect(Collectors.groupingBy(CommentReaction::getEmoji));

        return grouped.entrySet().stream()
                .map(entry -> ReactionSummary.builder()
                        .emoji(entry.getKey())
                        .count(entry.getValue().size())
                        .reacted(currentUserId != null && entry.getValue().stream()
                                .anyMatch(r -> r.getCreatedBy().equals(currentUserId)))
                        .build())
                .sorted(Comparator.comparing(ReactionSummary::getEmoji))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<CommentMention> parseMentions(List<Object> mentionData) {
        if (mentionData == null || mentionData.isEmpty()) return List.of();
        return mentionData.stream()
                .map(obj -> objectMapper.convertValue(obj, CommentMention.class))
                .toList();
    }
}
