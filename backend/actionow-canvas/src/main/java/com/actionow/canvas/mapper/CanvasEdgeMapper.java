package com.actionow.canvas.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.canvas.entity.CanvasEdge;
import com.actionow.common.core.constant.CommonConstants;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 画布边/连线 Mapper
 *
 * @author Actionow
 */
@Mapper
public interface CanvasEdgeMapper extends BaseMapper<CanvasEdge> {

    /**
     * 根据画布ID查询所有边
     */
    default List<CanvasEdge> selectByCanvasId(String canvasId) {
        return selectList(new LambdaQueryWrapper<CanvasEdge>()
                .eq(CanvasEdge::getCanvasId, canvasId)
                .eq(CanvasEdge::getDeleted, CommonConstants.NOT_DELETED)
                .orderByAsc(CanvasEdge::getSequence));
    }

    /**
     * 根据源实体查询边
     */
    default List<CanvasEdge> selectBySource(String canvasId, String sourceType, String sourceId) {
        return selectList(new LambdaQueryWrapper<CanvasEdge>()
                .eq(CanvasEdge::getCanvasId, canvasId)
                .eq(CanvasEdge::getSourceType, sourceType)
                .eq(CanvasEdge::getSourceId, sourceId)
                .eq(CanvasEdge::getDeleted, CommonConstants.NOT_DELETED)
                .orderByAsc(CanvasEdge::getSequence));
    }

    /**
     * 根据目标实体查询边
     */
    default List<CanvasEdge> selectByTarget(String canvasId, String targetType, String targetId) {
        return selectList(new LambdaQueryWrapper<CanvasEdge>()
                .eq(CanvasEdge::getCanvasId, canvasId)
                .eq(CanvasEdge::getTargetType, targetType)
                .eq(CanvasEdge::getTargetId, targetId)
                .eq(CanvasEdge::getDeleted, CommonConstants.NOT_DELETED)
                .orderByAsc(CanvasEdge::getSequence));
    }

    /**
     * 查询源和目标之间的边
     */
    default CanvasEdge selectBySourceAndTarget(String canvasId, String sourceType, String sourceId,
                                                String targetType, String targetId) {
        return selectOne(new LambdaQueryWrapper<CanvasEdge>()
                .eq(CanvasEdge::getCanvasId, canvasId)
                .eq(CanvasEdge::getSourceType, sourceType)
                .eq(CanvasEdge::getSourceId, sourceId)
                .eq(CanvasEdge::getTargetType, targetType)
                .eq(CanvasEdge::getTargetId, targetId)
                .eq(CanvasEdge::getDeleted, CommonConstants.NOT_DELETED));
    }

    /**
     * 查询源和目标之间特定关系类型的边
     */
    default CanvasEdge selectBySourceTargetAndType(String canvasId, String sourceType, String sourceId,
                                                    String targetType, String targetId, String relationType) {
        return selectOne(new LambdaQueryWrapper<CanvasEdge>()
                .eq(CanvasEdge::getCanvasId, canvasId)
                .eq(CanvasEdge::getSourceType, sourceType)
                .eq(CanvasEdge::getSourceId, sourceId)
                .eq(CanvasEdge::getTargetType, targetType)
                .eq(CanvasEdge::getTargetId, targetId)
                .eq(CanvasEdge::getRelationType, relationType)
                .eq(CanvasEdge::getDeleted, CommonConstants.NOT_DELETED));
    }

    /**
     * 查询与实体相关的所有边（作为源或目标）
     */
    default List<CanvasEdge> selectByEntity(String entityType, String entityId) {
        return selectList(new LambdaQueryWrapper<CanvasEdge>()
                .and(wrapper -> wrapper
                        .or(w -> w.eq(CanvasEdge::getSourceType, entityType)
                                .eq(CanvasEdge::getSourceId, entityId))
                        .or(w -> w.eq(CanvasEdge::getTargetType, entityType)
                                .eq(CanvasEdge::getTargetId, entityId)))
                .eq(CanvasEdge::getDeleted, CommonConstants.NOT_DELETED));
    }

