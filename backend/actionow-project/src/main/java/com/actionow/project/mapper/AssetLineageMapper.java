package com.actionow.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.project.entity.AssetLineage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 素材溯源 Mapper
 *
 * @author Actionow
 */
@Mapper
public interface AssetLineageMapper extends BaseMapper<AssetLineage> {

    /**
     * 根据输出素材ID查询溯源记录
     */
    @Select("SELECT * FROM t_asset_lineage WHERE output_asset_id = #{outputAssetId} ORDER BY sequence")
    List<AssetLineage> selectByOutputAssetId(@Param("outputAssetId") String outputAssetId);

    /**
     * 根据输入ID查询使用该输入生成的素材
     */
    @Select("SELECT * FROM t_asset_lineage WHERE input_id = #{inputId} AND input_type = #{inputType}")
    List<AssetLineage> selectByInputId(@Param("inputId") String inputId, @Param("inputType") String inputType);

    /**
     * 根据任务ID查询溯源记录
     */
    @Select("SELECT * FROM t_asset_lineage WHERE task_id = #{taskId} ORDER BY sequence")
    List<AssetLineage> selectByTaskId(@Param("taskId") String taskId);
}
