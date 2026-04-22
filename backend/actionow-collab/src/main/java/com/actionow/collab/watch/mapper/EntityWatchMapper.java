package com.actionow.collab.watch.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.collab.watch.entity.EntityWatch;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface EntityWatchMapper extends BaseMapper<EntityWatch> {

    default EntityWatch selectByUserAndEntity(String userId, String entityType, String entityId) {
        return selectOne(new LambdaQueryWrapper<EntityWatch>()
                .eq(EntityWatch::getUserId, userId)
                .eq(EntityWatch::getEntityType, entityType)
                .eq(EntityWatch::getEntityId, entityId));
    }

    default List<EntityWatch> selectByEntity(String entityType, String entityId) {
        return selectList(new LambdaQueryWrapper<EntityWatch>()
                .eq(EntityWatch::getEntityType, entityType)
                .eq(EntityWatch::getEntityId, entityId));
    }

    default List<EntityWatch> selectByUser(String userId) {
        return selectList(new LambdaQueryWrapper<EntityWatch>()
                .eq(EntityWatch::getUserId, userId)
                .orderByDesc(EntityWatch::getCreatedAt));
    }
}
