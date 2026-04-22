package com.actionow.collab.comment.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.collab.comment.entity.CommentAttachment;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CommentAttachmentMapper extends BaseMapper<CommentAttachment> {

    default List<CommentAttachment> selectByCommentId(String commentId) {
        return selectList(new LambdaQueryWrapper<CommentAttachment>()
                .eq(CommentAttachment::getCommentId, commentId)
                .orderByAsc(CommentAttachment::getSequence));
    }
}
