package com.actionow.collab.review.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.collab.review.entity.Review;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ReviewMapper extends BaseMapper<Review> {

    default List<Review> selectByEntity(String entityType, String entityId) {
        return selectList(new LambdaQueryWrapper<Review>()
                .eq(Review::getEntityType, entityType)
                .eq(Review::getEntityId, entityId)
                .orderByDesc(Review::getCreatedAt));
    }

    default IPage<Review> selectPendingByReviewer(Page<Review> page, String reviewerId) {
        return selectPage(page, new LambdaQueryWrapper<Review>()
                .eq(Review::getReviewerId, reviewerId)
                .eq(Review::getStatus, "PENDING")
                .orderByDesc(Review::getCreatedAt));
    }
}
