package com.actionow.canvas.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.canvas.entity.CanvasView;
import com.actionow.common.core.constant.CommonConstants;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 画布视图 Mapper
 *
 * @author Actionow
 */
@Mapper
public interface CanvasViewMapper extends BaseMapper<CanvasView> {

    /**
     * 根据画布ID查询所有视图
     */
    default List<CanvasView> selectByCanvasId(String canvasId) {
        return selectList(new LambdaQueryWrapper<CanvasView>()
                .eq(CanvasView::getCanvasId, canvasId)
                .eq(CanvasView::getDeleted, CommonConstants.NOT_DELETED)
                .orderByAsc(CanvasView::getSequence));
    }

    /**
     * 根据画布ID和视图键查询视图
     */
    default CanvasView selectByCanvasIdAndViewKey(String canvasId, String viewKey) {
        return selectOne(new LambdaQueryWrapper<CanvasView>()
                .eq(CanvasView::getCanvasId, canvasId)
                .eq(CanvasView::getViewKey, viewKey)
                .eq(CanvasView::getDeleted, CommonConstants.NOT_DELETED));
    }

    /**
     * 查询画布的默认视图
     */
    default CanvasView selectDefaultView(String canvasId) {
        return selectOne(new LambdaQueryWrapper<CanvasView>()
                .eq(CanvasView::getCanvasId, canvasId)
                .eq(CanvasView::getIsDefault, true)
                .eq(CanvasView::getDeleted, CommonConstants.NOT_DELETED));
    }

    /**
     * 查询画布的预设视图
     */
    default List<CanvasView> selectPresetViews(String canvasId) {
        return selectList(new LambdaQueryWrapper<CanvasView>()
                .eq(CanvasView::getCanvasId, canvasId)
                .eq(CanvasView::getViewType, "PRESET")
                .eq(CanvasView::getDeleted, CommonConstants.NOT_DELETED)
                .orderByAsc(CanvasView::getSequence));
    }

    /**
     * 查询画布的自定义视图
     */
    default List<CanvasView> selectCustomViews(String canvasId) {
        return selectList(new LambdaQueryWrapper<CanvasView>()
                .eq(CanvasView::getCanvasId, canvasId)
                .eq(CanvasView::getViewType, "CUSTOM")
                .eq(CanvasView::getDeleted, CommonConstants.NOT_DELETED)
                .orderByAsc(CanvasView::getSequence));
    }

    /**
     * 删除画布下的所有视图
     */
    default int deleteByCanvasId(String canvasId) {
        return delete(new LambdaQueryWrapper<CanvasView>()
                .eq(CanvasView::getCanvasId, canvasId));
    }

    /**
     * 检查视图键是否已存在
     */
    default boolean existsByCanvasIdAndViewKey(String canvasId, String viewKey) {
        return selectCount(new LambdaQueryWrapper<CanvasView>()
                .eq(CanvasView::getCanvasId, canvasId)
                .eq(CanvasView::getViewKey, viewKey)
                .eq(CanvasView::getDeleted, CommonConstants.NOT_DELETED)) > 0;
    }
}
