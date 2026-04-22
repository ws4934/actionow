package com.actionow.canvas.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.canvas.entity.Canvas;
import com.actionow.common.core.constant.CommonConstants;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 画布 Mapper
 * 统一主画布模型：1 Script = 1 Canvas
 *
 * @author Actionow
 */
@Mapper
public interface CanvasMapper extends BaseMapper<Canvas> {

    /**
     * 根据剧本ID查询画布
     */
    default Canvas selectByScriptId(String scriptId) {
        return selectOne(new LambdaQueryWrapper<Canvas>()
                .eq(Canvas::getScriptId, scriptId)
                .eq(Canvas::getDeleted, CommonConstants.NOT_DELETED));
    }

    /**
     * 检查剧本是否已有画布
     */
    default boolean existsByScriptId(String scriptId) {
        return selectCount(new LambdaQueryWrapper<Canvas>()
                .eq(Canvas::getScriptId, scriptId)
                .eq(Canvas::getDeleted, CommonConstants.NOT_DELETED)) > 0;
    }

    /**
     * 根据工作空间ID查询画布列表
     */
    default List<Canvas> selectByWorkspaceId(String workspaceId) {
        return selectList(new LambdaQueryWrapper<Canvas>()
                .eq(Canvas::getWorkspaceId, workspaceId)
                .eq(Canvas::getDeleted, CommonConstants.NOT_DELETED)
                .orderByDesc(Canvas::getUpdatedAt));
    }

    /**
     * 根据剧本ID删除画布
     */
    default int deleteByScriptId(String scriptId) {
        return delete(new LambdaQueryWrapper<Canvas>()
                .eq(Canvas::getScriptId, scriptId));
    }
}
