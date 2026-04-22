package com.actionow.collab.comment.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.collab.comment.entity.CommentReaction;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CommentReactionMapper extends BaseMapper<CommentReaction> {

    default List<CommentReaction> selectByCommentId(String commentId) {
        return selectList(new LambdaQueryWrapper<CommentReaction>()
                .eq(CommentReaction::getCommentId, commentId));
    }

    default CommentReaction selectByCommentIdAndUserAndEmoji(String commentId, String userId, String emoji) {
        return selectOne(new LambdaQueryWrapper<CommentReaction>()
                .eq(CommentReaction::getCommentId, commentId)
                .eq(CommentReaction::getCreatedBy, userId)
                .eq(CommentReaction::getEmoji, emoji));
    }
}