    /**
     * 查询画布中与实体相关的所有边（作为源或目标）
     * 用于实体聚焦模式，获取特定实体的关联关系
     */
    default List<CanvasEdge> selectByCanvasAndEntity(String canvasId, String entityType, String entityId) {
        return selectList(new LambdaQueryWrapper<CanvasEdge>()
                .eq(CanvasEdge::getCanvasId, canvasId)
                .and(wrapper -> wrapper
                        .or(w -> w.eq(CanvasEdge::getSourceType, entityType)
                                .eq(CanvasEdge::getSourceId, entityId))
                        .or(w -> w.eq(CanvasEdge::getTargetType, entityType)
                                .eq(CanvasEdge::getTargetId, entityId)))
                .eq(CanvasEdge::getDeleted, CommonConstants.NOT_DELETED)
                .orderByAsc(CanvasEdge::getSequence));
    }

    /**
     * 批量查询画布中与多个实体相关的边
     * 用于多级关联查询
     */
    default List<CanvasEdge> selectByCanvasAndEntityKeys(String canvasId, List<String> entityKeys) {
        if (entityKeys == null || entityKeys.isEmpty()) {
            return List.of();
        }
        // entityKey 格式: "entityType:entityId"
        return selectList(new LambdaQueryWrapper<CanvasEdge>()
                .eq(CanvasEdge::getCanvasId, canvasId)
                .and(wrapper -> {
                    for (String key : entityKeys) {
                        String[] parts = key.split(":");
                        if (parts.length == 2) {
                            String type = parts[0];
                            String id = parts[1];
                            wrapper.or(w -> w.eq(CanvasEdge::getSourceType, type)
                                    .eq(CanvasEdge::getSourceId, id));
                            wrapper.or(w -> w.eq(CanvasEdge::getTargetType, type)
                                    .eq(CanvasEdge::getTargetId, id));
                        }
                    }
                })
                .eq(CanvasEdge::getDeleted, CommonConstants.NOT_DELETED)
                .orderByAsc(CanvasEdge::getSequence));
    }

    /**
     * 删除画布下的所有边
     */
    default int deleteByCanvasId(String canvasId) {
        return delete(new LambdaQueryWrapper<CanvasEdge>()
                .eq(CanvasEdge::getCanvasId, canvasId));
    }

    /**
     * 删除与实体相关的所有边
     */
    default int deleteByEntity(String entityType, String entityId) {
        return delete(new LambdaQueryWrapper<CanvasEdge>()
                .and(wrapper -> wrapper
                        .or(w -> w.eq(CanvasEdge::getSourceType, entityType)
                                .eq(CanvasEdge::getSourceId, entityId))
                        .or(w -> w.eq(CanvasEdge::getTargetType, entityType)
                                .eq(CanvasEdge::getTargetId, entityId))));
    }

    /**
     * 获取当前画布中的最大序号
     */
    default Integer selectMaxSequence(String canvasId) {
        CanvasEdge edge = selectOne(new LambdaQueryWrapper<CanvasEdge>()
                .eq(CanvasEdge::getCanvasId, canvasId)
                .eq(CanvasEdge::getDeleted, CommonConstants.NOT_DELETED)
                .orderByDesc(CanvasEdge::getSequence)
                .last("LIMIT 1"));
        return edge != null ? edge.getSequence() : 0;
    }

    /**
     * 统计画布中的边数量
     */
    default long countByCanvasId(String canvasId) {
        return selectCount(new LambdaQueryWrapper<CanvasEdge>()
                .eq(CanvasEdge::getCanvasId, canvasId)
                .eq(CanvasEdge::getDeleted, CommonConstants.NOT_DELETED));
    }
}
