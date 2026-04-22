package com.actionow.collab.comment.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.collab.comment.entity.Comment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CommentMapper extends BaseMapper<Comment> {

    default IPage<Comment> selectPageByTarget(Page<Comment> page, String targetType, String targetId) {
        return selectPage(page, new LambdaQueryWrapper<Comment>()
                .eq(Comment::getTargetType, targetType)
                .eq(Comment::getTargetId, targetId)
                .isNull(Comment::getParentId)
                .orderByDesc(Comment::getCreatedAt));
    }

    default IPage<Comment> selectReplies(Page<Comment> page, String parentId) {
        return selectPage(page, new LambdaQueryWrapper<Comment>()
                .eq(Comment::getParentId, parentId)
                .orderByAsc(Comment::getCreatedAt));
    }

    @Select("SELECT COUNT(*) FROM t_comment WHERE target_type = #{targetType} AND target_id = #{targetId} AND parent_id IS NULL AND deleted = 0")
    int countByTarget(@Param("targetType") String targetType, @Param("targetId") String targetId);

    @Select("SELECT COUNT(*) FROM t_comment WHERE parent_id = #{parentId} AND deleted = 0")
    int countReplies(@Param("parentId") String parentId);
}
