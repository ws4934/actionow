package com.actionow.task.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.task.entity.PipelineStep;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Pipeline 步骤 Mapper
 *
 * @author Actionow
 */
@Mapper
public interface PipelineStepMapper extends BaseMapper<PipelineStep> {

    /**
     * 根据 Pipeline ID 查询所有步骤（按步骤编号排序）
     */
    default List<PipelineStep> selectByPipelineId(String pipelineId) {
        return selectList(new LambdaQueryWrapper<PipelineStep>()
                .eq(PipelineStep::getPipelineId, pipelineId)
                .orderByAsc(PipelineStep::getStepNumber)
                .last("LIMIT 200"));
    }

    /**
     * 根据 Pipeline ID 和步骤编号查询
     */
    default PipelineStep selectByPipelineIdAndStepNumber(String pipelineId, int stepNumber) {
        return selectOne(new LambdaQueryWrapper<PipelineStep>()
                .eq(PipelineStep::getPipelineId, pipelineId)
                .eq(PipelineStep::getStepNumber, stepNumber)
                .last("LIMIT 1"));
    }

    /**
     * 根据 Pipeline ID 和状态查询
     */
    default List<PipelineStep> selectByPipelineIdAndStatus(String pipelineId, String status) {
        return selectList(new LambdaQueryWrapper<PipelineStep>()
                .eq(PipelineStep::getPipelineId, pipelineId)
                .eq(PipelineStep::getStatus, status)
                .orderByAsc(PipelineStep::getStepNumber)
                .last("LIMIT 200"));
    }

    /**
     * 统计指定状态的步骤数
     */
    @Select("SELECT COUNT(*) FROM t_pipeline_step WHERE pipeline_id = #{pipelineId} AND status = #{status}")
    int countByStatus(@Param("pipelineId") String pipelineId, @Param("status") String status);
}
